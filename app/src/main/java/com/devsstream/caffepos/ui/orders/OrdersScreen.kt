package com.devsstream.caffepos.ui.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orders") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            items(orders, key = { it.clientOrderId }) { order ->
                OrderCard(order = order, onRetry = { viewModel.retryOrder(order.clientOrderId) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun OrderCard(order: OrderEntity, onRetry: () -> Unit) {
    val statusColor = when (order.status) {
        OrderStatus.SYNCED -> Color(0xFF4CAF50)
        OrderStatus.FAILED -> Color(0xFFF44336)
        OrderStatus.SYNCING -> Color(0xFF2196F3)
        else -> Color(0xFFFF9800)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(order.receiptNo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("৳${order.payablePoisha / 100}")
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(order.status.name, color = statusColor, style = MaterialTheme.typography.labelMedium)
                if (order.serverOrderNo != null) {
                    Text("  ${order.serverOrderNo}", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (order.status == OrderStatus.FAILED) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Retry")
                    Spacer(Modifier.width(4.dp))
                    Text("Retry Sync")
                }
            }
        }
    }
}
