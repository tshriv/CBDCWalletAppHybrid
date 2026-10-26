package com.ts.cbdcwalletapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ts.cbdcwalletapp.data.CbdcToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailScreen(navController: NavController, viewModel: MainViewModel) {
    val walletState by viewModel.walletState.collectAsState()
    val tokensState by viewModel.tokensState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.getBalance()
        viewModel.getTokens()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet Holdings", fontWeight = FontWeight.Bold) },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Summary card
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
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        when (val s = walletState) {
                            is WalletState.Success -> {
                                val symbol = if (s.wallet.currency == "INR") "₹" else s.wallet.currency
                                Text(
                                    text = "Total Balance",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "$symbol ${s.wallet.balance}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${s.wallet.tokenCount} CBDC tokens · ${s.wallet.currency}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            is WalletState.Loading -> CircularProgressIndicator(color = Color.White)
                            else -> Text("—", color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Tokens by Denomination",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (val ts = tokensState) {
                is TokensState.Success -> {
                    if (ts.tokens.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No tokens. Add funds from the dashboard.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        val grouped = ts.tokens
                            .groupBy { it.denomination }
                            .toSortedMap(compareByDescending { it })
                        val expanded = remember { mutableStateMapOf<Int, Boolean>() }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(grouped.keys.toList()) { denom ->
                                val tokens = grouped[denom].orEmpty()
                                DenominationGroupCard(
                                    denomination = denom,
                                    tokens = tokens,
                                    isExpanded = expanded[denom] == true,
                                    onToggle = { expanded[denom] = !(expanded[denom] ?: false) }
                                )
                            }
                        }
                    }
                }
                is TokensState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                is TokensState.Error -> Text(
                    text = "Error: ${ts.message}",
                    color = MaterialTheme.colorScheme.error
                )
                else -> {}
            }
        }
    }
}

@Composable
private fun DenominationGroupCard(
    denomination: Int,
    tokens: List<CbdcToken>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val total = denomination * tokens.size
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "₹ $denomination",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${tokens.size} tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = "= ₹ $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                tokens.forEach { tok ->
                    Text(
                        text = tok.serialNumber,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
