package com.devsstream.caffepos.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.devsstream.caffepos.core.model.OrderStatus
import com.devsstream.caffepos.core.model.PaymentType

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val clientOrderId: String,
    val receiptNo: String,
    val serverOrderNo: String?,
    val status: OrderStatus,
    val payablePoisha: Long,
    val serverTimeMs: Long,          // server time (Asia/Dhaka epoch ms)
    val createdAtMs: Long,           // local creation time for ordering
    val syncAttempts: Int = 0,
    val isDuplicate: Boolean = false
)

@Entity(
    tableName = "order_lines",
    foreignKeys = [ForeignKey(
        entity = OrderEntity::class,
        parentColumns = ["clientOrderId"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("orderId")]
)
data class OrderLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPricePoisha: Long,
    val lineTotalPoisha: Long,
    val modifiersJson: String          // JSON array of selected modifiers
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = OrderEntity::class,
        parentColumns = ["clientOrderId"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("orderId")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val type: PaymentType,
    val amountPoisha: Long,
    val cardRef: String?,
    val authCode: String?
)

@Entity(tableName = "catalog_cache")
data class CatalogCacheEntity(
    @PrimaryKey val id: Int = 1,       // single row
    val version: Int,
    val catalogJson: String,
    val fetchedAtMs: Long
)

@Entity(tableName = "server_time_offset")
data class ServerTimeOffsetEntity(
    @PrimaryKey val id: Int = 1,       // single row
    val offsetMs: Long,                // serverTime - deviceTime in ms
    val fetchedAtMs: Long
)

@Entity(tableName = "receipt_sequences")
data class ReceiptSequenceEntity(
    @PrimaryKey val dateKey: String,   // "yyyyMMdd" in server date (Asia/Dhaka)
    val lastSequence: Int
)
