package com.devsstream.caffepos.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.devsstream.caffepos.core.model.OrderStatus
import com.devsstream.caffepos.data.api.FakeApi
import com.devsstream.caffepos.data.api.OrderRequest
import com.devsstream.caffepos.data.api.ModifierRequest
import com.devsstream.caffepos.data.api.PaymentRequest
import com.devsstream.caffepos.data.db.OrderDao
import com.devsstream.caffepos.data.db.OrderEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import org.json.JSONArray

@HiltWorker
class OrderSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val orderDao: OrderDao,
    private val fakeApi: FakeApi
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_ORDER_ID = "order_id"
        private const val MAX_ATTEMPTS = 5

        fun buildRequest(clientOrderId: String): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return OneTimeWorkRequestBuilder<OrderSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_ORDER_ID to clientOrderId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val clientOrderId = inputData.getString(KEY_ORDER_ID)
            ?: return@withContext Result.failure()

        val order = orderDao.getOrder(clientOrderId)
            ?: return@withContext Result.failure()

        // Already synced — nothing to do
        if (order.status == OrderStatus.SYNCED) return@withContext Result.success()

        // Track attempts
        val newAttempts = order.syncAttempts + 1
        orderDao.updateOrder(order.copy(
            status = OrderStatus.SYNCING,
            syncAttempts = newAttempts
        ))

        return@withContext try {
            val lines = orderDao.getLinesForOrder(clientOrderId)
            val payments = orderDao.getPaymentsForOrder(clientOrderId)

            val request = OrderRequest(
                clientOrderId = order.clientOrderId,
                receiptNo = order.receiptNo,
                lines = lines.map { line ->
                    val mods = parseModifiersJson(line.modifiersJson)
                    com.devsstream.caffepos.data.api.OrderLineRequest(
                        productId = line.productId,
                        productName = line.productName,
                        quantity = line.quantity,
                        unitPricePoisha = line.unitPricePoisha,
                        lineTotalPoisha = line.lineTotalPoisha,
                        modifiers = mods
                    )
                },
                payments = payments.map { p ->
                    PaymentRequest(
                        type = p.type.name,
                        amountPoisha = p.amountPoisha,
                        cardRef = p.cardRef,
                        authCode = p.authCode
                    )
                },
                payablePoisha = order.payablePoisha,
                serverTime = Instant.ofEpochMilli(order.serverTimeMs)
            )

            val response = fakeApi.postOrder(request)
            orderDao.updateOrder(
                order.copy(
                    status = OrderStatus.SYNCED,
                    serverOrderNo = response.serverOrderNo,
                    isDuplicate = response.duplicate,
                    syncAttempts = newAttempts
                )
            )
            Result.success()
        } catch (e: IOException) {
            if (newAttempts >= MAX_ATTEMPTS) {
                orderDao.updateOrder(order.copy(
                    status = OrderStatus.FAILED,
                    syncAttempts = newAttempts
                ))
                Result.failure()  // stop retrying
            } else {
                orderDao.updateOrder(order.copy(
                    status = OrderStatus.PENDING,
                    syncAttempts = newAttempts
                ))
                Result.retry()
            }
        }
    }

    private fun parseModifiersJson(json: String): List<ModifierRequest> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ModifierRequest(
                optionId = obj.getString("optionId"),
                optionName = obj.getString("optionName"),
                quantity = obj.getInt("quantity"),
                pricePoisha = obj.getLong("pricePoisha")
            )
        }
    }
}
