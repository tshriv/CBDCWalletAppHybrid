package com.ts.cbdcwalletapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_tokens")
data class LocalToken(
    @PrimaryKey val tokenId: String,
    val serialNumber: String,
    val denomination: Int,
    val currency: String,
    val issuer: String,
    val status: String,
    val ownerPhoneNumber: String,
    val receivedAt: Long,
    val txLogId: String? = null
) {
    companion object {
        const val STATUS_ACTIVE_OFFLINE = "ACTIVE_OFFLINE"
        const val STATUS_TRANSFERRED_OUT = "TRANSFERRED_OUT"
        const val STATUS_RECEIVED = "RECEIVED"
        const val STATUS_SYNCED = "SYNCED"
    }
}
