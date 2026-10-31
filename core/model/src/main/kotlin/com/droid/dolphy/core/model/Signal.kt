package com.droid.dolphy.core.model

enum class SignalKind {
    IR,
    NFC,
    RFID125,
    SUBGHZ,
    IBUTTON,
    BLE,
    RAW,
}

enum class SignalSource {
    PHONE,
    FLIPPER,
    USB,
    SDR,
    FILE,
    MANUAL,
}

/**
 * Transport-neutral representation of anything that can be captured, stored,
 * edited, replayed or shared. Backends convert to/from their native format.
 */
class Signal(
    val id: String,
    val kind: SignalKind,
    val frequencyHz: Long? = null,
    val modulation: String? = null,
    val payload: ByteArray = ByteArray(0),
    val rawTimingsUs: IntArray? = null,
    val metadata: Map<String, String> = emptyMap(),
    val source: SignalSource = SignalSource.MANUAL,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Signal) return false
        return id == other.id &&
            kind == other.kind &&
            frequencyHz == other.frequencyHz &&
            modulation == other.modulation &&
            payload.contentEquals(other.payload) &&
            (rawTimingsUs?.contentEquals(other.rawTimingsUs) ?: (other.rawTimingsUs == null)) &&
            metadata == other.metadata &&
            source == other.source &&
            capturedAtEpochMs == other.capturedAtEpochMs
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + (frequencyHz?.hashCode() ?: 0)
        result = 31 * result + (modulation?.hashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (rawTimingsUs?.contentHashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + capturedAtEpochMs.hashCode()
        return result
    }

    override fun toString(): String =
        "Signal(id=$id, kind=$kind, frequencyHz=$frequencyHz, bytes=${payload.size}, source=$source)"
}

/**
 * An explicit, time-boxed engagement. Offensive features must require one.
 */
data class LabSession(
    val id: String,
    val title: String,
    val startedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val authorizedByUser: Boolean,
    val allowedTargets: Set<String> = emptySet(),
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        authorizedByUser && now in startedAtEpochMs until expiresAtEpochMs
}
