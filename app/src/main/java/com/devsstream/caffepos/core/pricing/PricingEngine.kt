package com.devsstream.caffepos.core.pricing

import com.devsstream.caffepos.core.model.*
import java.time.ZoneId
import java.time.ZonedDateTime

private val DHAKA = ZoneId.of("Asia/Dhaka")

// Coupon constants
private const val COUPON_DEVS10 = "DEVS10"
private const val DEVS10_RATE_NUMERATOR = 10L   // 10%
private const val DEVS10_CAP_POISHA = 12_000L   // 120 BDT
private const val T1_MIN_SUBTOTAL_POISHA = 100_000L  // 1000 BDT
private const val T1_DISCOUNT_POISHA = 8_000L   // 80 BDT
private const val VAT_RATE_NUMERATOR = 5L
private const val HAPPY_HOUR_START = 15
private const val HAPPY_HOUR_END = 17

/** Coupon DEVS10 expires at 2026-12-01T00:00:00 Asia/Dhaka */
private val DEVS10_EXPIRY = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, DHAKA).toInstant()

/**
 * Pure pricing function. All money is Long poisha (1 BDT = 100 poisha).
 * No Android classes. Safe for plain JVM unit tests.
 *
 * @param items       List of CartItem (product + size + modifiers + qty)
 * @param catalog     Current catalog (data-driven, never hardcoded)
 * @param couponCode  Optional coupon string entered by cashier
 * @param serverTime  Server time as ZonedDateTime (must be Asia/Dhaka or will be converted)
 */
fun priceOrder(
    items: List<CartItem>,
    catalog: Catalog,
    couponCode: String?,
    serverTime: ZonedDateTime
): PriceBreakdown {
    val dhakaTime = serverTime.withZoneSameInstant(DHAKA)

    // Step 1: compute unit prices and line totals
    val lineBreakdowns = items.map { item ->
        val product = catalog.products.first { it.id == item.productId }
        val sizeExtra = if (item.sizeId != null) {
            (product.sizes.find { it.id == item.sizeId }?.extraPriceBdt ?: 0) * 100L
        } else 0L
        val basePricePoisha = product.basePriceBdt * 100L
        val modifierTotalPoisha = item.modifiers.sumOf { sel ->
            val group = catalog.modifierGroups.first { g -> g.id == sel.groupId }
            val option = group.options.first { o -> o.id == sel.optionId }
            option.priceBdt * 100L * sel.quantity
        }
        val unitPricePoisha = basePricePoisha + sizeExtra + modifierTotalPoisha
        val lineTotalPoisha = unitPricePoisha * item.quantity
        LineBreakdown(
            productId = product.id,
            productName = product.name,
            unitPricePoisha = unitPricePoisha,
            quantity = item.quantity,
            lineTotalPoisha = lineTotalPoisha
        )
    }

    // Step 2: P1 Bakery 3-for-2
    // For every 3 BAKERY units, the cheapest unit is free (floor(n/3) cheapest)
    val bakeryLines = items.mapIndexedNotNull { idx, item ->
        val product = catalog.products.first { it.id == item.productId }
        if (product.category == ProductCategory.BAKERY) Pair(idx, item) else null
    }
    val totalBakeryUnits = bakeryLines.sumOf { it.second.quantity }
    val freeCount = totalBakeryUnits / 3

    // Build a sorted list of (unitPricePoisha) for all bakery units (expand by quantity)
    val bakeryUnitPrices = mutableListOf<Long>()
    for ((idx, item) in bakeryLines) {
        val unitPrice = lineBreakdowns[idx].unitPricePoisha
        repeat(item.quantity) { bakeryUnitPrices += unitPrice }
    }
    bakeryUnitPrices.sort()
    val p1Discount = bakeryUnitPrices.take(freeCount.toInt()).sum()

    // Step 3: P2 Happy Hour: 10% off every BEVERAGE line total if 15:00 <= time < 17:00
    val isHappyHour = dhakaTime.hour >= HAPPY_HOUR_START && dhakaTime.hour < HAPPY_HOUR_END
    val p2Discount = if (isHappyHour) {
        items.mapIndexedNotNull { idx, item ->
            val product = catalog.products.first { it.id == item.productId }
            if (product.category == ProductCategory.BEVERAGE) {
                roundHalfUpPoisha(lineBreakdowns[idx].lineTotalPoisha * 10L, 100L)
            } else null
        }.sum()
    } else 0L

    // Step 4: Subtotal
    val subtotalPoisha = lineBreakdowns.sumOf { it.lineTotalPoisha } - p1Discount - p2Discount

    // Step 5: Order discount — only one applies, pick larger (tie → T1)
    val t1Amount = if (subtotalPoisha >= T1_MIN_SUBTOTAL_POISHA) T1_DISCOUNT_POISHA else 0L
    val c1Amount: Long = if (
        couponCode?.uppercase() == COUPON_DEVS10 &&
        dhakaTime.toInstant().isBefore(DEVS10_EXPIRY)
    ) {
        val raw = roundHalfUpPoisha(subtotalPoisha * DEVS10_RATE_NUMERATOR, 100L)
        minOf(raw, DEVS10_CAP_POISHA)
    } else 0L

    val activeDiscount: AppliedDiscount? = when {
        t1Amount == 0L && c1Amount == 0L -> null
        c1Amount > t1Amount -> AppliedDiscount(DiscountType.C1_COUPON, c1Amount)
        else -> AppliedDiscount(DiscountType.T1_THRESHOLD, t1Amount)   // tie → T1
    }

    val discountAmount = activeDiscount?.amountPoisha ?: 0L
    val discountedSubtotal = subtotalPoisha - discountAmount

    // Step 6: VAT 5% on discounted subtotal, round half-up to poisha; payable rounded to whole BDT
    val vatPoisha = roundHalfUpPoisha(discountedSubtotal * VAT_RATE_NUMERATOR, 100L)
    val totalBeforeRounding = discountedSubtotal + vatPoisha
    val payablePoisha = roundHalfUpToBdt(totalBeforeRounding)

    return PriceBreakdown(
        lines = lineBreakdowns,
        p1BakeryDiscountPoisha = p1Discount,
        p2HappyHourDiscountPoisha = p2Discount,
        subtotalPoisha = subtotalPoisha,
        activeDiscount = activeDiscount,
        vatPoisha = vatPoisha,
        payablePoisha = payablePoisha
    )
}

/** Round half-up: (numerator * value) / denominator, where denominator is typically 100 */
internal fun roundHalfUpPoisha(numeratorProduct: Long, denominator: Long): Long {
    // Uses integer arithmetic to avoid floating point.
    // result = floor((value * rate + denominator/2) / denominator)
    return (numeratorProduct + denominator / 2) / denominator
}

/** Round half-up to the nearest 100 poisha (= whole BDT) */
internal fun roundHalfUpToBdt(poisha: Long): Long {
    return (poisha + 50) / 100 * 100
}
