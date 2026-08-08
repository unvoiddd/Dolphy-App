package com.droid.dolphy.nfc.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nfc_scans")
data class NfcScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scannedAtMillis: Long,
    val uidHex: String,
    val type: String,
    val techListCsv: String,
    val writable: Boolean?,
    val maxSizeBytes: Int?,
    val ndefRecordType: String?,
    val ndefContent: String?,
    val ndefHex: String?,
    val ntagType: String?,
    val isReadOnly: Boolean?,
    val passwordProtectionSupported: Boolean?,
    val passwordProtectionEnabled: Boolean?,
    val analysisVerdict: String,
    val emvDetected: Boolean,
    val emvInfo: String?,
)


