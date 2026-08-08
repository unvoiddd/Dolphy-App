package com.droid.dolphy.ble.smarthome







object ElkBledomProtocol {
    fun powerOn(): ByteArray = byteArrayOf(
        0x7e, 0x00, 0x04, 0xF0.toByte(), 0x00, 0x01, 0xFF.toByte(), 0x00, 0xEF.toByte()
    )

    fun powerOff(): ByteArray = byteArrayOf(
        0x7e, 0x00, 0x04, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x00, 0xEF.toByte()
    )

    fun setColor(r: Int, g: Int, b: Int): ByteArray = byteArrayOf(
        0x7e, 0x00, 0x05, 0x03,
        r.coerceIn(0, 255).toByte(),
        g.coerceIn(0, 255).toByte(),
        b.coerceIn(0, 255).toByte(),
        0x00,
        0xEF.toByte()
    )





    fun setBrightness(level: Int): ByteArray = byteArrayOf(
        0x7e, 0x00, 0x01,
        level.coerceIn(0, 255).toByte(),
        0x00, 0x00, 0x00, 0x00,
        0xEF.toByte()
    )
}


