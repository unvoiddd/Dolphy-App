package com.droid.dolphy.nfc.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NfcScanEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NfcDatabase : RoomDatabase() {
    abstract fun scans(): NfcScanDao

    companion object {
        @Volatile
        private var INSTANCE: NfcDatabase? = null

        fun get(context: Context): NfcDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NfcDatabase::class.java,
                    "nfc_scans.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}


