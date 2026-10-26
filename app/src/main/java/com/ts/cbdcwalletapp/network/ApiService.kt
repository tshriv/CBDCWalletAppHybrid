package com.ts.cbdcwalletapp.network

import com.ts.cbdcwalletapp.data.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Void>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/wallet/balance")
    suspend fun getBalance(): Response<WalletResponse>

    @GET("api/wallet/tokens")
    suspend fun getTokens(): Response<TokensResponse>

    @POST("api/wallet/mint")
    suspend fun mintFunds(@Body request: MintRequest): Response<MintResponse>

    @POST("api/wallet/transfer")
    suspend fun transferFunds(@Body request: TransferRequest): Response<TransferResponse>

    @GET("api/wallet/transactions")
    suspend fun getTransactions(): Response<List<Transaction>>

    @POST("api/wallet/offline/checkout")
    suspend fun offlineCheckout(@Body request: OfflineCheckoutRequest): Response<OfflineCheckoutResponse>

    @POST("api/wallet/offline/withdraw")
    suspend fun offlineWithdraw(@Body request: OfflineWithdrawRequest): Response<OfflineWithdrawResponse>

    @GET("api/wallet/offline/tokens")
    suspend fun getOfflineTokens(): Response<TokensResponse>

    @POST("api/wallet/offline/sync")
    suspend fun offlineSync(@Body request: OfflineSyncRequest): Response<OfflineSyncResponse>
}
