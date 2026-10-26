package com.ts.cbdcwalletapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineSessionDao {

    @Query("SELECT * FROM offline_sessions WHERE ownerPhoneNumber = :owner AND sessionStatus IN (:statuses) ORDER BY updatedAt DESC")
    fun observeByStatuses(
        owner: String,
        statuses: List<String> = listOf(OfflineSession.STATUS_ACTIVE, OfflineSession.STATUS_TERMINATED)
    ): Flow<List<OfflineSession>>

    @Query("SELECT * FROM offline_sessions WHERE ownerPhoneNumber = :owner AND txId = :txId LIMIT 1")
    suspend fun get(owner: String, txId: String): OfflineSession?

    @Query("SELECT * FROM offline_sessions WHERE ownerPhoneNumber = :owner AND sessionStatus IN (:statuses) ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestByStatuses(
        owner: String,
        statuses: List<String> = listOf(OfflineSession.STATUS_ACTIVE, OfflineSession.STATUS_TERMINATED)
    ): OfflineSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: OfflineSession)

    @Query("UPDATE offline_sessions SET sessionStatus = :status, updatedAt = :updatedAt WHERE ownerPhoneNumber = :owner AND txId = :txId")
    suspend fun updateStatus(owner: String, txId: String, status: String, updatedAt: Long = System.currentTimeMillis())
}
