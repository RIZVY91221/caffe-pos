package com.devsstream.caffepos.ui.cart

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBack: () -> Unit,
    cartViewModel: CartViewModel = hiltViewModel()
) {
    val cart by cartViewModel.cart.collectAsState()
    val cartState by cartViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Summary") },
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Order Complete", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Receipt: ${cartState.receiptNo}")
                    if (cartState.change > 0) {
                        Text("Change: ৳${cartState.change / 100}")
                    }
                    Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                        Text("New Order")
                    }
                }
                return@Column
            }

            if (cart.items.isEmpty() || pricing == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (cart.items.isEmpty()) Text("Cart is empty")
                    else CircularProgressIndicator()
                }
                return@Column
            }

            LazyColumn(Modifier.weight(1f)) {
                items(pricing.lines) { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${line.productName} x${line.quantity}", Modifier.weight(1f))
                        Text("৳${line.lineTotalPoisha / 100}")
                    }
                }
                if (pricing.p1BakeryDiscountPoisha > 0) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("Bakery 3-for-2", Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary)
                            Text("-৳${pricing.p1BakeryDiscountPoisha / 100}", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                if (pricing.p2HappyHourDiscountPoisha > 0) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("Happy Hour -10%", Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary)
                            Text("-৳${pricing.p2HappyHourDiscountPoisha / 100}", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Subtotal", Modifier.weight(1f))
                        Text("৳${formatPoisha(pricing.subtotalPoisha)}")
                    }
                }
                pricing.activeDiscount?.let { disc ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("Discount (${disc.type.name})", Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary)
                            Text("-৳${formatPoisha(disc.amountPoisha)}", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("VAT 5%", Modifier.weight(1f))
                        Text("৳${formatPoisha(pricing.vatPoisha)}")
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total Payable", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("৳${pricing.payablePoisha / 100}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            var couponInput by remember(cart.couponCode) { mutableStateOf(cart.couponCode ?: "") }
            OutlinedTextField(
                value = couponInput,
                onValueChange = { couponInput = it; cartViewModel.setCoupon(it) },
                label = { Text("Coupon Code (optional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            Spacer(Modifier.height(16.dp))

            when (cartState.paymentStep) {
                PaymentStep.SELECTING -> PaymentSelector(
                    cartViewModel = cartViewModel
                )
                PaymentStep.CARD_WAITING -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Waiting for card terminal...")
                            Text("Please do not close the app.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                PaymentStep.SPLIT_CASH -> {
                    Text("Card approved. Remaining: ৳${cartState.remainingPoisha / 100}")
                    var cash by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = cash, onValueChange = { cash = it },
                        label = { Text("Cash tendered (BDT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { cartViewModel.payCash(cash.toLongOrNull() ?: 0) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Confirm Cash") }
                }
                else -> {}
            }

            cartState.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun PaymentSelector(cartViewModel: CartViewModel) {
    var cashInput by remember { mutableStateOf("") }
    var showCash by remember { mutableStateOf(false) }
    var splitCardAmount by remember { mutableStateOf("") }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { showCash = !showCash }, modifier = Modifier.weight(1f)) {
                Text("Cash")
            }
            Button(onClick = { cartViewModel.payCard() }, modifier = Modifier.weight(1f)) {
                Text("Card")
            }
            OutlinedButton(onClick = {
                val amt = splitCardAmount.toLongOrNull()
                if (amt != null) cartViewModel.payCard(amt * 100)
            }, modifier = Modifier.weight(1f)) {
                Text("Split")
            }
        }
        if (showCash) {
            OutlinedTextField(
                value = cashInput,
                onValueChange = { cashInput = it },
                label = { Text("Cash tendered (BDT)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = { cartViewModel.payCash(cashInput.toLongOrNull() ?: 0) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) { Text("Confirm Cash Payment") }
        }
        OutlinedTextField(
            value = splitCardAmount,
            onValueChange = { splitCardAmount = it },
            label = { Text("Card amount for split (BDT)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

private fun formatPoisha(poisha: Long): String {
    val bdt = poisha / 100
    val rem = poisha % 100
    return if (rem == 0L) "$bdt" else "$bdt.${rem.toString().padStart(2, '0')}"
}
