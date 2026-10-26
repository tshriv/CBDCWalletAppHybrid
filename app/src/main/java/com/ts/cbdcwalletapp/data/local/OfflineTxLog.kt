package com.ts.cbdcwalletapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_tx_logs")
data class OfflineTxLog(
    @PrimaryKey val logId: String,
    val txId: String,
    val role: String,
    val step: String,
    val peerId: String,
    val amount: Int,
    val tokenIdsJson: String,
    val status: String,
    val createdAt: Long,
    val syncStatus: String = SYNC_PENDING
) {
    companion object {
        const val ROLE_SENDER = "SENDER"
        const val ROLE_RECEIVER = "RECEIVER"

        const val STEP_REQUEST_SHOWN = "REQUEST_SHOWN"
        const val STEP_TOKENS_TRANSFERRED_OUT = "TOKENS_TRANSFERRED_OUT"
        const val STEP_TOKENS_RECEIVED = "TOKENS_RECEIVED"
        const val STEP_CHANGE_TRANSFERRED_OUT = "CHANGE_TRANSFERRED_OUT"
        const val STEP_CHANGE_RECEIVED = "CHANGE_RECEIVED"
        const val STEP_ACK_RECEIVED = "ACK_RECEIVED"

        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"

        const val SYNC_PENDING = "PENDING"
        const val SYNCED = "SYNCED"
    }
}
