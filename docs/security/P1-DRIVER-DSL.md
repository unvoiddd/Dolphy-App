# Driver DSL (P1 — مكتمل جزئياً)

تم بناء بنية `HardwareDescriptor` و `Transport` و `CapabilityIndex` و `DeviceLeaseManager`. DSL نفسه (لغة تعريف الحزم الموقعة) لم يُكتب بعد بسبب الأولوية الأعلى لـ `rootd` و `bridge:flipper` و `PluginPermissions` و `DeviceGraphScreen`.

## الشكل المقترح (لم يُنفذ بعد):

```kotlin
// :driver/flipper/src/main/kotlin/com/droid/dolphy/driver/flipper/FlipperDriver.kt
data class DriverPack(
    val descriptor: HardwareDescriptor,
    val driverClass: String,
    val capabilities: List<HwCapability>,
    val signedBy: String,
)
```

## ما تم إنجازه:
- `core/model` يعرف `HardwareDescriptor` مع `driverId`.
- `core/hal` يعرف `HardwareDriver` interface مع `open/execute/close`.
- `bridge/flipper` يوفر `FlipperCliAdapter` و `FlipperUsbCdcTransport` كتنفيذ فعلي لـ `Transport.USB_SERIAL`.

## ما تبقى:
- كتابة `DriverDslParser` لتحويل `*.dsl` أو `*.json` إلى `HardwareDescriptor` + `HardwareDriver`.
- توقيع حزم التعريف (`Driver Pack signing`) — يعتمد على `core/security` (`SecureEnvelope`).
- دمج DSL في `HardwareManager.registerDriver()`.
