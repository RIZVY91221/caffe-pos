package com.devsstream.caffepos.di

import android.content.Context
import androidx.room.Room
import com.devsstream.caffepos.data.api.FakeApi
import com.devsstream.caffepos.data.api.FakeApiImpl
import com.devsstream.caffepos.data.db.*
import com.devsstream.terminal.LegacyCardTerminal
import com.devsstream.caffepos.data.terminal.CardTerminalWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "caffepos.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideOrderDao(db: AppDatabase): OrderDao = db.orderDao()
    @Provides fun provideSalesDao(db: AppDatabase): SalesDao = db.salesDao()
    @Provides fun provideCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()
    @Provides fun provideServerTimeDao(db: AppDatabase): ServerTimeDao = db.serverTimeDao()
    @Provides fun provideReceiptSequenceDao(db: AppDatabase): ReceiptSequenceDao = db.receiptSequenceDao()

    @Provides
    @Singleton
    fun provideFakeApi(): FakeApi = FakeApiImpl()

    @Provides
    @Singleton
    fun provideLegacyCardTerminal(): LegacyCardTerminal = LegacyCardTerminal()

    @Provides
    @Singleton
    fun provideCardTerminalWrapper(terminal: LegacyCardTerminal): CardTerminalWrapper =
        CardTerminalWrapper(terminal)
}
