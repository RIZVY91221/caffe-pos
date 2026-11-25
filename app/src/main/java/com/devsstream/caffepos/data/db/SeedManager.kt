package com.devsstream.caffepos.data.db

import com.devsstream.caffepos.core.model.*
import com.devsstream.caffepos.core.pricing.priceOrder
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the Appendix B orders for 20 November 2026 on first launch
 * so the Sales Summary screen matches ANSWERS.md immediately.
 */
@Singleton
class SeedManager @Inject constructor(
    private val orderDao: OrderDao,
    private val receiptSequenceDao: ReceiptSequenceDao
) {
    private val dhaka = ZoneId.of("Asia/Dhaka")

    suspend fun seedIfEmpty() {
        val existing = orderDao.observeAllOrders().first()
        if (existing.isNotEmpty()) return

        val catalog = defaultAppendixACatalog()
        val seeds = listOf(
            SeedSpec(
                id = "seed-o1",
                receiptNo = "T1-20261120-0001",
                time = t(8, 40),
                items = listOf(
                    CartItem("americano", "reg", emptyList(), 1),
                    CartItem("croissant", null, emptyList(), 1)
                ),
                coupon = null,
                payments = { payable -> listOf(PaymentLine(PaymentType.CASH, payable)) }
            ),
            SeedSpec(
                id = "seed-o2",
                receiptNo = "T1-20261120-0002",
                time = t(15, 10),
                items = listOf(
                    CartItem("latte", "lrg", listOf(SelectedModifier("milk_oat", "milk")), 2),
                    CartItem("brownie", null, emptyList(), 3)
                ),
                coupon = null,
                payments = { payable -> listOf(PaymentLine(PaymentType.CARD, payable, "seed-ref-o2", "AUTH-SEED2")) }
            ),
            SeedSpec(
                id = "seed-o3",
                receiptNo = "T1-20261120-0003",
                time = t(16, 59),
                items = listOf(
                    CartItem("iced_tea", "lrg", emptyList(), 3),
                    CartItem("chicken_sandwich", null, emptyList(), 2)
                ),
                coupon = "DEVS10",
                payments = { payable ->
                    listOf(
                        PaymentLine(PaymentType.CARD, 50_000L, "seed-ref-o3", "AUTH-SEED3"),
                        PaymentLine(PaymentType.CASH, payable - 50_000L)
                    )
                }
            ),
            SeedSpec(
                id = "seed-o4",
                receiptNo = "T1-20261120-0004",
                time = t(17, 0),
                items = listOf(
                    CartItem("americano", "lrg", listOf(
                        SelectedModifier("milk_oat", "milk"),
                        SelectedModifier("extra_shot", "shots", 2),
                        SelectedModifier("hazelnut", "syrup")
                    ), 1),
                    CartItem("veg_wrap", null, emptyList(), 2),
                    CartItem("brownie", null, emptyList(), 1),
                    CartItem("croissant", null, emptyList(), 2)
                ),
                coupon = null,
                payments = { payable -> listOf(PaymentLine(PaymentType.CARD, payable, "seed-ref-o4", "AUTH-SEED4")) }
            ),
            SeedSpec(
                id = "seed-o5",
                receiptNo = "T1-20261120-0005",
                time = t(11, 20),
                items = listOf(
                    CartItem("latte", "reg", listOf(SelectedModifier("milk_almond", "milk")), 1),
                    CartItem("veg_wrap", null, emptyList(), 1)
                ),
                coupon = "DEVS10",
                payments = { payable -> listOf(PaymentLine(PaymentType.CASH, payable)) }
            ),
            SeedSpec(
                id = "seed-o6",
                receiptNo = "T1-20261120-0006",
                time = t(15, 45),
                items = listOf(
                    CartItem("croissant", null, emptyList(), 6),
                    CartItem("americano", "reg", emptyList(), 2)
                ),
                coupon = null,
                payments = { payable -> listOf(PaymentLine(PaymentType.CASH, payable)) }
            )
        )

        seeds.forEachIndexed { index, spec ->
            val breakdown = priceOrder(spec.items, catalog, spec.coupon, spec.time)
            val serverMs = spec.time.toInstant().toEpochMilli()
            orderDao.insertOrder(
                OrderEntity(
                    clientOrderId = spec.id,
                    receiptNo = spec.receiptNo,
                    serverOrderNo = "SRV-SEED-${index + 1}",
                    status = OrderStatus.SYNCED,
                    payablePoisha = breakdown.payablePoisha,
                    serverTimeMs = serverMs,
                    createdAtMs = serverMs
                )
            )
            orderDao.insertLines(
                breakdown.lines.mapIndexed { idx, line ->
                    val mods = spec.items[idx].modifiers
                    OrderLineEntity(
                        orderId = spec.id,
                        productId = line.productId,
                        productName = line.productName,
                        quantity = line.quantity,
                        unitPricePoisha = line.unitPricePoisha,
                        lineTotalPoisha = line.lineTotalPoisha,
                        modifiersJson = serializeModifiers(mods, catalog)
                    )
                }
            )
            orderDao.insertPayments(
                spec.payments(breakdown.payablePoisha).map { p ->
                    PaymentEntity(
                        orderId = spec.id,
                        type = p.type,
                        amountPoisha = p.amountPoisha,
                        cardRef = p.cardRef,
                        authCode = p.authCode
                    )
                }
            )
        }
        receiptSequenceDao.saveSequence(ReceiptSequenceEntity("20261120", 6))
    }

    private fun t(hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, 20, hour, minute, 0, 0, dhaka)

    private fun serializeModifiers(modifiers: List<SelectedModifier>, catalog: Catalog): String {
        val arr = JSONArray()
        for (mod in modifiers) {
            val group = catalog.modifierGroups.find { it.id == mod.groupId }
            val option = group?.options?.find { it.id == mod.optionId }
            arr.put(JSONObject().apply {
                put("optionId", mod.optionId)
                put("optionName", option?.name ?: mod.optionId)
                put("quantity", mod.quantity)
                put("pricePoisha", (option?.priceBdt ?: 0) * 100L)
            })
        }
        return arr.toString()
    }

    private fun defaultAppendixACatalog(): Catalog {
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
            Product("latte", "Latte", ProductCategory.BEVERAGE, 220,
                listOf(ProductSize("reg", "Regular", 0), ProductSize("lrg", "Large", 50)),
                listOf("milk", "shots", "syrup")),
            Product("iced_tea", "Iced Tea", ProductCategory.BEVERAGE, 120,
                listOf(ProductSize("reg", "Regular", 0), ProductSize("lrg", "Large", 30)),
                listOf("syrup")),
            Product("chicken_sandwich", "Chicken Sandwich", ProductCategory.FOOD, 320, emptyList(), emptyList()),
            Product("veg_wrap", "Veg Wrap", ProductCategory.FOOD, 260, emptyList(), emptyList()),
            Product("croissant", "Croissant", ProductCategory.BAKERY, 150, emptyList(), emptyList()),
            Product("brownie", "Brownie", ProductCategory.BAKERY, 120, emptyList(), emptyList())
        )
        return Catalog(
            version = 1,
            products = products,
            modifierGroups = listOf(milkGroup, shotsGroup, syrupGroup),
            rules = rules,
            promotions = listOf(
                Promotion("P1", PromotionType.BAKERY_3_FOR_2),
                Promotion("P2", PromotionType.HAPPY_HOUR_BEVERAGE)
            )
        )
    }

    private data class SeedSpec(
        val id: String,
        val receiptNo: String,
        val time: ZonedDateTime,
        val items: List<CartItem>,
        val coupon: String?,
        val payments: (Long) -> List<PaymentLine>
    )
}
