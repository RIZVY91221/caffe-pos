package com.devsstream.caffepos.data.api

import com.devsstream.caffepos.core.model.*
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * In-memory fake API (Appendix C).
 * - Latency: 300-2000ms
 * - 20% of calls throw IOException
 * - 10% of postOrder calls return HTTP 500 AFTER the order is saved (idempotent retry safe)
 * - Server clock is 150 seconds ahead of real time, unaffected by device clock
 * - Idempotent: same clientOrderId always returns same serverOrderNo with duplicate=true
 */
class FakeApiImpl : FakeApi {

    // Stored orders (clientId -> serverOrderNo), survives "network" retries
    private val savedOrders = ConcurrentHashMap<String, String>()
    private var orderCounter = 1000

    // Test hooks
    private var forcedServerTime: Instant? = null
    private var forceFailureOnce = false
    private var catalogVersion = 1
    private var latteBaseOverride: Int? = null

    fun getServerTime(): Instant = forcedServerTime ?: Instant.now().plusSeconds(150)

    // Test hook: set fixed server time
    fun setServerTime(time: Instant) { forcedServerTime = time }

    // Test hook: next call throws IOException
    fun forceNextFailure() { forceFailureOnce = true }

    // Test hook: bump catalog to v2 (Latte base = 240 BDT)
    fun bumpCatalogToV2() { catalogVersion = 2; latteBaseOverride = 240 }

    override suspend fun getCatalog(): CatalogResponse {
        simulateNetwork()
        return CatalogResponse(
            version = catalogVersion,
            catalog = buildCatalog(),
            serverTime = getServerTime()
        )
    }

    override suspend fun postOrder(order: OrderRequest): OrderResponse {
        simulateNetwork()
        val serverTime = getServerTime()

        // Check if already saved (idempotency)
        val existingNo = savedOrders[order.clientOrderId]
        if (existingNo != null) {
            // 10% chance of HTTP 500 even for duplicates — but order already exists
            if (Random.nextInt(100) < 10) throw IOException("HTTP 500 (simulated, order already saved)")
            return OrderResponse(serverOrderNo = existingNo, serverTime = serverTime, duplicate = true)
        }

        // Save new order
        val serverOrderNo = "SRV-${++orderCounter}"
        savedOrders[order.clientOrderId] = serverOrderNo

        // 10% chance of HTTP 500 after saving (TC-15 scenario)
        if (Random.nextInt(100) < 10) throw IOException("HTTP 500 (simulated, order just saved, duplicate=true on retry)")

        return OrderResponse(serverOrderNo = serverOrderNo, serverTime = serverTime, duplicate = false)
    }

    private suspend fun simulateNetwork() {
        val latencyMs = Random.nextLong(300, 2001)
        delay(latencyMs)
        if (forceFailureOnce || Random.nextInt(100) < 20) {
            forceFailureOnce = false
            throw IOException("Simulated network failure")
        }
    }

    private fun buildCatalog(): Catalog {
        val latteBase = latteBaseOverride ?: 220

        val milkGroup = ModifierGroup(
            id = "milk", name = "Milk", max = 1,
            options = listOf(
                ModifierOption("milk_oat", "Oat", 60),
                ModifierOption("milk_almond", "Almond", 70)
            )
        )
        val shotsGroup = ModifierGroup(
            id = "shots", name = "Shots", max = 2,
            options = listOf(ModifierOption("extra_shot", "Extra shot", 50))
        )
        val syrupGroup = ModifierGroup(
            id = "syrup", name = "Syrup", maxDistinct = 2, maxPerOption = 1,
            options = listOf(
                ModifierOption("vanilla", "Vanilla", 30),
                ModifierOption("caramel", "Caramel", 30),
                ModifierOption("hazelnut", "Hazelnut", 35)
            )
        )

        val rules = listOf(
            ModifierRule("R1", RuleType.REQUIRES, "hazelnut", listOf("milk_oat", "milk_almond")),
            ModifierRule("R2", RuleType.EXCLUDES, "milk_almond", listOf("caramel"))
        )

        val products = listOf(
            Product("americano", "Americano", ProductCategory.BEVERAGE, 180,
                listOf(ProductSize("reg", "Regular", 0), ProductSize("lrg", "Large", 40)),
                listOf("milk", "shots", "syrup")),
            Product("latte", "Latte", ProductCategory.BEVERAGE, latteBase,
                listOf(ProductSize("reg", "Regular", 0), ProductSize("lrg", "Large", 50)),
                listOf("milk", "shots", "syrup")),
            Product("iced_tea", "Iced Tea", ProductCategory.BEVERAGE, 120,
                listOf(ProductSize("reg", "Regular", 0), ProductSize("lrg", "Large", 30)),
                listOf("syrup")),
            Product("chicken_sandwich", "Chicken Sandwich", ProductCategory.FOOD, 320,
                emptyList(), emptyList()),
            Product("veg_wrap", "Veg Wrap", ProductCategory.FOOD, 260,
                emptyList(), emptyList()),
            Product("croissant", "Croissant", ProductCategory.BAKERY, 150,
                emptyList(), emptyList()),
            Product("brownie", "Brownie", ProductCategory.BAKERY, 120,
                emptyList(), emptyList())
        )

        return Catalog(
            version = catalogVersion,
            products = products,
            modifierGroups = listOf(milkGroup, shotsGroup, syrupGroup),
            rules = rules,
            promotions = listOf(
                Promotion("P1", PromotionType.BAKERY_3_FOR_2),
                Promotion("P2", PromotionType.HAPPY_HOUR_BEVERAGE)
            )
        )
    }
}
