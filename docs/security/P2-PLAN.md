# P2 — Sandbox الإضافات + توقيع حزم التعريف + عزل USB

## الهدف
- عزل الإضافات في sandbox مع صلاحيات مُعلنة (`PluginPermission`).
- توقيع حزم تعريف العتاد (`Driver Pack`) عبر `core/security` (`SecureEnvelope` + توقيع رقمي).
- منع الوصول إلى USB أو Root أو ملفات النظام بدون `LabSession` نشطة.
- إضافة `LocalHttpPolicy` إلى كل طلبات `httpRequest` (تم إضافتها في `core/security`).

## ما تم إنجازه من P1 كأساس لـ P2:
- `PluginPermissions` (نظام الصلاحيات) مكتمل ومُدمج في `PluginDeviceApis` و `PluginAndroidApis` و `PythonPluginBridge`.
- `LocalHttpPolicy` (منع الاتصال بالخارج) مكتمل ومُختبر (`LocalHttpPolicyTest`).
- `DeviceGraphScreen` تعرض الأجهزة المكتشفة وتربط بـ `FlipperUsbMonitor`.
- `HardwareRuntime` يدير `connectFlipper()` و `disconnectFlipper()` مع دورة إذن USB (`PendingIntent`).

## المهام المتبقية لـ P2:
1. `PluginSandbox`: إنشاء `Context` معزول لكل إضافة (باستخدام `DexClassLoader` مع `pluginFilesDir` فقط).
2. `DriverPackSigner`: استخدام `SecureEnvelope` + مفتاح `AndroidKeyStore` لتوقيع `*.dsl`.
3. `LabSession` enforcement: ربط كل عملية `SUBGHZ_TX` أو `USB_GADGET` بـ `LabSession` نشطة (`isActive()` فحص الزمن).
4. `UsbIsolation`: منع أي تطبيق آخر من الوصول إلى `FlipperUsbMonitor` أثناء جلسة `LabSession` نشطة.
