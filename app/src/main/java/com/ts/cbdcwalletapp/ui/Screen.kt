package com.ts.cbdcwalletapp.ui

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object UsbAuth : Screen("usb_auth")
    object Signup : Screen("signup")
    object Dashboard : Screen("dashboard")
    object WalletDetail : Screen("wallet_detail")
    object OfflineWallet : Screen("offline_wallet")
    object OfflineTransfer : Screen("offline_transfer")
}
