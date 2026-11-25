package com.devsstream.caffepos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.devsstream.caffepos.core.model.OrderStatus
import com.devsstream.caffepos.core.model.PaymentType

@Database(
    entities = [
        OrderEntity::class,
        OrderLineEntity::class,
        PaymentEntity::class,
        CatalogCacheEntity::class,
        ServerTimeOffsetEntity::class,
        ReceiptSequenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun salesDao(): SalesDao
    abstract fun catalogDao(): CatalogDao
    abstract fun serverTimeDao(): ServerTimeDao
    abstract fun receiptSequenceDao(): ReceiptSequenceDao
}

class AppTypeConverters {
    @TypeConverter
    fun fromOrderStatus(value: OrderStatus): String = value.name

    @TypeConverter
    fun toOrderStatus(value: String): OrderStatus = OrderStatus.valueOf(value)

    @TypeConverter
    fun fromPaymentType(value: PaymentType): String = value.name

    @TypeConverter
    fun toPaymentType(value: String): PaymentType = PaymentType.valueOf(value)
}
