package com.devsstream.caffepos

import com.devsstream.caffepos.core.model.*
import com.devsstream.caffepos.core.pricing.priceOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PricingEngineTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val catalog = buildTestCatalog()

    private fun dhakaTime(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, dhaka)

    private fun itemX(qty: Int = 1) = CartItem(
        productId = "latte",
        sizeId = "lrg",
        modifiers = listOf(
            SelectedModifier("milk_oat", "milk"),
            SelectedModifier("extra_shot", "shots", quantity = 1),
            SelectedModifier("vanilla", "syrup")
        ),
        quantity = qty
    )

    private fun itemY(qty: Int = 1) = CartItem(
        productId = "americano",
        sizeId = "reg",
        modifiers = listOf(
            SelectedModifier("extra_shot", "shots", quantity = 2),
            SelectedModifier("caramel", "syrup")
        ),
        quantity = qty
    )

    private fun itemZ(qty: Int = 1) = CartItem(
        productId = "iced_tea",
        sizeId = "lrg",
        modifiers = listOf(SelectedModifier("vanilla", "syrup")),
        quantity = qty
    )

    private fun croissant(qty: Int) = CartItem("croissant", null, emptyList(), qty)
    private fun brownie(qty: Int) = CartItem("brownie", null, emptyList(), qty)

    @Test
    fun `TC-05 unit prices of X Y Z`() {
        val serverTime = dhakaTime(2026, 11, 20, 12)
        assertEquals(41_000L, priceOrder(listOf(itemX(1)), catalog, null, serverTime).lines[0].unitPricePoisha)
        assertEquals(31_000L, priceOrder(listOf(itemY(1)), catalog, null, serverTime).lines[0].unitPricePoisha)
        assertEquals(18_000L, priceOrder(listOf(itemZ(1)), catalog, null, serverTime).lines[0].unitPricePoisha)
    }

    @Test
    fun `TC-06 full order at happy hour with DEVS10`() {
        val serverTime = dhakaTime(2026, 11, 20, 15, 30)
        val pricing = priceOrder(listOf(itemX(2), croissant(2), brownie(2)), catalog, "DEVS10", serverTime)

        assertEquals(12_000L, pricing.p1BakeryDiscountPoisha)
        assertEquals(8_200L, pricing.p2HappyHourDiscountPoisha)
        assertEquals(115_800L, pricing.subtotalPoisha)
        assertEquals(DiscountType.C1_COUPON, pricing.activeDiscount?.type)
        assertEquals(11_580L, pricing.activeDiscount?.amountPoisha)
        assertEquals(5_211L, pricing.vatPoisha)
        assertEquals(109_400L, pricing.payablePoisha)
    }

    @Test
    fun `TC-07 same order at 17 00 has no happy hour`() {
        val serverTime = dhakaTime(2026, 11, 20, 17, 0)
        val pricing = priceOrder(listOf(itemX(2), croissant(2), brownie(2)), catalog, "DEVS10", serverTime)

        assertEquals(0L, pricing.p2HappyHourDiscountPoisha)
        assertEquals(12_000L, pricing.p1BakeryDiscountPoisha)
        assertEquals(124_000L, pricing.subtotalPoisha)
        assertEquals(DiscountType.C1_COUPON, pricing.activeDiscount?.type)
        assertEquals(12_000L, pricing.activeDiscount?.amountPoisha)
        assertEquals(5_600L, pricing.vatPoisha)
        assertEquals(117_600L, pricing.payablePoisha)
    }

    @Test
    fun `TC-08 same order on 1 Dec 2026 expires DEVS10 and falls back to T1`() {
        val serverTime = dhakaTime(2026, 12, 1, 15, 30)
        val pricing = priceOrder(listOf(itemX(2), croissant(2), brownie(2)), catalog, "DEVS10", serverTime)

        assertEquals(DiscountType.T1_THRESHOLD, pricing.activeDiscount?.type)
        assertEquals(8_000L, pricing.activeDiscount?.amountPoisha)
        assertEquals(5_390L, pricing.vatPoisha)
        assertEquals(113_200L, pricing.payablePoisha)
    }

    @Test
    fun `TC-09 engine uses server time regardless of device clock`() {
        val serverTime = dhakaTime(2026, 11, 20, 15, 30)
        val pricing = priceOrder(listOf(itemX(2), croissant(2), brownie(2)), catalog, "DEVS10", serverTime)
        assertEquals(109_400L, pricing.payablePoisha)
    }
}
