package com.tutozz.blespam

enum class ContinuityType { DEVICE, ACTION, NOTYOURDEVICE }

data class ContinuityDevice(
    val value: String,
    val name: String,
    val deviceType: ContinuityType
)
