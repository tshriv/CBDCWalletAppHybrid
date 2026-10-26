package com.ts.cbdcwalletapp.data

// ---------- Auth ----------

data class LoginRequest(
    val phoneNumber: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val phoneNumber: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class User(
    val id: String? = null,        // From login response
    val _id: String? = null,       // From populated MongoDB objects
    val name: String,
    val phoneNumber: String? = null
) {
    val userId: String
        get() = id ?: _id ?: ""
}

// ---------- Wallet & balance ----------

data class WalletResponse(
    val walletId: String,
    val balance: Int,
    val tokenCount: Int,
    val offlineBalance: Int = 0,
    val offlineTokenCount: Int = 0,
    val currency: String
)

// ---------- CBDC Tokens ----------

data class CbdcToken(
    val tokenId: String,
    val serialNumber: String,
    val denomination: Int,
    val currency: String,
    val issuer: String,
    val issuedAt: String? = null,
    val lastTransferredAt: String? = null
)

data class TokensResponse(
    val walletId: String,
    val tokens: List<CbdcToken>
)

data class MintedToken(
    val tokenId: String,
    val serialNumber: String,
    val denomination: Int
)

// ---------- Mint ----------

data class MintRequest(
    val amount: Int
)

data class MintResponse(
    val message: String,
    val transactionId: String,
    val mintedTokens: List<MintedToken>,
    val balance: Int,
    val tokenCount: Int,
    val offlineBalance: Int = 0,
    val offlineTokenCount: Int = 0,
    val currency: String
)

// ---------- Transfer ----------

data class TransferRequest(
    val toPhoneNumber: String,
    val tokenIds: List<String>
)

data class TransferResponse(
    val message: String,
    val transactionId: String,
    val transferredTokenIds: List<String>,
    val amount: Int,
    val balance: Int,
    val tokenCount: Int,
    val offlineBalance: Int = 0,
    val offlineTokenCount: Int = 0,
    val currency: String
)

// ---------- Offline checkout / withdraw ----------

data class OfflineCheckoutRequest(
    val amount: Int
)

data class OfflineCheckoutResponse(
    val message: String,
    val amount: Int,
    val tokens: List<CbdcToken>,
    val balance: Int,
    val tokenCount: Int,
    val offlineBalance: Int,
    val offlineTokenCount: Int,
    val currency: String
)

data class OfflineWithdrawRequest(
    val tokenIds: List<String>
)

data class OfflineWithdrawResponse(
    val message: String,
    val amount: Int,
    val tokens: List<MintedToken>,
    val balance: Int,
    val tokenCount: Int,
    val offlineBalance: Int,
    val offlineTokenCount: Int,
    val currency: String
)

data class OfflineSyncToken(
    val tokenId: String,
    val serialNumber: String,
    val denomination: Int,
    val currency: String,
    val issuer: String
)

data class OfflineSyncLog(
    val logId: String,
    val txId: String,
    val role: String,
    val step: String,
    val peerId: String,
    val amount: Int,
    val tokenIdsJson: String,
    val status: String,
    val createdAt: Long
)

data class OfflineSyncRequest(
    val activeOfflineTokens: List<OfflineSyncToken>,
    val transferredOutTokenIds: List<String>,
    val logs: List<OfflineSyncLog>
)

data class OfflineSyncResponse(
    val message: String,
    val claimedTokenIds: List<String>,
    val markedSpentTokenIds: List<String>,
    val rejectedTokenIds: List<String>,
    val transactionIds: List<String>,
    val balance: Int,
    val tokenCount: Int,
    val offlineBalance: Int,
    val offlineTokenCount: Int,
    val currency: String
)

// ---------- Transaction history ----------

data class TokenRef(
    val tokenId: String,
    val serialNumber: String,
    val denomination: Int
)

data class Transaction(
    val _id: String,
    val fromWallet: WalletInfo?,
    val toWallet: WalletInfo?,
    val amount: Int,
    val currency: String? = null,
    val type: String,    // MINT | TRANSFER | REDEEM
    val status: String,
    val tokens: List<TokenRef> = emptyList(),
    val createdAt: String
)

data class TransactionUser(
    val _id: String,
    val name: String,
    val phoneNumber: String? = null
)

data class WalletInfo(
    val _id: String,
    val user: TransactionUser?
)
