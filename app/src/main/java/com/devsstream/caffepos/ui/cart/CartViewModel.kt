package com.devsstream.caffepos.ui.cart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsstream.caffepos.core.model.*
import com.devsstream.caffepos.core.pricing.priceOrder
import com.devsstream.caffepos.data.repository.CartStore
import com.devsstream.caffepos.data.repository.CatalogRepository
import com.devsstream.caffepos.data.repository.OrderRepository
import com.devsstream.caffepos.data.repository.ServerTimeRepository
import com.devsstream.caffepos.data.terminal.CardResult
import com.devsstream.caffepos.data.terminal.CardTerminalWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PaymentStep { SELECTING, CASH_INPUT, CARD_WAITING, SPLIT_CARD, SPLIT_CASH, DONE, ERROR }

data class CartUiState(
    val pricing: PriceBreakdown? = null,
    val catalog: Catalog? = null,
    val couponCode: String = "",
    val paymentStep: PaymentStep = PaymentStep.SELECTING,
    val cashTendered: Long = 0,
    val change: Long = 0,
    val cardRef: String? = null,
    val clientOrderId: String? = null,
    val collectedPayments: List<PaymentLine> = emptyList(),
    val remainingPoisha: Long = 0,
    val receiptNo: String? = null,
    val error: String? = null,
    val isCardWaiting: Boolean = false
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val cartStore: CartStore,
    private val catalogRepository: CatalogRepository,
    private val serverTimeRepository: ServerTimeRepository,
    private val orderRepository: OrderRepository,
    private val cardTerminalWrapper: CardTerminalWrapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    val cart: StateFlow<Cart> = cartStore.cart

    private var persistedClientOrderId: String
        get() = savedStateHandle["client_order_id"] ?: ""
        set(value) { savedStateHandle["client_order_id"] = value }

    private var persistedCardRef: String
        get() = savedStateHandle["card_ref"] ?: ""
        set(value) { savedStateHandle["card_ref"] = value }

    init {
        viewModelScope.launch {
            cartStore.cart.collect { currentCart ->
                if (currentCart.items.isNotEmpty()) {
                    loadPricing(currentCart.items, currentCart.couponCode)
                }
            }
        }
    }

    fun setCoupon(code: String) {
        cartStore.setCoupon(code)
    }

    fun loadPricing(items: List<CartItem>, couponCode: String?) {
        viewModelScope.launch {
            val catalog = catalogRepository.getCatalog()
            val serverTime = serverTimeRepository.getServerTime()
            val pricing = priceOrder(items, catalog, couponCode, serverTime)
            val clientOrderId = persistedClientOrderId.ifBlank {
                UUID.randomUUID().toString().also { persistedClientOrderId = it }
            }
            _uiState.update {
                it.copy(
                    pricing = pricing,
                    catalog = catalog,
                    remainingPoisha = pricing.payablePoisha - it.collectedPayments.sumOf { p -> p.amountPoisha },
                    clientOrderId = clientOrderId,
                    couponCode = couponCode ?: ""
                )
            }
        }
    }

    fun payCash(tenderedBdt: Long) {
        val items = cartStore.cart.value.items
        viewModelScope.launch {
            val state = _uiState.value
            val pricing = state.pricing ?: return@launch
            val tenderedPoisha = tenderedBdt * 100
            if (tenderedPoisha < state.remainingPoisha) {
                _uiState.update { it.copy(error = "Cash tendered is less than amount due") }
                return@launch
            }
            val change = tenderedPoisha - state.remainingPoisha
            val payment = PaymentLine(PaymentType.CASH, state.remainingPoisha)
            val allPayments = state.collectedPayments + payment

            val serverTime = serverTimeRepository.getServerTime()
            val catalog = state.catalog ?: return@launch
            val receiptNo = orderRepository.saveOrder(
                clientOrderId = state.clientOrderId!!,
                pricing = pricing,
                items = items,
                catalog = catalog,
                payments = allPayments,
                serverTime = serverTime
            )
            cartStore.clear()
            persistedClientOrderId = ""
            persistedCardRef = ""
            _uiState.update {
                it.copy(
                    paymentStep = PaymentStep.DONE,
                    cashTendered = tenderedPoisha,
                    change = change,
                    receiptNo = receiptNo,
                    error = null
                )
            }
        }
    }

    fun payCard(amountPoisha: Long? = null) {
        val items = cartStore.cart.value.items
        viewModelScope.launch {
            val state = _uiState.value
            val pricing = state.pricing ?: return@launch
            val chargeAmount = amountPoisha ?: state.remainingPoisha
            if (chargeAmount <= 0 || chargeAmount > state.remainingPoisha) {
                _uiState.update { it.copy(error = "Invalid card amount") }
                return@launch
            }

            // Create cardRef before calling charge() and persist in SavedStateHandle
            val cardRef = persistedCardRef.ifBlank {
                UUID.randomUUID().toString().also { persistedCardRef = it }
            }
            _uiState.update {
                it.copy(
                    isCardWaiting = true,
                    cardRef = cardRef,
                    paymentStep = PaymentStep.CARD_WAITING,
                    error = null
                )
            }

            val result = cardTerminalWrapper.charge(chargeAmount, cardRef)

            when (result) {
                is CardResult.Approved -> {
                    val payment = PaymentLine(PaymentType.CARD, chargeAmount, cardRef, result.authCode)
                    val allPayments = state.collectedPayments + payment
                    val remaining = state.remainingPoisha - chargeAmount

                    if (remaining <= 0) {
                        val serverTime = serverTimeRepository.getServerTime()
                        val catalog = state.catalog ?: return@launch
                        val receiptNo = orderRepository.saveOrder(
                            clientOrderId = state.clientOrderId!!,
                            pricing = pricing,
                            items = items,
                            catalog = catalog,
                            payments = allPayments,
                            serverTime = serverTime
                        )
                        cartStore.clear()
                        persistedClientOrderId = ""
                        persistedCardRef = ""
                        _uiState.update {
                            it.copy(
                                paymentStep = PaymentStep.DONE,
                                isCardWaiting = false,
                                receiptNo = receiptNo,
                                collectedPayments = allPayments,
                                remainingPoisha = 0
                            )
                        }
                    } else {
                        persistedCardRef = ""
                        _uiState.update {
                            it.copy(
                                isCardWaiting = false,
                                collectedPayments = allPayments,
                                remainingPoisha = remaining,
                                paymentStep = PaymentStep.SPLIT_CASH
                            )
                        }
                    }
                }
                is CardResult.Declined -> {
                    persistedCardRef = ""
                    _uiState.update {
                        it.copy(
                            isCardWaiting = false,
                            paymentStep = PaymentStep.SELECTING,
                            error = "Card declined: ${result.reason}"
                        )
                    }
                }
                CardResult.Unknown -> {
                    _uiState.update {
                        it.copy(
                            isCardWaiting = false,
                            paymentStep = PaymentStep.SELECTING,
                            error = "Card status unknown. Please verify terminal before retrying."
                        )
                    }
                }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
