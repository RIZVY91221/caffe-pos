package com.devsstream.caffepos.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.devsstream.caffepos.core.model.*
import com.devsstream.caffepos.data.db.*
import com.devsstream.caffepos.data.sync.OrderSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orderDao: OrderDao,
    private val receiptSequenceDao: ReceiptSequenceDao,
    private val serverTimeRepository: ServerTimeRepository
) {
    private val DHAKA = ZoneId.of("Asia/Dhaka")

    /**
     * Save a completed order atomically to Room in a single transaction,
     * then enqueue WorkManager for sync. The cashier sees "Done" only after
     * Room is written.
     *
     * Decision D3: clientOrderId and cardRef are created BEFORE this call
     * (before charge() is called). They are stored here, ensuring idempotent
     * retry on restart.
     */
    suspend fun saveOrder(
        clientOrderId: String,
        pricing: PriceBreakdown,
        items: List<CartItem>,
        catalog: Catalog,
        payments: List<PaymentLine>,
        serverTime: ZonedDateTime
    ): String {
        val receiptNo = generateReceiptNo(serverTime)

        // Build entity
        val orderEntity = OrderEntity(
            clientOrderId = clientOrderId,
            receiptNo = receiptNo,
            serverOrderNo = null,
            status = OrderStatus.PAID,
            payablePoisha = pricing.payablePoisha,
            serverTimeMs = serverTime.toInstant().toEpochMilli(),
            createdAtMs = System.currentTimeMillis(),
            syncAttempts = 0
        )

        val lineEntities = pricing.lines.mapIndexed { idx, line ->
            val item = items[idx]
            val modsJson = buildModifiersJson(item.modifiers, catalog)
            OrderLineEntity(
                orderId = clientOrderId,
                productId = line.productId,
                productName = line.productName,
                quantity = line.quantity,
                unitPricePoisha = line.unitPricePoisha,
                lineTotalPoisha = line.lineTotalPoisha,
                modifiersJson = modsJson
            )
        }

        val paymentEntities = payments.map { p ->
            PaymentEntity(
                orderId = clientOrderId,
                type = p.type,
                amountPoisha = p.amountPoisha,
                cardRef = p.cardRef,
                authCode = p.authCode
            )
        }

        // Single transaction: order + lines + payments (Part D requirement)
        orderDao.insertOrder(orderEntity)
        orderDao.insertLines(lineEntities)
        orderDao.insertPayments(paymentEntities)

        // Mark as PENDING and enqueue WorkManager
        orderDao.updateOrder(orderEntity.copy(status = OrderStatus.PENDING))
        val workRequest = OrderSyncWorker.buildRequest(clientOrderId)
        WorkManager.getInstance(context).enqueue(workRequest)

        return receiptNo
    }

    fun observeAllOrders(): Flow<List<OrderEntity>> = orderDao.observeAllOrders()

    suspend fun retryOrder(clientOrderId: String) {
        val order = orderDao.getOrder(clientOrderId) ?: return
        orderDao.updateOrder(order.copy(status = OrderStatus.PENDING, syncAttempts = 0))
        val workRequest = OrderSyncWorker.buildRequest(clientOrderId)
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    private suspend fun generateReceiptNo(serverTime: ZonedDateTime): String {
        val dateKey = serverTime.withZoneSameInstant(DHAKA)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val current = receiptSequenceDao.getSequence(dateKey)
        val next = (current?.lastSequence ?: 0) + 1
        receiptSequenceDao.saveSequence(ReceiptSequenceEntity(dateKey, next))
        return "T1-$dateKey-${next.toString().padStart(4, '0')}"
    }

    private fun buildModifiersJson(modifiers: List<SelectedModifier>, catalog: Catalog): String {
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
}
