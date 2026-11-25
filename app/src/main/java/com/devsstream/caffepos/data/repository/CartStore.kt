package com.devsstream.caffepos.data.repository

import com.devsstream.caffepos.core.model.Cart
import com.devsstream.caffepos.core.model.CartItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartStore @Inject constructor() {
    private val _cart = MutableStateFlow(Cart())
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    fun addItem(item: CartItem) {
        _cart.update { it.copy(items = it.items + item) }
    }

    fun removeItem(index: Int) {
        _cart.update {
            if (index in it.items.indices) {
                it.copy(items = it.items.toMutableList().apply { removeAt(index) })
            } else it
        }
    }

    fun setCoupon(code: String?) {
        _cart.update { it.copy(couponCode = code?.trim()?.ifBlank { null }) }
    }

    fun clear() {
        _cart.value = Cart()
    }
}
