package com.devsstream.caffepos.data.db

import androidx.room.*
import com.devsstream.caffepos.core.model.OrderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Transaction
    @Query("SELECT * FROM orders ORDER BY createdAtMs ASC")
    fun observeAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE clientOrderId = :id")
    suspend fun getOrder(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE status IN (:statuses) ORDER BY createdAtMs ASC")
    suspend fun getOrdersByStatus(statuses: List<OrderStatus>): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<OrderLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId")
    suspend fun getLinesForOrder(orderId: String): List<OrderLineEntity>

    @Query("SELECT * FROM payments WHERE orderId = :orderId")
    suspend fun getPaymentsForOrder(orderId: String): List<PaymentEntity>
}

@Dao
interface SalesDao {
    /**
     * Returns all orders for a given server date, as a Flow so the summary
     * screen updates the moment a sale is saved (Part E).
     * serverTimeMs is stored in epoch ms; we filter by date in Asia/Dhaka.
     * We use a wide window: from midnight to next midnight in epoch ms.
     */
    @Query("""
        SELECT * FROM orders
        WHERE serverTimeMs >= :startMs AND serverTimeMs < :endMs
        ORDER BY serverTimeMs ASC
    """)
    fun observeOrdersForDay(startMs: Long, endMs: Long): Flow<List<OrderEntity>>

    @Query("""
        SELECT ol.productName, ol.productId, SUM(ol.quantity) as totalQty
        FROM order_lines ol
        JOIN orders o ON o.clientOrderId = ol.orderId
        WHERE o.serverTimeMs >= :startMs AND o.serverTimeMs < :endMs
        GROUP BY ol.productId
        ORDER BY totalQty DESC
        LIMIT 1
    """)
    fun observeTopItemForDay(startMs: Long, endMs: Long): Flow<TopItemResult?>

    @Query("""
        SELECT SUM(p.amountPoisha) as total, p.type
        FROM payments p
        JOIN orders o ON o.clientOrderId = p.orderId
        WHERE o.serverTimeMs >= :startMs AND o.serverTimeMs < :endMs
        GROUP BY p.type
    """)
    fun observePaymentTotalsForDay(startMs: Long, endMs: Long): Flow<List<PaymentTotalResult>>

    @Query("""
        SELECT COUNT(*) FROM orders
        WHERE serverTimeMs >= :startMs AND serverTimeMs < :endMs
        AND status NOT IN ('SYNCED')
    """)
    fun observeUnsyncedCountForDay(startMs: Long, endMs: Long): Flow<Int>
}

data class TopItemResult(
    val productName: String,
    val productId: String,
    val totalQty: Int
)

data class PaymentTotalResult(
    val total: Long,
    val type: String
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_cache WHERE id = 1")
    suspend fun getCachedCatalog(): CatalogCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCatalog(entity: CatalogCacheEntity)
}

@Dao
interface ServerTimeDao {
    @Query("SELECT * FROM server_time_offset WHERE id = 1")
    suspend fun getOffset(): ServerTimeOffsetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOffset(entity: ServerTimeOffsetEntity)
}

@Dao
interface ReceiptSequenceDao {
    @Query("SELECT * FROM receipt_sequences WHERE dateKey = :dateKey")
    suspend fun getSequence(dateKey: String): ReceiptSequenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSequence(entity: ReceiptSequenceEntity)
}
