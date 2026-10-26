package com.ts.cbdcwalletapp.data

import com.ts.cbdcwalletapp.network.ApiService
import retrofit2.Response

class Repository(private val apiService: ApiService) {
    suspend fun register(request: RegisterRequest): Response<Void> = apiService.register(request)
    suspend fun login(request: LoginRequest): Response<AuthResponse> = apiService.login(request)
    suspend fun getBalance(): Response<WalletResponse> = apiService.getBalance()
    suspend fun getTokens(): Response<TokensResponse> = apiService.getTokens()
    suspend fun mintFunds(request: MintRequest): Response<MintResponse> = apiService.mintFunds(request)
    suspend fun transferFunds(request: TransferRequest): Response<TransferResponse> = apiService.transferFunds(request)
    suspend fun getTransactions(): Response<List<Transaction>> = apiService.getTransactions()
    suspend fun offlineCheckout(request: OfflineCheckoutRequest): Response<OfflineCheckoutResponse> = apiService.offlineCheckout(request)
    suspend fun offlineWithdraw(request: OfflineWithdrawRequest): Response<OfflineWithdrawResponse> = apiService.offlineWithdraw(request)
    suspend fun getOfflineTokens(): Response<TokensResponse> = apiService.getOfflineTokens()
    suspend fun offlineSync(request: OfflineSyncRequest): Response<OfflineSyncResponse> = apiService.offlineSync(request)
}
