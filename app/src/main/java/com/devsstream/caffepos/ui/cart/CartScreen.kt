package com.devsstream.caffepos.ui.cart

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private enum class TenderMode { CASH, CARD, SPLIT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBack: () -> Unit,
    cartViewModel: CartViewModel = hiltViewModel()
) {
    val cart by cartViewModel.cart.collectAsState()
    val cartState by cartViewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Order Summary") },
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
        val pricing = cartState.pricing

        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize()
        ) {
            if (cartState.paymentStep == PaymentStep.DONE) {
                OrderCompleteView(
                    receiptNo = cartState.receiptNo ?: "",
                    changePoisha = cartState.change,
                    onDone = onBack
                )
                return@Column
            }

            if (cart.items.isEmpty() || pricing == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (cart.items.isEmpty()) Text("Your cart is empty")
                    else CircularProgressIndicator()
                }
                return@Column
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pricing.lines) { line ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.productName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${line.quantity} × ৳${formatPoisha(line.unitPricePoisha)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("৳${formatPoisha(line.lineTotalPoisha)}", style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    if (pricing.p1BakeryDiscountPoisha > 0) {
                        item {
                            SummaryLine(
                                label = "Bakery 3-for-2 (P1)",
                                value = "-৳${formatPoisha(pricing.p1BakeryDiscountPoisha)}",
                                highlight = true
                            )
                        }
                    }
                    if (pricing.p2HappyHourDiscountPoisha > 0) {
                        item {
                            SummaryLine(
                                label = "Happy Hour 10% (P2)",
                                value = "-৳${formatPoisha(pricing.p2HappyHourDiscountPoisha)}",
                                highlight = true
                            )
                        }
                    }

                    item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

                    item {
                        SummaryLine("Subtotal", "৳${formatPoisha(pricing.subtotalPoisha)}")
                    }
                    pricing.activeDiscount?.let { disc ->
                        val label = when (disc.type) {
                            com.devsstream.caffepos.core.model.DiscountType.C1_COUPON -> "Coupon (DEVS10)"
                            com.devsstream.caffepos.core.model.DiscountType.T1_THRESHOLD -> "Order Discount (T1)"
                        }
                        item {
                            SummaryLine(
                                label = label,
                                value = "-৳${formatPoisha(disc.amountPoisha)}",
                                highlight = true
                            )
                        }
                    }
                    item {
                        SummaryLine("VAT (5%)", "৳${formatPoisha(pricing.vatPoisha)}")
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Payable", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "৳${pricing.payablePoisha / 100}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            var couponInput by remember(cart.couponCode) { mutableStateOf(cart.couponCode ?: "") }
            OutlinedTextField(
                value = couponInput,
                onValueChange = { couponInput = it; cartViewModel.setCoupon(it) },
                label = { Text("Coupon Code") },
                placeholder = { Text("e.g. DEVS10") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            when (cartState.paymentStep) {
                PaymentStep.SELECTING -> PaymentSelector(
                    payablePoisha = pricing.payablePoisha,
                    cartViewModel = cartViewModel
                )
                PaymentStep.CARD_WAITING -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Processing Card Payment...", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Waiting for terminal response. Do not close the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                PaymentStep.SPLIT_CASH -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Card Approved · Remaining Cash: ৳${cartState.remainingPoisha / 100}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            var cash by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = cash,
                                onValueChange = { cash = it },
                                label = { Text("Cash Tendered (BDT)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { cartViewModel.payCash(cash.toLongOrNull() ?: 0) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) { Text("Complete Split Payment") }
                        }
                    }
                }
                else -> {}
            }

            cartState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderCompleteView(receiptNo: String, changePoisha: Long, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(56.dp)
                )
                Text("Order Saved", style = MaterialTheme.typography.titleLarge)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        receiptNo,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                if (changePoisha > 0) {
                    Text(
                        "Change Due: ৳${changePoisha / 100}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("New Order")
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSelector(payablePoisha: Long, cartViewModel: CartViewModel) {
    var mode by remember { mutableStateOf(TenderMode.CASH) }
    var cashInput by remember { mutableStateOf("") }
    var splitCardAmount by remember { mutableStateOf("") }
    val payableBdt = payablePoisha / 100

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TenderMode.entries.forEachIndexed { index, tenderMode ->
                    SegmentedButton(
                        selected = mode == tenderMode,
                        onClick = { mode = tenderMode; cartViewModel.clearError() },
                        shape = SegmentedButtonDefaults.itemShape(index, TenderMode.entries.size),
                        label = {
                            Text(
                                when (tenderMode) {
                                    TenderMode.CASH -> "Cash"
                                    TenderMode.CARD -> "Card"
                                    TenderMode.SPLIT -> "Split"
                                }
                            )
                        }
                    )
                }
            }

            when (mode) {
                TenderMode.CASH -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(payableBdt, 500L, 1000L, 2000L).distinct().take(4).forEach { preset ->
                            SuggestionChip(
                                onClick = { cashInput = preset.toString() },
                                label = { Text("৳$preset") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = cashInput,
                        onValueChange = { cashInput = it },
                        label = { Text("Cash Tendered (BDT)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { cartViewModel.payCash(cashInput.toLongOrNull() ?: 0) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Confirm Cash · ৳$payableBdt")
                    }
                }
                TenderMode.CARD -> {
                    Button(
                        onClick = { cartViewModel.payCard() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Charge Card · ৳$payableBdt")
                    }
                }
                TenderMode.SPLIT -> {
                    OutlinedTextField(
                        value = splitCardAmount,
                        onValueChange = { splitCardAmount = it },
                        label = { Text("Card Amount (BDT)") },
                        placeholder = { Text("Remaining will be paid in cash") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val amt = splitCardAmount.toLongOrNull() ?: 0L
                            cartViewModel.payCard(amt * 100)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Charge Card First")
                    }
                }
            }
        }
    }
}

private fun formatPoisha(poisha: Long): String {
    val bdt = poisha / 100
    val rem = poisha % 100
    return if (rem == 0L) "$bdt" else "$bdt.${rem.toString().padStart(2, '0')}"
}
