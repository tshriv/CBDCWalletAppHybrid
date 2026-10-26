package com.ts.cbdcwalletapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ts.cbdcwalletapp.data.Repository
import com.ts.cbdcwalletapp.data.TokenManager
import com.ts.cbdcwalletapp.data.local.WalletDatabase
import com.ts.cbdcwalletapp.network.NetworkMonitor
import com.ts.cbdcwalletapp.network.RetrofitClient
import androidx.compose.runtime.*
import com.ts.cbdcwalletapp.security.UsbTokenManager
import com.ts.cbdcwalletapp.security.UsbTokenState
import com.ts.cbdcwalletapp.ui.*
import com.ts.cbdcwalletapp.ui.theme.CBDCWalletAppTheme

class MainActivity : ComponentActivity() {
    private lateinit var usbTokenManager: UsbTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbTokenManager = UsbTokenManager(applicationContext)
        usbTokenManager.register()

        setContent {
            CBDCWalletAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val tokenManager = remember { TokenManager(applicationContext) }
                    val apiService = remember { RetrofitClient.getApiService(applicationContext) }
                    val repository = remember(apiService) { Repository(apiService) }
                    val db = remember { WalletDatabase.get(applicationContext) }
                    val networkMonitor = remember { NetworkMonitor(applicationContext) }
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(
                            repository,
                            tokenManager,
                            db.localTokenDao(),
                            db.offlineTxLogDao(),
                            db.offlineSessionDao(),
                            networkMonitor,
                            usbTokenManager
                        )
                    )

                    val isOnline by viewModel.isOnline.collectAsState()
                    val tokenState by viewModel.usbTokenState.collectAsState()
                    val authState by viewModel.authState.collectAsState()

                    // Check if user is already logged in
                    viewModel.checkAuth()
                    val startDestination = remember {
                        when {
                            tokenManager.getToken() == null -> Screen.Login.route
                            isOnline || usbTokenManager.isAuthenticated() -> Screen.Dashboard.route
                            else -> Screen.UsbAuth.route
                        }
                    }

                    // Enforce USB Token presence when offline and user is logged in
                    LaunchedEffect(tokenState, authState, isOnline) {
                        if (tokenManager.getToken() != null) {
                            if (!isOnline && (tokenState !is UsbTokenState.Authenticated)) {
                                val currentRoute = navController.currentBackStackEntry?.destination?.route
                                if (currentRoute != null &&
                                    currentRoute != Screen.Login.route &&
                                    currentRoute != Screen.Signup.route &&
                                    currentRoute != Screen.UsbAuth.route
                                ) {
                                    viewModel.setUsbAuthResumeRoute(currentRoute)
                                    navController.navigate(Screen.UsbAuth.route) {
                                        launchSingleTop = true
                                    }
                                }
                            } else if (isOnline) {
                                val currentRoute = navController.currentBackStackEntry?.destination?.route
                                if (currentRoute == Screen.UsbAuth.route) {
                                    val resumeRoute = viewModel.getAndClearUsbAuthResumeRoute()
                                    val destination = if (resumeRoute != null &&
                                        resumeRoute != Screen.Dashboard.route &&
                                        resumeRoute != Screen.UsbAuth.route
                                    ) {
                                        resumeRoute
                                    } else {
                                        Screen.Dashboard.route
                                    }



























                                    navController.navigate(destination) {
                                        popUpTo(Screen.UsbAuth.route) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable(Screen.Login.route) {
                            LoginScreen(navController, viewModel)
                        }
                        composable(Screen.UsbAuth.route) {
                            UsbAuthScreen(navController, viewModel)
                        }
                        composable(Screen.Signup.route) {
                            SignupScreen(navController, viewModel)
                        }
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(navController, viewModel)
                        }
                        composable(Screen.WalletDetail.route) {
                            WalletDetailScreen(navController, viewModel)
                        }
                        composable(Screen.OfflineWallet.route) {
                            OfflineWalletScreen(navController, viewModel)
                        }
                        composable(Screen.OfflineTransfer.route) {
                            OfflineTransferScreen(navController, viewModel, tokenManager.getUser())
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::usbTokenManager.isInitialized) {
            usbTokenManager.checkAndInitializeToken()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::usbTokenManager.isInitialized) {
            usbTokenManager.unregister()
        }
    }
}
