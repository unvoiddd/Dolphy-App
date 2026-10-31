package com.droid.dolphy.bridge.flipper

import java.io.Closeable

/**
 * Byte/line transport to a Flipper. Implementations: USB CDC-ACM (P1),
 * BLE serial (P2).
 */
interface FlipperTransport : Closeable {
    val isOpen: Boolean

    fun open()

    fun writeLine(line: String)

    /**
     * Reads one line, or returns null on timeout.
     */
    fun readLine(timeoutMillis: Long): String?
}
