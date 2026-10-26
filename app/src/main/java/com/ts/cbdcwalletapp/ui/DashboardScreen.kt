package com.ts.cbdcwalletapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ts.cbdcwalletapp.data.CbdcToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: MainViewModel) {
    val walletState by viewModel.walletState.collectAsState()
    val tokensState by viewModel.tokensState.collectAsState()
    val transactionState by viewModel.transactionState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val localOfflineBalance by viewModel.localOfflineBalance.collectAsState()
    val localOfflineTokenCount by viewModel.localOfflineTokenCount.collectAsState()
    val currentUserId = (authState as? AuthState.Success)?.user?.userId
    var showMintDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            viewModel.getBalance()
            viewModel.getTokens()
            viewModel.getTransactions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CBDC Wallet", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Offline mode banner
            if (!isOnline) {
                OfflineBanner(
                    onOpenOfflineWallet = { navController.navigate(Screen.OfflineWallet.route) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Balance Card (tap to see token breakdown)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.WalletDetail.route) },
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val userName = (authState as? AuthState.Success)?.user?.name ?: "User"
                        Text(
                            text = "Hello, $userName",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Online balance (big)
                        Text(
                            text = if (isOnline) "Online Balance" else "Online Balance · unavailable while offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        when (val s = walletState) {
                            is WalletState.Success -> {
                                val symbol = if (s.wallet.currency == "INR") "₹" else s.wallet.currency
                                Text(
                                    text = "$symbol ${s.wallet.balance}",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${s.wallet.tokenCount} tokens",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                            is WalletState.Loading -> CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(28.dp).padding(top = 4.dp)
                            )
                            is WalletState.Error -> Text(
                                text = if (isOnline) "Error: ${s.message}" else "—",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            else -> Text(text = "—", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Offline balance (always sourced from local Room — never flashes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Offline Balance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "₹ $localOfflineBalance",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$localOfflineTokenCount tokens on this device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                            FilledTonalButton(
                                onClick = { navController.navigate(Screen.OfflineWallet.route) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) { Text("Manage") }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons — online operations disabled when offline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    text = "Add Funds",
                    icon = Icons.Default.Add,
                    enabled = isOnline
                ) { showMintDialog = true }
                ActionButton(
                    text = "Transfer",
                    icon = Icons.Default.ArrowForward,
                    enabled = isOnline
                ) { showTransferDialog = true }
                ActionButton(text = "Offline", icon = Icons.Default.Share) {
                    navController.navigate(Screen.OfflineWallet.route)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Transactions
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (val ts = transactionState) {
                is TransactionState.Success -> {
                    if (ts.transactions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No recent transactions", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(ts.transactions) { transaction ->
                                TransactionItem(transaction, currentUserId)
                            }
                        }
                    }
                }
                is TransactionState.Loading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is TransactionState.Error -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Error: ${ts.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }

    if (showMintDialog) {
        MintFundsDialog(
            onDismiss = { showMintDialog = false },
            onConfirm = { amount ->
                viewModel.mintFunds(amount)
                showMintDialog = false
            }
        )
    }

    if (showTransferDialog) {
        val tokens = (tokensState as? TokensState.Success)?.tokens ?: emptyList()
        TransferTokensDialog(
            tokens = tokens,
            onDismiss = { showTransferDialog = false },
            onConfirm = { phone, tokenIds ->
                viewModel.transferFunds(phone, tokenIds)
                showTransferDialog = false
            }
        )
    }

}

@Composable
fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                icon,
                contentDescription = text,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun OfflineBanner(onOpenOfflineWallet: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "You're offline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "Only offline transfers using already-reserved tokens are available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            TextButton(onClick = onOpenOfflineWallet) { Text("Open") }
        }
    }
}

@Composable
fun TransactionItem(transaction: com.ts.cbdcwalletapp.data.Transaction, currentUserId: String?) {
    val isMint = transaction.type == "MINT"
    val isSentByMe = transaction.fromWallet?.user?._id == currentUserId
    val isReceivedByMe = isMint || (transaction.toWallet?.user?._id == currentUserId && !isSentByMe)

    val title = when {
        isMint -> "Added Funds (Deposit)"
        isSentByMe -> "Sent to ${transaction.toWallet?.user?.name ?: "Unknown"}"
        isReceivedByMe -> "Received from ${transaction.fromWallet?.user?.name ?: "Unknown"}"
        else -> transaction.type
    }

    val amountColor = if (isReceivedByMe) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val amountSign = if (isReceivedByMe) "+" else "-"
    val symbol = if ((transaction.currency ?: "INR") == "INR") "₹" else (transaction.currency ?: "")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${transaction.tokens.size} tokens · ${transaction.createdAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountSign $symbol ${transaction.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Text(
                    text = transaction.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (transaction.status == "COMPLETED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun MintFundsDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Funds to Wallet") },
        text = {
            Column {
                Text(
                    "Enter amount in ₹. Digital currency tokens will be added to your wallet in standard denominations (₹500, 200, 100, 50, 20, 10, 5, 2, 1).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                amount.toIntOrNull()?.takeIf { it > 0 }?.let { onConfirm(it) }
            }) {
                Text("Add Funds")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Greedy breakdown of `target` using available denominations (largest first),
 * capped by how many of each the user holds. Returns the chosen quantity per
 * denomination, even if the breakdown can't reach `target` exactly (caller
 * checks).
 */
private fun suggestBreakdown(target: Int, available: Map<Int, Int>): Map<Int, Int> {
    val picks = mutableMapOf<Int, Int>()
    var remaining = target
    for (denom in available.keys.sortedDescending()) {
        if (remaining <= 0) break
        val have = available[denom] ?: 0
        val take = minOf(remaining / denom, have)
        if (take > 0) {
            picks[denom] = take
            remaining -= take * denom
        }
    }
    return picks
}

@Composable
fun TransferTokensDialog(
    tokens: List<CbdcToken>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }

    // tokens grouped by denomination (descending)
    val grouped = remember(tokens) {
        tokens.groupBy { it.denomination }.toSortedMap(compareByDescending { it })
    }
    val available: Map<Int, Int> = remember(grouped) {
        grouped.mapValues { (_, v) -> v.size }
    }

    // selected count per denomination — single source of truth for picks.
    val picks = remember { mutableStateMapOf<Int, Int>() }
    val total = picks.entries.sumOf { (denom, qty) -> denom * qty }
    val typedAmount = amountText.toIntOrNull()
    // mismatch = user typed an amount we couldn't satisfy exactly with available tokens
    val mismatch = typedAmount != null && typedAmount != total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer Tokens") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Recipient Phone") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // AMOUNT -> PICKS sync: every keystroke recomputes denomination breakdown.
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { newText ->
                        if (newText.all { c -> c.isDigit() }) {
                            amountText = newText
                            val target = newText.toIntOrNull()
                            picks.clear()
                            if (target != null && target > 0) {
                                suggestBreakdown(target, available).forEach { (d, q) -> picks[d] = q }
                            }
                        }
                    },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        {
                            Text(
                                "Best your tokens can make: ₹$total. Adjust denominations below or change amount.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Adjust quantity per denomination:",
                    style = MaterialTheme.typography.titleSmall
                )
                if (available.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You have no tokens. Add funds first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(available.keys.toList()) { denom ->
                            val have = available[denom] ?: 0
                            val qty = picks[denom] ?: 0
                            DenominationStepper(
                                denomination = denom,
                                available = have,
                                selected = qty,
                                onChange = { newQty ->
                                    val capped = newQty.coerceIn(0, have)
                                    if (capped == 0) picks.remove(denom) else picks[denom] = capped
                                    // PICKS -> AMOUNT sync: amount text always reflects current total.
                                    val newTotal = picks.entries.sumOf { (d, q) -> d * q }
                                    amountText = if (newTotal > 0) newTotal.toString() else ""
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Total to send: ₹ $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            val ready = phoneNumber.isNotBlank() && total > 0 && !mismatch
            Button(
                enabled = ready,
                onClick = {
                    // Materialize concrete tokenIds: take the first `qty` tokens of each denom.
                    val ids = mutableListOf<String>()
                    picks.forEach { (denom, qty) ->
                        grouped[denom]?.take(qty)?.forEach { ids.add(it.tokenId) }
                    }
                    if (ids.isNotEmpty()) onConfirm(phoneNumber, ids)
                }
            ) { Text("Send ₹ $total") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DenominationStepper(
    denomination: Int,
    available: Int,
    selected: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("₹ $denomination", fontWeight = FontWeight.Bold)
            Text(
                "you have $available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        OutlinedIconButton(
            onClick = { onChange((selected - 1).coerceAtLeast(0)) },
            enabled = selected > 0,
            modifier = Modifier.size(36.dp)
        ) { Text("−", fontWeight = FontWeight.Bold) }
        Text(
            text = "$selected",
            modifier = Modifier
                .widthIn(min = 28.dp)
                .padding(horizontal = 8.dp),
            fontWeight = FontWeight.Bold
        )
        OutlinedIconButton(
            onClick = { onChange((selected + 1).coerceAtMost(available)) },
            enabled = selected < available,
            modifier = Modifier.size(36.dp)
        ) { Text("+", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun OfflineSetupDialog(
    offlineBalance: Int,
    onDismiss: () -> Unit,
    onStartTransfer: (Boolean, Int) -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: Role Selection, 2: Amount Input
    var amount by remember { mutableStateOf("") }
    val parsed = amount.toIntOrNull()
    val insufficient = parsed != null && parsed > offlineBalance
    val canProceed = parsed != null && parsed > 0 && parsed <= offlineBalance

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 1) "Select Role" else "Enter Amount to Send") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step == 1) {
                    Text(
                        "Choose your role for this offline exchange.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { onStartTransfer(false, 0) }) { Text("Receive") }
                        Button(
                            onClick = { step = 2 },
                            enabled = offlineBalance > 0
                        ) { Text("Send") }
                    }
                    if (offlineBalance == 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "You have no offline tokens to send. Use Move Offline first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        "Available offline: ₹$offlineBalance",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.all { c -> c.isDigit() }) amount = it },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        isError = insufficient,
                        supportingText = if (insufficient) {
                            {
                                Text(
                                    "Exceeds your offline balance",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            if (step == 2) {
                Button(
                    enabled = canProceed,
                    onClick = { parsed?.let { onStartTransfer(true, it) } }
                ) { Text("Next") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
