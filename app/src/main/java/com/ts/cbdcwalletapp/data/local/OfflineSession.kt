package com.ts.cbdcwalletapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_sessions")
data class OfflineSession(
    @PrimaryKey val txId: String,
    val ownerPhoneNumber: String,
    val isSender: Boolean,
    val currentStep: String,
    val peerId: String,
    val amount: Int,
    val qrContent: String?,
    val statusMessage: String,
    val sessionStatus: String,
    val updatedAt: Long
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_TERMINATED = "TERMINATED"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}
