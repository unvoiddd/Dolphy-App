package com.droid.dolphy.rootd

/**
 * Builds fixed shell commands from typed operations. Arguments are validated
 * against strict allowlists before being quoted, so neither plugins nor network
 * input can inject shell syntax.
 */
object RootdProtocol {

    private val IFACE_PATTERN = Regex("[A-Za-z0-9_.-]{1,32}")
    private val PROP_PATTERN = Regex("[A-Za-z0-9_.-]{1,128}")
    private val SETTING_NAMESPACES = setOf("global", "system", "secure")

    fun commandFor(operation: RootOperation, args: Map<String, String> = emptyMap()): String {
        return when (operation) {
            RootOperation.ID -> "id"
            RootOperation.LIST_INTERFACES -> "ls /sys/class/net"
            RootOperation.IFACE_STATE -> {
                val iface = requireArg(args, "iface", IFACE_PATTERN)
                val up = when (requireArg(args, "state", Regex("up|down"))) {
                    "up" -> "up"
                    else -> "down"
                }
                "ip link set dev $iface $up"
            }
            RootOperation.GETPROP -> {
                val key = requireArg(args, "key", PROP_PATTERN)
                "getprop $key"
            }
            RootOperation.SETTING_GET -> {
                val namespace = requireArg(args, "namespace", Regex("global|system|secure"))
                require(namespace in SETTING_NAMESPACES) { "invalid namespace" }
                val key = requireArg(args, "key", PROP_PATTERN)
                "settings get $namespace $key"
            }
            RootOperation.NFC_ENABLE ->
                "svc nfc enable 2>/dev/null; cmd nfc enable 2>/dev/null; settings put global nfc_on 1"
            RootOperation.NFC_DISABLE ->
                "svc nfc disable 2>/dev/null; cmd nfc disable 2>/dev/null; settings put global nfc_on 0"
            RootOperation.WIFI_RESTART ->
                "svc wifi disable; sleep 1; svc wifi enable"
            RootOperation.USB_GADGET_HID ->
                "ls /config/usb_gadget 2>/dev/null || ls /sys/class/udc 2>/dev/null"
            RootOperation.LIRC_LIST ->
                "ls -l /dev/lirc* 2>/dev/null; ls /sys/class/rc 2>/dev/null"
        }
    }

    fun validateOperation(operation: RootOperation, args: Map<String, String>) {
        commandFor(operation, args)
    }

    private fun requireArg(args: Map<String, String>, name: String, pattern: Regex): String {
        val value = args[name] ?: throw IllegalArgumentException("missing argument: $name")
        if (!pattern.matches(value)) {
            throw IllegalArgumentException("invalid argument: $name")
        }
        return value
    }
}
