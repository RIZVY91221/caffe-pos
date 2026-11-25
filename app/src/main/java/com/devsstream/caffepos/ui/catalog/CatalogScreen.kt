package com.devsstream.caffepos.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devsstream.caffepos.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onGoToCart: () -> Unit,
    onGoToOrders: () -> Unit,
    onGoToSummary: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Café POS — New Order") },
                actions = {
                    IconButton(onClick = onGoToOrders) { Icon(Icons.AutoMirrored.Filled.List, "Orders") }
                    IconButton(onClick = onGoToSummary) { Icon(Icons.Default.BarChart, "Summary") }
                }
            )
        },
        floatingActionButton = {
            if (state.cart.items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onGoToCart,
                    icon = { Icon(Icons.Default.ShoppingCart, "Cart") },
                    text = { Text("Cart (${state.cart.items.sumOf { it.quantity }})") }
                )
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.error != null && state.catalog == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "Error loading catalog", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::loadCatalog) {
                        Text("Retry")
                    }
                }
            }
            return@Scaffold
        }

        val catalog = state.catalog ?: return@Scaffold

        Row(Modifier.padding(padding).fillMaxSize()) {
            // Product list
            LazyColumn(
                Modifier.weight(0.4f).fillMaxHeight(),
                contentPadding = PaddingValues(8.dp)
            ) {
                val grouped = catalog.products.groupBy { it.category }
                grouped.forEach { (category, products) ->
                    item {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    items(products) { product ->
                        ProductCard(
                            product = product,
                            isSelected = state.selectedProduct?.id == product.id,
                            onClick = { viewModel.selectProduct(product) }
                        )
                    }
                }
            }

            // Modifier panel
            if (state.selectedProduct != null) {
                ModifierPanel(
                    modifier = Modifier.weight(0.6f),
                    state = state,
                    catalog = catalog,
                    onSelectSize = viewModel::selectSize,
                    onToggleModifier = viewModel::toggleModifier,
                    onAdjustQty = viewModel::adjustModifierQty,
                    onSetQuantity = viewModel::setQuantity,
                    onAddToCart = viewModel::addToCart
                )
            }
        }
    }
}

@Composable
fun ProductCard(product: Product, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(product.name, fontWeight = FontWeight.SemiBold)
            Text("৳${product.basePriceBdt}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ModifierPanel(
    modifier: Modifier,
    state: CatalogUiState,
    catalog: com.devsstream.caffepos.core.model.Catalog,
    onSelectSize: (String) -> Unit,
    onToggleModifier: (String, String, Int?) -> Unit,
    onAdjustQty: (String, String, Int) -> Unit,
    onSetQuantity: (Int) -> Unit,
    onAddToCart: () -> Unit
) {
    val product = state.selectedProduct ?: return
    LazyColumn(modifier.padding(8.dp), contentPadding = PaddingValues(8.dp)) {
        item { Text(product.name, style = MaterialTheme.typography.titleLarge) }

        // Sizes
        if (product.sizes.isNotEmpty()) {
            item {
                Text("Size *", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    product.sizes.forEach { size ->
                        FilterChip(
                            selected = state.selectedSizeId == size.id,
                            onClick = { onSelectSize(size.id) },
                            label = { Text("${size.label} (+৳${size.extraPriceBdt})") }
                        )
                    }
                }
            }
        }

        // Modifier groups
        val applicableGroups = product.modifierGroupIds.mapNotNull { id ->
            catalog.modifierGroups.find { it.id == id }
        }
        for (group in applicableGroups) {
            item {
                val limit = buildString {
                    if (group.max != null) append("max ${group.max}")
                    if (group.maxDistinct != null) append("max ${group.maxDistinct} types")
                }
                Text("${group.name} ($limit)", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp))
            }
            for (option in group.options) {
                item {
                    val sel = state.selectedModifiers.find {
                        it.optionId == option.id && it.groupId == group.id
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = sel != null, onCheckedChange = {
                            onToggleModifier(group.id, option.id, group.maxPerOption)
                        })
                        Text("${option.name} (+৳${option.priceBdt})", Modifier.weight(1f))
                        if (sel != null && group.maxPerOption != null && group.maxPerOption > 1) {
                            IconButton(onClick = { onAdjustQty(group.id, option.id, -1) }) {
                                Icon(Icons.Default.Remove, "-")
                            }
                            Text("${sel.quantity}")
                            IconButton(onClick = { onAdjustQty(group.id, option.id, 1) }) {
                                Icon(Icons.Default.Add, "+")
                            }
                        }
                    }
                }
            }
        }

        // Quantity
        item {
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Qty:", Modifier.padding(end = 8.dp))
                IconButton(onClick = { onSetQuantity(state.quantity - 1) }) {
                    Icon(Icons.Default.Remove, "-")
                }
                Text("${state.quantity}")
                IconButton(onClick = { onSetQuantity(state.quantity + 1) }) {
                    Icon(Icons.Default.Add, "+")
                }
            }
        }

        // Violations
        if (state.violations.isNotEmpty()) {
            item {
                state.violations.forEach { v ->
                    Text("⚠ ${v.message}", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // Add to cart button
        item {
            Button(
                onClick = onAddToCart,
                enabled = state.violations.isEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add to Order")
            }
        }
    }
}
