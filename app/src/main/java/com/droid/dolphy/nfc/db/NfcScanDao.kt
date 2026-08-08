package com.droid.dolphy.nfc.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NfcScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: NfcScanEntity): Long

    @Query("SELECT * FROM nfc_scans ORDER BY scannedAtMillis DESC")
    fun observeAll(): Flow<List<NfcScanEntity>>

    @Query("SELECT * FROM nfc_scans WHERE id = :id")
    fun observeById(id: Long): Flow<NfcScanEntity?>

    @Query("SELECT * FROM nfc_scans WHERE id = :id")
    suspend fun getById(id: Long): NfcScanEntity?
}


