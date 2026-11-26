package com.devsstream.caffepos.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    var selectedCategory by remember { mutableStateOf<ProductCategory?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Café POS", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "New Order",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onGoToOrders) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Orders")
                    }
                    IconButton(onClick = onGoToSummary) {
                        Icon(Icons.Default.BarChart, contentDescription = "Sales Summary")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.cart.items.isNotEmpty()) {
                val totalUnits = state.cart.items.sumOf { it.quantity }
                ExtendedFloatingActionButton(
                    onClick = onGoToCart,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    text = { Text("View Order ($totalUnits)") }
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.error ?: "Unable to load catalog",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::loadCatalog) {
                        Text("Retry")
                    }
                }
            }
            return@Scaffold
        }

        val catalog = state.catalog ?: return@Scaffold
        val filteredProducts = remember(catalog, selectedCategory) {
            if (selectedCategory == null) catalog.products
            else catalog.products.filter { it.category == selectedCategory }
        }

        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            if (isWideScreen) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(0.5f).fillMaxHeight()) {
                        CategoryFilterRow(
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it }
                        )
                        ProductGrid(
                            products = filteredProducts,
                            selectedProductId = state.selectedProduct?.id,
                            onSelectProduct = viewModel::selectProduct,
                            columns = 2
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Box(Modifier.weight(0.5f).fillMaxHeight()) {
                        if (state.selectedProduct != null) {
                            ModifierPanel(
                                state = state,
                                catalog = catalog,
                                onSelectSize = viewModel::selectSize,
                                onToggleModifier = viewModel::toggleModifier,
                                onAdjustQty = viewModel::adjustModifierQty,
                                onSetQuantity = viewModel::setQuantity,
                                onAddToCart = viewModel::addToCart
                            )
                        } else {
                            EmptySelectionPlaceholder()
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    CategoryFilterRow(
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it }
                    )
                    ProductGrid(
                        products = filteredProducts,
                        selectedProductId = state.selectedProduct?.id,
                        onSelectProduct = viewModel::selectProduct,
                        columns = 2
                    )
                }

                if (state.selectedProduct != null) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { viewModel.clearSelection() },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ) {
                        ModifierPanel(
                            state = state,
                            catalog = catalog,
                            onSelectSize = viewModel::selectSize,
                            onToggleModifier = viewModel::toggleModifier,
                            onAdjustQty = viewModel::adjustModifierQty,
                            onSetQuantity = viewModel::setQuantity,
                            onAddToCart = viewModel::addToCart,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: ProductCategory?,
    onSelectCategory: (ProductCategory?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text("All") },
                shape = RoundedCornerShape(10.dp)
            )
        }
        items(ProductCategory.entries) { category ->
            val label = when (category) {
                ProductCategory.BEVERAGE -> "Beverages"
                ProductCategory.FOOD -> "Food"
                ProductCategory.BAKERY -> "Bakery"
            }
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = { Text(label) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<Product>,
    selectedProductId: String?,
    onSelectProduct: (Product) -> Unit,
    columns: Int
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(products, key = { it.id }) { product ->
            ProductCard(
                product = product,
                isSelected = product.id == selectedProductId,
                onClick = { onSelectProduct(product) }
            )
        }
    }
}

@Composable
fun ProductCard(product: Product, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = product.category.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "৳${product.basePriceBdt}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptySelectionPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.LocalCafe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select an item to customize",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ModifierPanel(
    state: CatalogUiState,
    catalog: Catalog,
    onSelectSize: (String) -> Unit,
    onToggleModifier: (String, String, Int?) -> Unit,
    onAdjustQty: (String, String, Int) -> Unit,
    onSetQuantity: (Int) -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val product = state.selectedProduct ?: return
    val unitPreviewBdt = remember(product, state.selectedSizeId, state.selectedModifiers, catalog) {
        val sizeExtra = product.sizes.find { it.id == state.selectedSizeId }?.extraPriceBdt ?: 0
        val modExtra = state.selectedModifiers.sumOf { sel ->
            val group = catalog.modifierGroups.find { it.id == sel.groupId }
            val option = group?.options?.find { it.id == sel.optionId }
            (option?.priceBdt ?: 0) * sel.quantity
        }
        product.basePriceBdt + sizeExtra + modExtra
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Base ৳${product.basePriceBdt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "৳${unitPreviewBdt * state.quantity}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (product.sizes.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Size (Required)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        product.sizes.forEach { size ->
                            val isSizeSelected = state.selectedSizeId == size.id
                            SizeOptionButton(
                                label = size.label,
                                extraPriceBdt = size.extraPriceBdt,
                                selected = isSizeSelected,
                                onClick = { onSelectSize(size.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        val applicableGroups = product.modifierGroupIds.mapNotNull { id ->
            catalog.modifierGroups.find { it.id == id }
        }
        for (group in applicableGroups) {
            item {
                val limit = buildString {
                    if (group.max != null) append("Max ${group.max}")
                    if (group.maxDistinct != null) {
                        if (isNotEmpty()) append(" · ")
                        append("Up to ${group.maxDistinct} types")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(group.name, style = MaterialTheme.typography.labelLarge)
                    if (limit.isNotEmpty()) {
                        Text(
                            limit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(group.options, key = { "${group.id}_${it.id}" }) { option ->
                val sel = state.selectedModifiers.find {
                    it.optionId == option.id && it.groupId == group.id
                }
                val isChecked = sel != null
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isChecked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleModifier(group.id, option.id, group.maxPerOption) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleModifier(group.id, option.id, group.maxPerOption) }
                        )
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                        Text(
                            text = "+৳${option.priceBdt}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        if (sel != null && (group.maxPerOption == null || group.maxPerOption > 1) && (group.max == null || group.max > 1)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = { onAdjustQty(group.id, option.id, -1) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    "${sel.quantity}",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                                FilledTonalIconButton(
                                    onClick = { onAdjustQty(group.id, option.id, 1) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Quantity", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = { onSetQuantity(state.quantity - 1) },
                        enabled = state.quantity > 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        "${state.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { onSetQuantity(state.quantity + 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }
        }

        if (state.violations.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.violations.forEach { v ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    v.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onAddToCart,
                enabled = state.violations.isEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Add to Order · ৳${unitPreviewBdt * state.quantity}",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SizeOptionButton(
    label: String,
    extraPriceBdt: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier.height(56.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (extraPriceBdt == 0) "Included" else "+৳$extraPriceBdt",
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
