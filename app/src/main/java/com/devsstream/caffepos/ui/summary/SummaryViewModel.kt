package com.devsstream.caffepos.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsstream.caffepos.data.db.SalesDao
import com.devsstream.caffepos.data.repository.ServerTimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SummaryUiState(
    val selectedDate: String = "",
    val totalPayablePoisha: Long = 0,
    val orderCount: Int = 0,
    val cashTotalPoisha: Long = 0,
    val cardTotalPoisha: Long = 0,
    val topItemName: String = "-",
    val topItemQty: Int = 0,
    val unsyncedCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val salesDao: SalesDao,
    private val serverTimeRepository: ServerTimeRepository
) : ViewModel() {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val displayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    private val _selectedLocalDate = MutableStateFlow<LocalDate?>(null)
    val uiState: StateFlow<SummaryUiState> = _selectedLocalDate
        .flatMapLatest { date ->
            if (date == null) flowOf(SummaryUiState())
            else {
                val startMs = date.atStartOfDay(dhaka).toInstant().toEpochMilli()
                val endMs = date.plusDays(1).atStartOfDay(dhaka).toInstant().toEpochMilli()
                combine(
                    salesDao.observeOrdersForDay(startMs, endMs),
                    salesDao.observeTopItemForDay(startMs, endMs),
                    salesDao.observePaymentTotalsForDay(startMs, endMs),
                    salesDao.observeUnsyncedCountForDay(startMs, endMs)
                ) { orders, topItem, paymentTotals, unsynced ->
                    val cashTotal = paymentTotals.find { it.type == "CASH" }?.total ?: 0L
                    val cardTotal = paymentTotals.find { it.type == "CARD" }?.total ?: 0L
                    SummaryUiState(
                        selectedDate = date.format(displayFormatter),
                        totalPayablePoisha = orders.sumOf { it.payablePoisha },
                        orderCount = orders.size,
                        cashTotalPoisha = cashTotal,
                        cardTotalPoisha = cardTotal,
                        topItemName = topItem?.productName ?: "-",
                        topItemQty = topItem?.totalQty ?: 0,
                        unsyncedCount = unsynced
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SummaryUiState())

    init {
        viewModelScope.launch {
            val serverTime = serverTimeRepository.getServerTime()
            _selectedLocalDate.value = serverTime.toLocalDate()
        }
    }

    fun changeDay(delta: Int) {
        _selectedLocalDate.update { it?.plusDays(delta.toLong()) }
    }
}
