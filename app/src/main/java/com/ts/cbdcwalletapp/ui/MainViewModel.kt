package com.ts.cbdcwalletapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ts.cbdcwalletapp.data.*
import com.ts.cbdcwalletapp.data.local.LocalToken
import com.ts.cbdcwalletapp.data.local.LocalTokenDao
import com.ts.cbdcwalletapp.data.local.OfflineSession
import com.ts.cbdcwalletapp.data.local.OfflineSessionDao
import com.ts.cbdcwalletapp.data.local.OfflineTxLog
import com.ts.cbdcwalletapp.data.local.OfflineTxLogDao
import com.ts.cbdcwalletapp.network.NetworkMonitor
import com.ts.cbdcwalletapp.security.UsbTokenManager
import com.ts.cbdcwalletapp.security.UsbTokenState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: Repository,
    private val tokenManager: TokenManager,
    private val localTokenDao: LocalTokenDao,
    private val offlineTxLogDao: OfflineTxLogDao,
    private val offlineSessionDao: OfflineSessionDao,
    networkMonitor: NetworkMonitor,
    private val usbTokenManager: UsbTokenManager
) : ViewModel() {

    val usbTokenState: StateFlow<UsbTokenState> = usbTokenManager.tokenState

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _walletState = MutableStateFlow<WalletState>(WalletState.Idle)
    val walletState: StateFlow<WalletState> = _walletState

    private val _tokensState = MutableStateFlow<TokensState>(TokensState.Idle)
    val tokensState: StateFlow<TokensState> = _tokensState

    private val _transactionState = MutableStateFlow<TransactionState>(TransactionState.Idle)
    val transactionState: StateFlow<TransactionState> = _transactionState

    private val _offlineState = MutableStateFlow<OfflineOpState>(OfflineOpState.Idle)
    val offlineState: StateFlow<OfflineOpState> = _offlineState

    /** Network state — gates online operations in the UI. */
    val isOnline: StateFlow<Boolean> = networkMonitor.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, networkMonitor.isCurrentlyOnline())

    /** Current logged-in user's phone — drives which offline tokens to observe. */
    private val _userPhone = MutableStateFlow(tokenManager.getUser()?.phoneNumber)

    /**
     * Live mirror of this device's offline-held tokens, sourced from Room.
     * Emits whenever the table changes, so checkout/withdraw/transfer all just
     * write to Room and the UI follows automatically.
     */
    val localOfflineTokens: StateFlow<List<LocalToken>> = _userPhone
        .flatMapLatest { phone ->
            if (phone.isNullOrBlank()) flowOf(emptyList())
            else localTokenDao.observeByStatus(phone, LocalToken.STATUS_ACTIVE_OFFLINE)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Derived: offline balance from local tokens, seeded with cached value to avoid ₹0 flash. */
    val localOfflineBalance: StateFlow<Int> = localOfflineTokens
        .map { it.sumOf { t -> t.denomination } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, tokenManager.getCachedOfflineBalance())

    val localOfflineTokenCount: StateFlow<Int> = localOfflineTokens
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, tokenManager.getCachedOfflineCount())

    val pendingOfflineSessions: StateFlow<List<OfflineSession>> = _userPhone
        .flatMapLatest { phone ->
            if (phone.isNullOrBlank()) flowOf(emptyList())
            else offlineSessionDao.observeByStatuses(phone)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _resumeOfflineTxId = MutableStateFlow<String?>(null)

    init {
        // Persist the latest values so the next cold start opens without a flash.
        viewModelScope.launch {
            localOfflineTokens.collect { tokens ->
                tokenManager.saveOfflineCache(
                    balance = tokens.sumOf { it.denomination },
                    count = tokens.size
                )
            }
        }
        // When network reconnects, pull fresh server state.
        viewModelScope.launch {
            isOnline.drop(1).collect { online ->
                if (online && _authState.value is AuthState.Success) {
                    syncOfflineState(silent = true)
                    getBalance()
                    getTokens()
                    getTransactions()
                }
            }
        }
    }

    fun checkAuth() {
        val token = tokenManager.getToken()
        val user = tokenManager.getUser()
        if (token != null) {
            _authState.value = AuthState.Success(user)
            _userPhone.value = user?.phoneNumber
        }
    }

    fun register(request: RegisterRequest) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = repository.register(request)
                if (response.isSuccessful) {
                    _authState.value = AuthState.Registered
                } else {
                    _authState.value = AuthState.Error(response.message())
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun login(request: LoginRequest) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = repository.login(request)
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveToken(authResponse.token)
                    tokenManager.saveUser(authResponse.user)
                    _userPhone.value = authResponse.user.phoneNumber
                    _authState.value = AuthState.Success(authResponse.user)
                } else {
                    _authState.value = AuthState.Error(response.message())
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loginWithUsbToken(pin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = usbTokenManager.login(pin)
            result.onSuccess { msg ->
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "Authentication failed")
            }
        }
    }

    fun requestUsbPermission() {
        usbTokenManager.requestUsbPermission()
    }

    fun retryUsbDetection() {
        usbTokenManager.checkAndInitializeToken()
    }

    fun isUsbAuthenticated(): Boolean {
        return usbTokenManager.isAuthenticated()
    }

    private var usbAuthResumeRoute: String? = null

    fun setUsbAuthResumeRoute(route: String?) {
        usbAuthResumeRoute = route
    }

    fun getAndClearUsbAuthResumeRoute(): String? {
        val route = usbAuthResumeRoute
        usbAuthResumeRoute = null
        return route
    }

    fun logout() {
        usbAuthResumeRoute = null
        usbTokenManager.logout()
        tokenManager.clearToken()
        _authState.value = AuthState.Idle
        _walletState.value = WalletState.Idle
        _tokensState.value = TokensState.Idle
        _transactionState.value = TransactionState.Idle
        _offlineState.value = OfflineOpState.Idle
        _userPhone.value = null
    }

    fun getBalance() {
        if (!isOnline.value) return
        viewModelScope.launch {
            _walletState.value = WalletState.Loading
            try {
                val response = repository.getBalance()
                if (response.isSuccessful && response.body() != null) {
                    _walletState.value = WalletState.Success(response.body()!!)
                } else {
                    _walletState.value = WalletState.Error(response.message())
                }
            } catch (e: Exception) {
                _walletState.value = WalletState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun getTokens() {
        if (!isOnline.value) return
        viewModelScope.launch {
            _tokensState.value = TokensState.Loading
            try {
                val response = repository.getTokens()
                if (response.isSuccessful && response.body() != null) {
                    _tokensState.value = TokensState.Success(response.body()!!.tokens)
                } else {
                    _tokensState.value = TokensState.Error(response.message())
                }
            } catch (e: Exception) {
                _tokensState.value = TokensState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun mintFunds(amount: Int) {
        if (!isOnline.value) {
            _walletState.value = WalletState.Error("Adding funds requires a network connection")
            return
        }
        viewModelScope.launch {
            _walletState.value = WalletState.Loading
            try {
                val response = repository.mintFunds(MintRequest(amount))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _walletState.value = WalletState.Success(
                        WalletResponse(
                            walletId = "",
                            balance = body.balance,
                            tokenCount = body.tokenCount,
                            offlineBalance = body.offlineBalance,
                            offlineTokenCount = body.offlineTokenCount,
                            currency = body.currency
                        )
                    )
                    getTokens()
                    getTransactions()
                } else {
                    _walletState.value = WalletState.Error(response.message())
                }
            } catch (e: Exception) {
                _walletState.value = WalletState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun transferFunds(toPhoneNumber: String, tokenIds: List<String>) {
        if (!isOnline.value) {
            _walletState.value = WalletState.Error("Online transfer requires a network connection")
            return
        }
        viewModelScope.launch {
            _walletState.value = WalletState.Loading
            try {
                val response = repository.transferFunds(TransferRequest(toPhoneNumber, tokenIds))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _walletState.value = WalletState.Success(
                        WalletResponse(
                            walletId = "",
                            balance = body.balance,
                            tokenCount = body.tokenCount,
                            offlineBalance = body.offlineBalance,
                            offlineTokenCount = body.offlineTokenCount,
                            currency = body.currency
                        )
                    )
                    getTokens()
                    getTransactions()
                } else {
                    val errBody = response.errorBody()?.string()
                    _walletState.value = WalletState.Error(errBody ?: response.message())
                }
            } catch (e: Exception) {
                _walletState.value = WalletState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun getTransactions() {
        if (!isOnline.value) return
        viewModelScope.launch {
            _transactionState.value = TransactionState.Loading
            try {
                val response = repository.getTransactions()
                if (response.isSuccessful && response.body() != null) {
                    _transactionState.value = TransactionState.Success(response.body()!!)
                } else {
                    _transactionState.value = TransactionState.Error(response.message())
                }
            } catch (e: Exception) {
                _transactionState.value = TransactionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ---------- Offline checkout / withdraw ----------

    fun checkoutToOffline(amount: Int) {
        if (!isOnline.value) {
            _offlineState.value = OfflineOpState.Error("Move-offline requires a network connection")
            return
        }
        val phone = tokenManager.getUser()?.phoneNumber
        if (phone == null) {
            _offlineState.value = OfflineOpState.Error("Not logged in")
            return
        }
        viewModelScope.launch {
            _offlineState.value = OfflineOpState.Loading
            try {
                val response = repository.offlineCheckout(OfflineCheckoutRequest(amount))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val now = System.currentTimeMillis()
                    val rows = body.tokens.map { t ->
                        LocalToken(
                            tokenId = t.tokenId,
                            serialNumber = t.serialNumber,
                            denomination = t.denomination,
                            currency = t.currency,
                            issuer = t.issuer,
                            status = LocalToken.STATUS_ACTIVE_OFFLINE,
                            ownerPhoneNumber = phone,
                            receivedAt = now
                        )
                    }
                    localTokenDao.upsertAll(rows)
                    _walletState.value = WalletState.Success(
                        WalletResponse(
                            walletId = "",
                            balance = body.balance,
                            tokenCount = body.tokenCount,
                            offlineBalance = body.offlineBalance,
                            offlineTokenCount = body.offlineTokenCount,
                            currency = body.currency
                        )
                    )
                    getTokens()
                    _offlineState.value = OfflineOpState.Success(
                        "Moved ₹${body.amount} into offline storage"
                    )
                } else {
                    val msg = response.errorBody()?.string() ?: response.message()
                    _offlineState.value = OfflineOpState.Error(msg)
                }
            } catch (e: Exception) {
                _offlineState.value = OfflineOpState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun withdrawFromOffline(tokenIds: List<String>) {
        if (!isOnline.value) {
            _offlineState.value = OfflineOpState.Error("Bring-online requires a network connection")
            return
        }
        if (tokenIds.isEmpty()) {
            _offlineState.value = OfflineOpState.Error("Pick at least one token")
            return
        }
        viewModelScope.launch {
            _offlineState.value = OfflineOpState.Loading
            try {
                val response = repository.offlineWithdraw(OfflineWithdrawRequest(tokenIds))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    localTokenDao.deleteByIds(tokenIds)
                    _walletState.value = WalletState.Success(
                        WalletResponse(
                            walletId = "",
                            balance = body.balance,
                            tokenCount = body.tokenCount,
                            offlineBalance = body.offlineBalance,
                            offlineTokenCount = body.offlineTokenCount,
                            currency = body.currency
                        )
                    )
                    getTokens()
                    _offlineState.value = OfflineOpState.Success(
                        "Returned ₹${body.amount} to online"
                    )
                } else {
                    val msg = response.errorBody()?.string() ?: response.message()
                    _offlineState.value = OfflineOpState.Error(msg)
                }
            } catch (e: Exception) {
                _offlineState.value = OfflineOpState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearOfflineOpStatus() {
        _offlineState.value = OfflineOpState.Idle
    }

    fun syncOfflineState(silent: Boolean = false) {
        if (!isOnline.value) {
            if (!silent) _offlineState.value = OfflineOpState.Error("Offline sync requires a network connection")
            return
        }
        val phone = tokenManager.getUser()?.phoneNumber
        if (phone == null) {
            if (!silent) _offlineState.value = OfflineOpState.Error("Not logged in")
            return
        }

        viewModelScope.launch {
            if (!silent) _offlineState.value = OfflineOpState.Loading
            try {
                val active = localTokenDao.listByStatus(phone, LocalToken.STATUS_ACTIVE_OFFLINE)
                val transferredOut = localTokenDao.listByStatus(phone, LocalToken.STATUS_TRANSFERRED_OUT)
                val logs = offlineTxLogDao.listBySyncStatus()

                if (active.isEmpty() && transferredOut.isEmpty() && logs.isEmpty()) {
                    if (!silent) _offlineState.value = OfflineOpState.Success("Nothing to sync")
                    return@launch
                }

                val response = repository.offlineSync(
                    OfflineSyncRequest(
                        activeOfflineTokens = active.map {
                            OfflineSyncToken(
                                tokenId = it.tokenId,
                                serialNumber = it.serialNumber,
                                denomination = it.denomination,
                                currency = it.currency,
                                issuer = it.issuer
                            )
                        },
                        transferredOutTokenIds = transferredOut.map { it.tokenId },
                        logs = logs.map {
                            OfflineSyncLog(
                                logId = it.logId,
                                txId = it.txId,
                                role = it.role,
                                step = it.step,
                                peerId = it.peerId,
                                amount = it.amount,
                                tokenIdsJson = it.tokenIdsJson,
                                status = it.status,
                                createdAt = it.createdAt
                            )
                        }
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (logs.isNotEmpty()) offlineTxLogDao.updateSyncStatus(logs.map { it.logId })
                    _walletState.value = WalletState.Success(
                        WalletResponse(
                            walletId = "",
                            balance = body.balance,
                            tokenCount = body.tokenCount,
                            offlineBalance = body.offlineBalance,
                            offlineTokenCount = body.offlineTokenCount,
                            currency = body.currency
                        )
                    )
                    if (!silent) {
                        val suffix = if (body.rejectedTokenIds.isEmpty()) "" else " (${body.rejectedTokenIds.size} rejected)"
                        _offlineState.value = OfflineOpState.Success("Offline activity synced$suffix")
                    }
                } else if (!silent) {
                    _offlineState.value = OfflineOpState.Error(response.errorBody()?.string() ?: response.message())
                }
            } catch (e: Exception) {
                if (!silent) _offlineState.value = OfflineOpState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ---------- Token-aware offline transfer (Chunk 2: direct transfer only) ----------

    data class OfflineTransferState(
        val isSender: Boolean = false,
        val transferAmount: Int = 0
    )

    private val _offlineTransferState = MutableStateFlow(OfflineTransferState())
    val offlineTransferState: StateFlow<OfflineTransferState> = _offlineTransferState

    fun startOfflineTransfer(isSender: Boolean, amount: Int = 0) {
        _resumeOfflineTxId.value = null
        _offlineTransferState.value = OfflineTransferState(isSender, amount)
    }

    fun resumeOfflineTransfer(txId: String) {
        _resumeOfflineTxId.value = txId
    }

    fun loadOfflineSession(onResult: (OfflineSession?) -> Unit) {
        val phone = tokenManager.getUser()?.phoneNumber ?: return onResult(null)
        val txId = _resumeOfflineTxId.value ?: return onResult(null)
        viewModelScope.launch {
            onResult(offlineSessionDao.get(phone, txId))
        }
    }

    fun saveOfflineSession(
        txId: String,
        isSender: Boolean,
        currentStep: TransferStep,
        peerId: String,
        amount: Int,
        qrContent: String?,
        statusMessage: String
    ) {
        val phone = tokenManager.getUser()?.phoneNumber ?: return
        if (txId.isBlank()) return
        viewModelScope.launch {
            offlineSessionDao.upsert(
                OfflineSession(
                    txId = txId,
                    ownerPhoneNumber = phone,
                    isSender = isSender,
                    currentStep = currentStep.name,
                    peerId = peerId,
                    amount = amount,
                    qrContent = qrContent,
                    statusMessage = statusMessage,
                    sessionStatus = OfflineSession.STATUS_ACTIVE,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun terminateOfflineSession(txId: String) {
        val phone = tokenManager.getUser()?.phoneNumber ?: return
        if (txId.isBlank()) return
        viewModelScope.launch {
            offlineSessionDao.updateStatus(phone, txId, OfflineSession.STATUS_TERMINATED)
        }
    }

    fun completeOfflineSession(txId: String) {
        val phone = tokenManager.getUser()?.phoneNumber ?: return
        if (txId.isBlank()) return
        viewModelScope.launch {
            offlineSessionDao.updateStatus(phone, txId, OfflineSession.STATUS_COMPLETED)
            if (_resumeOfflineTxId.value == txId) _resumeOfflineTxId.value = null
        }
    }

    fun logOfflineRequestShown(txId: String, receiverId: String, amount: Int = 0) {
        viewModelScope.launch {
            offlineTxLogDao.upsert(
                OfflineTxLog(
                    logId = UUID.randomUUID().toString(),
                    txId = txId,
                    role = OfflineTxLog.ROLE_RECEIVER,
                    step = OfflineTxLog.STEP_REQUEST_SHOWN,
                    peerId = receiverId,
                    amount = amount,
                    tokenIdsJson = "[]",
                    status = OfflineTxLog.STATUS_IN_PROGRESS,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun prepareOfflineHandoff(
        txId: String,
        receiverId: String,
        amount: Int,
        receiverAvailableDenominations: List<OfflineDenominationQuantity>,
        onResult: (Result<OfflineHandoff>) -> Unit
    ) {
        val senderId = tokenManager.getUser()?.phoneNumber ?: tokenManager.getUser()?.userId ?: "sender"
        viewModelScope.launch {
            val receiverCounts = receiverAvailableDenominations
                .associate { it.denomination to it.quantity }
                .filterValues { it > 0 }
            val plan = selectOfflineTransferPlan(localOfflineTokens.value, amount, receiverCounts)
            if (plan == null) {
                onResult(Result.failure(IllegalStateException("Transaction not possible. Receiver cannot provide exact change for your available offline tokens.")))
                return@launch
            }

            val chosen = plan.tokens
            val tokenIds = chosen.map { it.tokenId }
            offlineTxLogDao.upsert(
                OfflineTxLog(
                    logId = UUID.randomUUID().toString(),
                    txId = txId,
                    role = OfflineTxLog.ROLE_SENDER,
                    step = OfflineTxLog.STEP_TOKENS_TRANSFERRED_OUT,
                    peerId = receiverId,
                    amount = plan.handoffAmount,
                    tokenIdsJson = Gson().toJson(tokenIds),
                    status = OfflineTxLog.STATUS_IN_PROGRESS,
                    createdAt = System.currentTimeMillis()
                )
            )
            localTokenDao.updateStatus(
                ids = tokenIds,
                newStatus = LocalToken.STATUS_TRANSFERRED_OUT,
                txLogId = txId
            )

            onResult(
                Result.success(
                    OfflineHandoff(
                        tx_id = txId,
                        from = senderId,
                        to = receiverId,
                        amount = amount,
                        handoffAmount = plan.handoffAmount,
                        changeAmount = plan.changeAmount,
                        tokens = chosen.map {
                            OfflineTokenPayload(
                                tokenId = it.tokenId,
                                serialNumber = it.serialNumber,
                                denomination = it.denomination,
                                currency = it.currency,
                                issuer = it.issuer
                            )
                        },
                        isExchange = plan.changeAmount > 0
                    )
                )
            )
        }
    }

    fun acceptOfflineHandoff(
        handoff: OfflineHandoff,
        onResult: (Result<OfflineAck>) -> Unit
    ) {
        val receiverId = tokenManager.getUser()?.phoneNumber ?: tokenManager.getUser()?.userId ?: "receiver"
        if (handoff.to != receiverId) {
            onResult(Result.failure(IllegalStateException("This QR is addressed to ${handoff.to}, not this user.")))
            return
        }

        viewModelScope.launch {
            val total = handoff.tokens.sumOf { it.denomination }
            if (total != handoff.handoffAmount) {
                onResult(Result.failure(IllegalStateException("Token total ₹$total does not match ₹${handoff.amount}.")))
                return@launch
            }

            if (handoff.handoffAmount - handoff.amount != handoff.changeAmount) {
                onResult(Result.failure(IllegalStateException("Invalid exchange amounts in QR.")))
                return@launch
            }

            val changeTokens = if (handoff.changeAmount > 0) {
                selectExactTokens(localOfflineTokens.value, handoff.changeAmount)
                    ?: run {
                        onResult(Result.failure(IllegalStateException("You no longer have exact change of INR ${handoff.changeAmount}. Ask sender to rescan your latest request QR.")))
                        return@launch
                    }
            } else {
                emptyList()
            }

            val now = System.currentTimeMillis()
            localTokenDao.upsertAll(
                handoff.tokens.map { token ->
                    LocalToken(
                        tokenId = token.tokenId,
                        serialNumber = token.serialNumber,
                        denomination = token.denomination,
                        currency = token.currency,
                        issuer = token.issuer,
                        status = LocalToken.STATUS_ACTIVE_OFFLINE,
                        ownerPhoneNumber = receiverId,
                        receivedAt = now,
                        txLogId = handoff.tx_id
                    )
                }
            )
            if (changeTokens.isNotEmpty()) {
                localTokenDao.updateStatus(
                    ids = changeTokens.map { it.tokenId },
                    newStatus = LocalToken.STATUS_TRANSFERRED_OUT,
                    txLogId = handoff.tx_id
                )
            }

            val tokenIds = handoff.tokens.map { it.tokenId }
            offlineTxLogDao.upsert(
                OfflineTxLog(
                    logId = UUID.randomUUID().toString(),
                    txId = handoff.tx_id,
                    role = OfflineTxLog.ROLE_RECEIVER,
                    step = OfflineTxLog.STEP_TOKENS_RECEIVED,
                    peerId = handoff.from,
                    amount = handoff.handoffAmount,
                    tokenIdsJson = Gson().toJson(tokenIds),
                    status = if (handoff.changeAmount > 0) OfflineTxLog.STATUS_IN_PROGRESS else OfflineTxLog.STATUS_COMPLETED,
                    createdAt = now
                )
            )
            if (changeTokens.isNotEmpty()) {
                offlineTxLogDao.upsert(
                    OfflineTxLog(
                        logId = UUID.randomUUID().toString(),
                        txId = handoff.tx_id,
                        role = OfflineTxLog.ROLE_RECEIVER,
                        step = OfflineTxLog.STEP_CHANGE_TRANSFERRED_OUT,
                        peerId = handoff.from,
                        amount = handoff.changeAmount,
                        tokenIdsJson = Gson().toJson(changeTokens.map { it.tokenId }),
                        status = OfflineTxLog.STATUS_COMPLETED,
                        createdAt = now
                    )
                )
            }

            onResult(
                Result.success(
                    OfflineAck(
                        tx_id = handoff.tx_id,
                        receivedAmount = handoff.amount,
                        receivedTokenIds = tokenIds,
                        changeAmount = handoff.changeAmount,
                        changeTokens = changeTokens.map {
                            OfflineTokenPayload(
                                tokenId = it.tokenId,
                                serialNumber = it.serialNumber,
                                denomination = it.denomination,
                                currency = it.currency,
                                issuer = it.issuer
                            )
                        }
                    )
                )
            )
        }
    }

    fun completeOfflineSenderAck(ack: OfflineAck, peerId: String, amount: Int) {
        viewModelScope.launch {
            if (ack.changeTokens.isNotEmpty()) {
                val senderId = tokenManager.getUser()?.phoneNumber ?: tokenManager.getUser()?.userId ?: "sender"
                val now = System.currentTimeMillis()
                localTokenDao.upsertAll(
                    ack.changeTokens.map { token ->
                        LocalToken(
                            tokenId = token.tokenId,
                            serialNumber = token.serialNumber,
                            denomination = token.denomination,
                            currency = token.currency,
                            issuer = token.issuer,
                            status = LocalToken.STATUS_ACTIVE_OFFLINE,
                            ownerPhoneNumber = senderId,
                            receivedAt = now,
                            txLogId = ack.tx_id
                        )
                    }
                )
                offlineTxLogDao.upsert(
                    OfflineTxLog(
                        logId = UUID.randomUUID().toString(),
                        txId = ack.tx_id,
                        role = OfflineTxLog.ROLE_SENDER,
                        step = OfflineTxLog.STEP_CHANGE_RECEIVED,
                        peerId = peerId,
                        amount = ack.changeAmount,
                        tokenIdsJson = Gson().toJson(ack.changeTokens.map { it.tokenId }),
                        status = OfflineTxLog.STATUS_COMPLETED,
                        createdAt = now
                    )
                )
            }
            offlineTxLogDao.upsert(
                OfflineTxLog(
                    logId = UUID.randomUUID().toString(),
                    txId = ack.tx_id,
                    role = OfflineTxLog.ROLE_SENDER,
                    step = OfflineTxLog.STEP_ACK_RECEIVED,
                    peerId = peerId,
                    amount = amount,
                    tokenIdsJson = Gson().toJson(ack.receivedTokenIds),
                    status = OfflineTxLog.STATUS_COMPLETED,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun selectExactTokens(tokens: List<LocalToken>, amount: Int): List<LocalToken>? {
        if (amount <= 0) return null
        val reachable = mutableMapOf(0 to emptyList<LocalToken>())
        tokens.sortedWith(compareByDescending<LocalToken> { it.denomination }.thenBy { it.receivedAt })
            .forEach { token ->
                val snapshot = reachable.toMap()
                snapshot.forEach { (sum, chosen) ->
                    val next = sum + token.denomination
                    if (next <= amount && !reachable.containsKey(next)) {
                        reachable[next] = chosen + token
                    }
                }
        }
        return reachable[amount]
    }

    private data class OfflineTransferPlan(
        val tokens: List<LocalToken>,
        val handoffAmount: Int,
        val changeAmount: Int
    )

    private fun selectOfflineTransferPlan(
        senderTokens: List<LocalToken>,
        amount: Int,
        receiverCounts: Map<Int, Int>
    ): OfflineTransferPlan? {
        val exact = selectExactTokens(senderTokens, amount)
        if (exact != null) return OfflineTransferPlan(exact, amount, 0)

        val reachable = mutableMapOf(0 to emptyList<LocalToken>())
        senderTokens
            .sortedWith(compareByDescending<LocalToken> { it.denomination }.thenBy { it.receivedAt })
            .forEach { token ->
                val snapshot = reachable.toMap()
                snapshot.forEach { (sum, chosen) ->
                    val next = sum + token.denomination
                    if (!reachable.containsKey(next)) {
                        reachable[next] = chosen + token
                    }
                }
            }

        return reachable
            .filterKeys { it > amount }
            .toSortedMap()
            .entries
            .firstOrNull { (handoffAmount, _) ->
                canMakeAmountWithDenominationCounts(receiverCounts, handoffAmount - amount)
            }
            ?.let { (handoffAmount, tokens) ->
                OfflineTransferPlan(tokens, handoffAmount, handoffAmount - amount)
            }
    }

    private fun canMakeAmountWithDenominationCounts(
        counts: Map<Int, Int>,
        amount: Int
    ): Boolean {
        if (amount == 0) return true
        if (amount < 0) return false
        val reachable = mutableSetOf(0)
        counts.entries
            .sortedByDescending { it.key }
            .forEach { (denomination, quantity) ->
                repeat(quantity.coerceAtLeast(0)) {
                    val snapshot = reachable.toList()
                    snapshot.forEach { sum ->
                        val next = sum + denomination
                        if (next <= amount) reachable.add(next)
                    }
                }
            }
        return amount in reachable
    }

    fun deductBalance(amount: Int) {
        // Legacy no-op retained until old callers are removed.
    }

    fun addBalance(amount: Int) {
        // Legacy no-op retained until old callers are removed.
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Registered : AuthState()
    data class Success(val user: User?) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class WalletState {
    object Idle : WalletState()
    object Loading : WalletState()
    data class Success(val wallet: WalletResponse) : WalletState()
    data class Error(val message: String) : WalletState()
}

sealed class TokensState {
    object Idle : TokensState()
    object Loading : TokensState()
    data class Success(val tokens: List<CbdcToken>) : TokensState()
    data class Error(val message: String) : TokensState()
}

sealed class TransactionState {
    object Idle : TransactionState()
    object Loading : TransactionState()
    data class Success(val transactions: List<Transaction>) : TransactionState()
    data class Error(val message: String) : TransactionState()
}

sealed class OfflineOpState {
    object Idle : OfflineOpState()
    object Loading : OfflineOpState()
    data class Success(val message: String) : OfflineOpState()
    data class Error(val message: String) : OfflineOpState()
}

class MainViewModelFactory(
    private val repository: Repository,
    private val tokenManager: TokenManager,
    private val localTokenDao: LocalTokenDao,
    private val offlineTxLogDao: OfflineTxLogDao,
    private val offlineSessionDao: OfflineSessionDao,
    private val networkMonitor: NetworkMonitor,
    private val usbTokenManager: UsbTokenManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                repository,
                tokenManager,
                localTokenDao,
                offlineTxLogDao,
                offlineSessionDao,
                networkMonitor,
                usbTokenManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
