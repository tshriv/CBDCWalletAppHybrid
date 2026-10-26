package com.ts.cbdcwalletapp.data

import com.google.gson.Gson

enum class TransferStep {
    REQUEST, HANDOFF, ACK, SUCCESS
}

data class BaseTransferMessage(
    val type: TransferStep
)

// STEP 1: Receiver shows this
data class OfflineRequest(
    val type: TransferStep = TransferStep.REQUEST,
    val tx_id: String,
    val to: String,
    val availableDenominations: List<OfflineDenominationQuantity> = emptyList()
)

data class OfflineDenominationQuantity(
    val denomination: Int,
    val quantity: Int
)

data class OfflineTokenPayload(
    val tokenId: String,
    val serialNumber: String,
    val denomination: Int,
    val currency: String,
    val issuer: String
)

// STEP 2: Sender scans REQUEST, destroys/transfers local tokens, shows token handoff QR.
data class OfflineHandoff(
    val type: TransferStep = TransferStep.HANDOFF,
    val tx_id: String,
    val from: String,
    val to: String,
    val amount: Int,
    val handoffAmount: Int,
    val changeAmount: Int = 0,
    val tokens: List<OfflineTokenPayload>,
    val isExchange: Boolean = false
)

// STEP 3: Receiver stores the same token records and acknowledges.
data class OfflineAck(
    val type: TransferStep = TransferStep.ACK,
    val tx_id: String,
    val receivedAmount: Int,
    val receivedTokenIds: List<String>,
    val changeAmount: Int = 0,
    val changeTokens: List<OfflineTokenPayload> = emptyList(),
    val status: String = "RECEIVED"
)

// STEP 4: Sender scans ACK and shows final confirmation.
data class OfflineSuccess(
    val type: TransferStep = TransferStep.SUCCESS,
    val tx_id: String,
    val message: String = "SUCCESS"
)

object TransferProtocol {
    private val gson = Gson()

    fun toJson(data: Any): String = gson.toJson(data)

    fun parseMessage(json: String): BaseTransferMessage? = try {
        gson.fromJson(json, BaseTransferMessage::class.java)
    } catch (e: Exception) { null }

    fun parseRequest(json: String): OfflineRequest? = try { gson.fromJson(json, OfflineRequest::class.java) } catch (e: Exception) { null }
    fun parseHandoff(json: String): OfflineHandoff? = try { gson.fromJson(json, OfflineHandoff::class.java) } catch (e: Exception) { null }
    fun parseAck(json: String): OfflineAck? = try { gson.fromJson(json, OfflineAck::class.java) } catch (e: Exception) { null }
    fun parseSuccess(json: String): OfflineSuccess? = try { gson.fromJson(json, OfflineSuccess::class.java) } catch (e: Exception) { null }
}
