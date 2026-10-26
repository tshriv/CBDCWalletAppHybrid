package com.ts.cbdcwalletapp.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ts.cbdcwalletapp.security.UsbTokenState

@Composable
fun UsbAuthScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val tokenState by viewModel.usbTokenState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Prevent bypassing USB authentication with system hardware back press
    BackHandler(enabled = true) {
        (context as? Activity)?.moveTaskToBack(true)
    }

    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    val userPhone = (authState as? AuthState.Success)?.user?.phoneNumber ?: ""

    // Listen for device state changes or network status and resume to previous screen upon authentication or online connection
    LaunchedEffect(tokenState, isOnline) {
        if ((tokenState is UsbTokenState.Authenticated) || isOnline) {
            val resumeRoute = viewModel.getAndClearUsbAuthResumeRoute()
            if (resumeRoute != null && resumeRoute != Screen.Dashboard.route && resumeRoute != Screen.UsbAuth.route) {
                val popped = navController.popBackStack()
                if (!popped) {
                    navController.navigate(resumeRoute) {
                        popUpTo(Screen.UsbAuth.route) { inclusive = true }
                    }
                }
            } else {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                    popUpTo(Screen.UsbAuth.route) { inclusive = true }
                }
            }
        }
        if (tokenState is UsbTokenState.Disconnected) {
            pinError = null
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "USB Token Authentication",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (userPhone.isNotBlank()) {
                    Text(
                        text = "Account: $userPhone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // USB Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (tokenState) {
                            is UsbTokenState.Ready, is UsbTokenState.Authenticated -> MaterialTheme.colorScheme.surfaceVariant
                            is UsbTokenState.Disconnected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            is UsbTokenState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (val state = tokenState) {
                            is UsbTokenState.Disconnected -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "USB Disconnected",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "USB Token Not Detected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Please connect your TrusToken USB device / OTG Reader to continue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { viewModel.retryUsbDetection() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Scan",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan for Token")
                                }
                            }

                            is UsbTokenState.PermissionRequired -> {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Permission Required",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "USB Permission Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Allow access to the USB device to authenticate.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.requestUsbPermission() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Grant Permission")
                                }
                            }

                            is UsbTokenState.Initializing -> {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Initializing Token...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = state.deviceName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is UsbTokenState.Ready -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Token Ready",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${state.deviceName} Connected",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enter your Token PIN below to complete login",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is UsbTokenState.Authenticating -> {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Verifying PIN with Token...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            is UsbTokenState.Authenticated -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Authenticated",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Authenticated!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }

                            is UsbTokenState.Error -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Token Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Token Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (state.canRetry) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.retryUsbDetection() },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Input (enabled when token is ready or error with retry)
                val isTokenReady = tokenState is UsbTokenState.Ready ||
                        (tokenState is UsbTokenState.Error && (tokenState as UsbTokenState.Error).canRetry)

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        pinError = null
                    },
                    label = { Text("Token PIN") },
                    placeholder = { Text("Enter USB Token PIN") },
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (isTokenReady && pin.isNotEmpty() && !isVerifying) {
                                isVerifying = true
                                viewModel.loginWithUsbToken(pin) { success, msg ->
                                    isVerifying = false
                                    if (!success) {
                                        pinError = msg
                                    }
                                }
                            }
                        }
                    ),
                    trailingIcon = {
                        TextButton(onClick = { pinVisible = !pinVisible }) {
                            Text(
                                text = if (pinVisible) "HIDE" else "SHOW",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    isError = pinError != null,
                    supportingText = {
                        if (pinError != null) {
                            Text(
                                text = pinError!!,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isTokenReady && !isVerifying,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Authenticate Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (pin.isEmpty()) {
                            pinError = "Please enter your Token PIN"
                            return@Button
                        }
                        isVerifying = true
                        viewModel.loginWithUsbToken(pin) { success, msg ->
                            isVerifying = false
                            if (!success) {
                                pinError = msg
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isTokenReady && pin.isNotEmpty() && !isVerifying
                ) {
                    if (isVerifying || tokenState is UsbTokenState.Authenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Login",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Login with Token",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Switch account / Cancel option
                TextButton(
                    onClick = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                ) {
                    Text("Cancel / Switch Account")
                }
            }
        }
    }
}
