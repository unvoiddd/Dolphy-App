package com.droid.dolphy.rootd

/**
 * Every privileged action Dolphy can perform. There is deliberately no
 * `exec(String)` operation: plugins and features can only request one of these
 * typed operations.
 */
enum class RootOperation(val requiredCapability: RootCapability) {
    ID(RootCapability.INFO),
    LIST_INTERFACES(RootCapability.NETWORK),
    IFACE_STATE(RootCapability.NETWORK),
    GETPROP(RootCapability.INFO),
    SETTING_GET(RootCapability.INFO),
    NFC_ENABLE(RootCapability.NFC),
    NFC_DISABLE(RootCapability.NFC),
    WIFI_RESTART(RootCapability.NETWORK),
    USB_GADGET_HID(RootCapability.USB_GADGET),
    LIRC_LIST(RootCapability.IR),
}

enum class RootCapability {
    INFO,
    NETWORK,
    NFC,
    IR,
    USB_GADGET,
}

data class RootdResult(
    val ok: Boolean,
    val exitCode: Int,
    val output: String,
    val error: String? = null,
    val durationMs: Long = 0,
)

class RootdException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RootPermissionDeniedException(operation: RootOperation) :
    SecurityException("Root capability not granted for $operation")
