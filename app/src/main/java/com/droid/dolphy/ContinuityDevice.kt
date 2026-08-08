package com.droid.dolphy

enum class ContinuityType { DEVICE, ACTION, NOTYOURDEVICE }

data class ContinuityDevice(
    val value: String,
    val name: String,
    val deviceType: ContinuityType
)

