package com.ts.cbdcwalletapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocalToken::class, OfflineTxLog::class, OfflineSession::class],
    version = 4,
    exportSchema = false
)
abstract class WalletDatabase : RoomDatabase() {

    abstract fun localTokenDao(): LocalTokenDao
    abstract fun offlineTxLogDao(): OfflineTxLogDao
    abstract fun offlineSessionDao(): OfflineSessionDao

    companion object {
        @Volatile
        private var INSTANCE: WalletDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_tx_logs (
                        logId TEXT NOT NULL PRIMARY KEY,
                        txId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        step TEXT NOT NULL,
                        peerId TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        tokenIdsJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_sessions (
                        ownerPhoneNumber TEXT NOT NULL PRIMARY KEY,
                        txId TEXT NOT NULL,
                        isSender INTEGER NOT NULL,
                        currentStep TEXT NOT NULL,
                        peerId TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        qrContent TEXT,
                        statusMessage TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_sessions_new (
                        txId TEXT NOT NULL PRIMARY KEY,
                        ownerPhoneNumber TEXT NOT NULL,
                        isSender INTEGER NOT NULL,
                        currentStep TEXT NOT NULL,
                        peerId TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        qrContent TEXT,
                        statusMessage TEXT NOT NULL,
                        sessionStatus TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO offline_sessions_new (
                        txId, ownerPhoneNumber, isSender, currentStep, peerId, amount,
                        qrContent, statusMessage, sessionStatus, updatedAt
                    )
                    SELECT txId, ownerPhoneNumber, isSender, currentStep, peerId, amount,
                        qrContent, statusMessage, 'ACTIVE', updatedAt
                    FROM offline_sessions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE offline_sessions")
                db.execSQL("ALTER TABLE offline_sessions_new RENAME TO offline_sessions")
            }
        }

        fun get(context: Context): WalletDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "cbdc_wallet.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
