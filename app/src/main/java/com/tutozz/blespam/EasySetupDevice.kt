package com.tutozz.blespam

data class EasySetupDevice(
    val value: String,
    val name: String,
    val deviceType: type
) {
    enum class type { BUDS, WATCH }

    fun toManufacturerData(): String = when (deviceType) {
        type.WATCH -> "010002000101FF000043${value.removePrefix("0x").removePrefix("0X")}"
        type.BUDS -> "42098102141503210109${value.removePrefix("0x").removePrefix("0X").take(4)}01${value.removePrefix("0x").removePrefix("0X").drop(4)}063C948E00000000C700"
        else -> value
    }
}
