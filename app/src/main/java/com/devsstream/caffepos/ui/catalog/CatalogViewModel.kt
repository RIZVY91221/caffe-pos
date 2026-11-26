package com.devsstream.caffepos.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsstream.caffepos.core.catalog.validate
import com.devsstream.caffepos.core.model.*
import com.devsstream.caffepos.data.repository.CartStore
import com.devsstream.caffepos.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUiState(
    val catalog: Catalog? = null,
    val selectedProduct: Product? = null,
    val selectedSizeId: String? = null,
    val selectedModifiers: List<SelectedModifier> = emptyList(),
    val quantity: Int = 1,
    val violations: List<Violation> = emptyList(),
    val cart: Cart = Cart(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val cartStore: CartStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        loadCatalog()
        viewModelScope.launch {
            cartStore.cart.collect { cart ->
                _uiState.update { it.copy(cart = cart) }
            }
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val catalog = catalogRepository.getCatalog()
                _uiState.update { it.copy(catalog = catalog, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to load catalog", isLoading = false) }
            }
        }
    }

    fun selectProduct(product: Product) {
        _uiState.update {
            it.copy(
                selectedProduct = product,
                selectedSizeId = null,
                selectedModifiers = emptyList(),
                quantity = 1
            )
        }
        revalidate()
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedProduct = null,
                selectedSizeId = null,
                selectedModifiers = emptyList(),
                quantity = 1,
                violations = emptyList()
            )
        }
    }

    fun selectSize(sizeId: String) {
        _uiState.update { it.copy(selectedSizeId = sizeId) }
        revalidate()
    }

    fun toggleModifier(groupId: String, optionId: String, maxPerOption: Int?) {
        _uiState.update { state ->
            val existing = state.selectedModifiers.find { it.optionId == optionId && it.groupId == groupId }
            val newModifiers = if (existing != null) {
                state.selectedModifiers - existing
            } else {
                state.selectedModifiers + SelectedModifier(optionId, groupId, 1)
            }
            state.copy(selectedModifiers = newModifiers)
        }
        revalidate()
    }

    fun adjustModifierQty(groupId: String, optionId: String, delta: Int) {
        _uiState.update { state ->
            val newMods = state.selectedModifiers.map { mod ->
                if (mod.optionId == optionId && mod.groupId == groupId) {
                    mod.copy(quantity = (mod.quantity + delta).coerceIn(1, 3))
                } else mod
            }
            state.copy(selectedModifiers = newMods)
        }
        revalidate()
    }

    fun setQuantity(qty: Int) {
        _uiState.update { it.copy(quantity = qty.coerceAtLeast(1)) }
    }

    fun addToCart() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        if (state.violations.isNotEmpty()) return
        val item = CartItem(
            productId = product.id,
            sizeId = state.selectedSizeId,
            modifiers = state.selectedModifiers,
            quantity = state.quantity
        )
        cartStore.addItem(item)
        _uiState.update {
            it.copy(
                selectedProduct = null,
                selectedSizeId = null,
                selectedModifiers = emptyList(),
                quantity = 1,
                violations = emptyList()
            )
        }
    }

    private fun revalidate() {
        val state = _uiState.value
        val catalog = state.catalog ?: return
        val product = state.selectedProduct ?: run {
            _uiState.update { it.copy(violations = emptyList()) }
            return
        }
        val item = CartItem(
            productId = product.id,
            sizeId = state.selectedSizeId,
            modifiers = state.selectedModifiers,
            quantity = state.quantity
        )
        val violations = validate(item, catalog)
        _uiState.update { it.copy(violations = violations) }
    }
}
