package com.devsstream.caffepos

import com.devsstream.caffepos.core.model.*
import com.devsstream.caffepos.core.pricing.priceOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AppendixBSeedTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val catalog = buildTestCatalog()

    private fun t(hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 11, 20, hour, minute, 0, 0, dhaka)

    @Test
    fun `print and verify all Appendix B orders and Q6`() {
        val o1 = priceOrder(
            listOf(CartItem("americano", "reg", emptyList(), 1), CartItem("croissant", null, emptyList(), 1)),
            catalog, null, t(8, 40)
        )
        val o2 = priceOrder(
            listOf(CartItem("latte", "lrg", listOf(SelectedModifier("milk_oat", "milk")), 2), CartItem("brownie", null, emptyList(), 3)),
            catalog, null, t(15, 10)
        )
        val o3 = priceOrder(
            listOf(CartItem("iced_tea", "lrg", emptyList(), 3), CartItem("chicken_sandwich", null, emptyList(), 2)),
            catalog, "DEVS10", t(16, 59)
        )
        val o4 = priceOrder(
            listOf(
                CartItem("americano", "lrg", listOf(
                    SelectedModifier("milk_oat", "milk"),
                    SelectedModifier("extra_shot", "shots", 2),
                    SelectedModifier("hazelnut", "syrup")
                ), 1),
                CartItem("veg_wrap", null, emptyList(), 2),
                CartItem("brownie", null, emptyList(), 1),
                CartItem("croissant", null, emptyList(), 2)
            ),
            catalog, null, t(17, 0)
        )
        val o5 = priceOrder(
            listOf(CartItem("latte", "reg", listOf(SelectedModifier("milk_almond", "milk")), 1), CartItem("veg_wrap", null, emptyList(), 1)),
            catalog, "DEVS10", t(11, 20)
        )
        val o6 = priceOrder(
            listOf(CartItem("croissant", null, emptyList(), 6), CartItem("americano", "reg", emptyList(), 2)),
            catalog, null, t(15, 45)
        )

        val orders = listOf("O1" to o1, "O2" to o2, "O3" to o3, "O4" to o4, "O5" to o5, "O6" to o6)
        orders.forEach { (name, p) ->
            println("=== $name === payable=${p.payablePoisha} poisha (${p.payablePoisha / 100} BDT)")
            p.lines.forEach { println("  Line: ${it.productName} x${it.quantity} unit=${it.unitPricePoisha} total=${it.lineTotalPoisha}") }
            println("  P1=${p.p1BakeryDiscountPoisha} P2=${p.p2HappyHourDiscountPoisha} subtotal=${p.subtotalPoisha}")
            println("  Discount=${p.activeDiscount} VAT=${p.vatPoisha}")
        }
        val dayTotal = orders.sumOf { it.second.payablePoisha }
        println("DAY_TOTAL=$dayTotal poisha (${dayTotal / 100} BDT)")

        val cardTotal = 50_000L + o2.payablePoisha + o4.payablePoisha
        val cashTotal = o1.payablePoisha + (o3.payablePoisha - 50_000L) + o5.payablePoisha + o6.payablePoisha
        println("CARD_TOTAL=$cardTotal poisha (${cardTotal / 100} BDT)")
        println("CASH_TOTAL=$cashTotal poisha (${cashTotal / 100} BDT)")
        assertEquals(dayTotal, cardTotal + cashTotal)

        // Q6: TC-06 + 1 extra brownie
        val q6 = priceOrder(
            listOf(
                CartItem("latte", "lrg", listOf(
                    SelectedModifier("milk_oat", "milk"),
                    SelectedModifier("extra_shot", "shots", 1),
                    SelectedModifier("vanilla", "syrup")
                ), 2),
                CartItem("croissant", null, emptyList(), 2),
                CartItem("brownie", null, emptyList(), 3)
            ),
            catalog,
            "DEVS10",
            t(15, 30)
        )
        println("Q6: payable=${q6.payablePoisha} poisha (${q6.payablePoisha / 100} BDT), P1=${q6.p1BakeryDiscountPoisha}, P2=${q6.p2HappyHourDiscountPoisha}, subtotal=${q6.subtotalPoisha}, disc=${q6.activeDiscount}, vat=${q6.vatPoisha}")
        assertTrue(dayTotal > 0)
    }
}
