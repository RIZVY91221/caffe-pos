package com.devsstream.caffepos.ui.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devsstream.caffepos.core.model.OrderStatus
import com.devsstream.caffepos.data.db.OrderEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    viewModel: OrdersViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsState(initial = emptyList())
    var filterStatus by remember { mutableStateOf<OrderStatus?>(null) }

    val filtered = remember(orders, filterStatus) {
        if (filterStatus == null) orders.reversed()
        else orders.filter { it.status == filterStatus }.reversed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Orders (${orders.size})") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterStatus == null,
                    onClick = { filterStatus = null },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filterStatus == OrderStatus.PENDING,
                    onClick = { filterStatus = OrderStatus.PENDING },
                    label = { Text("Pending") }
                )
                FilterChip(
                    selected = filterStatus == OrderStatus.SYNCED,
                    onClick = { filterStatus = OrderStatus.SYNCED },
                    label = { Text("Synced") }
                )
                FilterChip(
                    selected = filterStatus == OrderStatus.FAILED,
                    onClick = { filterStatus = OrderStatus.FAILED },
                    label = { Text("Failed") }
                )
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No orders found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.clientOrderId }) { order ->
                        OrderCard(order = order, onRetry = { viewModel.retryOrder(order.clientOrderId) })
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: OrderEntity, onRetry: () -> Unit) {
    val (bgColor, fgColor) = when (order.status) {
        OrderStatus.SYNCED -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        OrderStatus.FAILED -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        OrderStatus.SYNCING -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        else -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(order.receiptNo, style = MaterialTheme.typography.titleMedium)
                Text(
                    "৳${order.payablePoisha / 100}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = bgColor, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        order.status.name,
                        color = fgColor,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (order.serverOrderNo != null) {
                    Text(
                        order.serverOrderNo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (order.status == OrderStatus.FAILED) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry Sync")
                }
            }
        }
    }
}
