package com.devsstream.caffepos.data.api

import com.devsstream.caffepos.core.model.Catalog
import java.time.Instant

interface FakeApi {
    suspend fun getCatalog(): CatalogResponse
    suspend fun postOrder(order: OrderRequest): OrderResponse
}

data class CatalogResponse(
    val version: Int,
    val catalog: Catalog,
    val serverTime: Instant
)

data class OrderRequest(
    val clientOrderId: String,
    val receiptNo: String,
    val lines: List<OrderLineRequest>,
    val payments: List<PaymentRequest>,
    val payablePoisha: Long,
    val serverTime: Instant
)

data class OrderLineRequest(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPricePoisha: Long,
    val lineTotalPoisha: Long,
    val modifiers: List<ModifierRequest>
)

data class ModifierRequest(
    val optionId: String,
    val optionName: String,
    val quantity: Int,
    val pricePoisha: Long
)

data class PaymentRequest(
    val type: String,         // "CASH" or "CARD"
    val amountPoisha: Long,
    val cardRef: String?,
    val authCode: String?
)

data class OrderResponse(
    val serverOrderNo: String,
    val serverTime: Instant,
    val duplicate: Boolean
)
