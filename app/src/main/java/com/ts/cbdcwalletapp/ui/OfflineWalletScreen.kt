package com.ts.cbdcwalletapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ts.cbdcwalletapp.data.local.LocalToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineWalletScreen(navController: NavController, viewModel: MainViewModel) {
    val walletState by viewModel.walletState.collectAsState()
    val localTokens by viewModel.localOfflineTokens.collectAsState()
    val offlineBalance by viewModel.localOfflineBalance.collectAsState()
    val offlineCount by viewModel.localOfflineTokenCount.collectAsState()
    val opState by viewModel.offlineState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val context = LocalContext.current

    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (isOnline) viewModel.getBalance()
    }

    LaunchedEffect(opState) {
        when (val s = opState) {
            is OfflineOpState.Success -> {
                Toast.makeText(context, s.message, Toast.LENGTH_SHORT).show()
                viewModel.clearOfflineOpStatus()
            }
            is OfflineOpState.Error -> {
                Toast.makeText(context, "Error: ${s.message}", Toast.LENGTH_LONG).show()
                viewModel.clearOfflineOpStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Wallet", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (!isOnline) {
                AssistChipNetwork()
                Spacer(modifier = Modifier.height(12.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text("Offline Balance", color = Color.White.copy(alpha = 0.85f))
                        Text(
                            "INR $offlineBalance",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$offlineCount tokens stored on this device",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showCheckoutDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = isOnline && opState !is OfflineOpState.Loading
                ) { Text("Move Offline") }
                OutlinedButton(
                    onClick = { showWithdrawDialog = true },
                    enabled = isOnline && localTokens.isNotEmpty() && opState !is OfflineOpState.Loading,
                    modifier = Modifier.weight(1f)
                ) { Text("Bring Online") }
            }
            if (!isOnline) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Move / Bring need a network connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showSendDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send / Receive Offline (QR)") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.syncOfflineState() },
                enabled = isOnline && opState !is OfflineOpState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sync Offline Activity") }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Tokens stored on this device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (localTokens.isEmpty()) {
                Text(
                    "No offline tokens yet. Tap Move Offline while online to set aside funds for offline use.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                val grouped = localTokens.groupBy { it.denomination }.toSortedMap(compareByDescending { it })
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(grouped.keys.toList()) { denom ->
                        val toks = grouped[denom].orEmpty()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("INR $denom", fontWeight = FontWeight.Bold)
                                    Text(
                                        "${toks.size} tokens",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    "= INR ${denom * toks.size}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCheckoutDialog) {
        CheckoutOfflineDialog(
            onlineBalance = (walletState as? WalletState.Success)?.wallet?.balance ?: 0,
            onDismiss = { showCheckoutDialog = false },
            onConfirm = { amount ->
                viewModel.checkoutToOffline(amount)
                showCheckoutDialog = false
            }
        )
    }

    if (showWithdrawDialog) {
        WithdrawOfflineDialog(
            tokens = localTokens,
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { ids ->
                viewModel.withdrawFromOffline(ids)
                showWithdrawDialog = false
            }
        )
    }

    if (showSendDialog) {
        OfflineSetupDialog(
            offlineBalance = offlineBalance,
            onDismiss = { showSendDialog = false },
            onStartTransfer = { isSender, amount ->
                viewModel.startOfflineTransfer(isSender, amount)
                showSendDialog = false
                navController.navigate(Screen.OfflineTransfer.route)
            }
        )
    }
}

@Composable
private fun AssistChipNetwork() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = "Offline mode: QR transfers only",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun CheckoutOfflineDialog(
    onlineBalance: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val amt = amount.toIntOrNull() ?: 0
    val valid = amt in 1..onlineBalance

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move funds Offline") },
        text = {
            Column {
                Text(
                    "Reserves some online tokens as offline-only tokens on this device.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Online balance: INR $onlineBalance")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = amount.isNotEmpty() && !valid,
                    supportingText = if (amount.isNotEmpty() && amt > onlineBalance) {
                        { Text("Exceeds online balance", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onConfirm(amt) }) { Text("Move INR $amt") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WithdrawOfflineDialog(
    tokens: List<LocalToken>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val grouped = remember(tokens) {
        tokens.groupBy { it.denomination }.toSortedMap(compareByDescending { it })
    }
    val selectedAmount = tokens.filter { selected[it.tokenId] == true }.sumOf { it.denomination }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bring tokens back Online") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Pick offline tokens to return to online use.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    grouped.forEach { (denom, list) ->
                        item(key = "h-$denom") {
                            Text("INR $denom (${list.size})", fontWeight = FontWeight.Bold)
                        }
                        items(list, key = { it.tokenId }) { tok ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected[tok.tokenId] == true,
                                    onCheckedChange = { selected[tok.tokenId] = it }
                                )
                                Text(tok.serialNumber, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Selected: INR $selectedAmount", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(
                enabled = selectedAmount > 0,
                onClick = {
                    val ids = tokens.filter { selected[it.tokenId] == true }.map { it.tokenId }
                    if (ids.isNotEmpty()) onConfirm(ids)
                }
            ) { Text("Return INR $selectedAmount") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
