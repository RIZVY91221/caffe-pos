package com.devsstream.caffepos.core.model

/** A single selected modifier option on a cart item */
data class SelectedModifier(
    val optionId: String,
    val groupId: String,
    val quantity: Int = 1
)

/**
 * A single item the cashier is adding to the cart.
 * sizeId: null if product has no sizes, must be set if product has sizes.
 */
data class CartItem(
    val productId: String,
    val sizeId: String?,
    val modifiers: List<SelectedModifier>,
    val quantity: Int = 1
)

/** The state of the in-progress cart */
data class Cart(
    val items: List<CartItem> = emptyList(),
    val couponCode: String? = null
)

/** Validation violation returned by validate() */
data class Violation(
    val ruleId: String,
    val message: String
)

/** Order status after payment and during sync lifecycle */
enum class OrderStatus {
    PAID,       // saved to Room, awaiting sync queue
    PENDING,    // queued in WorkManager
    SYNCING,    // actively being sent
    SYNCED,     // server confirmed
    FAILED      // 5 failed attempts, needs retry
}

enum class PaymentType { CASH, CARD }

data class PaymentLine(
    val type: PaymentType,
    val amountPoisha: Long,
    val cardRef: String? = null,
    val authCode: String? = null
)

/** Full pricing breakdown returned by priceOrder() */
data class PriceBreakdown(
    val lines: List<LineBreakdown>,
    val p1BakeryDiscountPoisha: Long,
    val p2HappyHourDiscountPoisha: Long,
    val subtotalPoisha: Long,
    val activeDiscount: AppliedDiscount?,
    val vatPoisha: Long,
    val payablePoisha: Long
)

data class LineBreakdown(
    val productId: String,
    val productName: String,
    val unitPricePoisha: Long,
    val quantity: Int,
    val lineTotalPoisha: Long
)

data class AppliedDiscount(
    val type: DiscountType,
    val amountPoisha: Long
)

enum class DiscountType { T1_THRESHOLD, C1_COUPON }
