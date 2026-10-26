package com.ts.cbdcwalletapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalTokenDao {

    @Query("SELECT * FROM local_tokens WHERE ownerPhoneNumber = :owner AND status = :status ORDER BY denomination DESC, receivedAt ASC")
    fun observeByStatus(owner: String, status: String = LocalToken.STATUS_ACTIVE_OFFLINE): Flow<List<LocalToken>>

    @Query("SELECT * FROM local_tokens WHERE ownerPhoneNumber = :owner AND status = :status ORDER BY denomination DESC, receivedAt ASC")
    suspend fun listByStatus(owner: String, status: String = LocalToken.STATUS_ACTIVE_OFFLINE): List<LocalToken>

    @Query("SELECT * FROM local_tokens WHERE ownerPhoneNumber = :owner AND status IN (:statuses) ORDER BY denomination DESC, receivedAt ASC")
    suspend fun listByStatuses(owner: String, statuses: List<String>): List<LocalToken>

    @Query("SELECT COALESCE(SUM(denomination), 0) FROM local_tokens WHERE ownerPhoneNumber = :owner AND status = :status")
    suspend fun sumByStatus(owner: String, status: String = LocalToken.STATUS_ACTIVE_OFFLINE): Int

    @Query("SELECT COUNT(*) FROM local_tokens WHERE ownerPhoneNumber = :owner AND status = :status")
    suspend fun countByStatus(owner: String, status: String = LocalToken.STATUS_ACTIVE_OFFLINE): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tokens: List<LocalToken>)

    @Query("DELETE FROM local_tokens WHERE tokenId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE local_tokens SET status = :newStatus, txLogId = :txLogId WHERE tokenId IN (:ids)")
    suspend fun updateStatus(ids: List<String>, newStatus: String, txLogId: String? = null)

    @Query("DELETE FROM local_tokens WHERE ownerPhoneNumber = :owner")
    suspend fun clearForOwner(owner: String)
}
