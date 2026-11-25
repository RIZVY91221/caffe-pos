package com.devsstream.caffepos.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsstream.caffepos.data.db.OrderEntity
import com.devsstream.caffepos.data.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    val orders: Flow<List<OrderEntity>> = orderRepository.observeAllOrders()

    fun retryOrder(clientOrderId: String) {
        viewModelScope.launch {
            orderRepository.retryOrder(clientOrderId)
        }
    }
}
