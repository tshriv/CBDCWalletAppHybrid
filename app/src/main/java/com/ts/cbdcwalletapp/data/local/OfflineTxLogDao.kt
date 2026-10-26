package com.ts.cbdcwalletapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTxLogDao {

    @Query("SELECT * FROM offline_tx_logs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OfflineTxLog>>

    @Query("SELECT * FROM offline_tx_logs WHERE syncStatus = :syncStatus ORDER BY createdAt ASC")
    suspend fun listBySyncStatus(syncStatus: String = OfflineTxLog.SYNC_PENDING): List<OfflineTxLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: OfflineTxLog)

    @Query("SELECT * FROM offline_tx_logs WHERE txId = :txId ORDER BY createdAt ASC")
    suspend fun listByTxId(txId: String): List<OfflineTxLog>

    @Query("UPDATE offline_tx_logs SET syncStatus = :syncStatus WHERE logId IN (:logIds)")
    suspend fun updateSyncStatus(logIds: List<String>, syncStatus: String = OfflineTxLog.SYNCED)
}
