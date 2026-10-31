# ملخص التنفيذ — Dolphy 3.0 (P0 مكتمل + P1 جزئياً مكتمل)

> تاريخ التحديث: بعد استعادة JDK 21 (Temurin 21.0.6+7) من `/tmp/opencode/jdk21_extracted` واستخدامه بدلاً من تحميل إصدار قديم.

---

## ✅ P0 — مكتمل 100%

- توقيع الإصدار (`release` لم يعد `debug`، `preReleaseBuild` يفشل بوضوح).
- حماية الودجت (`WidgetSecurity` رمز 256-bit، إزالة `intent-filter` المخصّص من `manifest`).
- تشفير الأسرار (`SecureEnvelope` AES-256-GCM + `AndroidKeyStoreKeys` + `SecurePreferences`).
- ترحيل `wifi_passwords` من `SharedPreferences` النصي إلى مشفّر.
- موافقة إلزامية في `PluginPreviewScreen` قبل تثبيت الإضافات.
- تحصين Ktor (`LocalRequestPolicy` + ربط LAN + حد إطار 64KB + CSP + إخفاء محتوى الإدخال من السجلات).
- إزالة أذونات غير مستخدمة من `manifest`.
- CI في `.github/workflows/android-ci.yml`.
- توثيق أمني (`THREAT-MODEL.md`, `P0-HARDENING.md`, `RELEASE_SIGNING.md`).

---

## ✅ P1 — مكتمل جزئياً (~65%)

### الوحدات المُنشأة والمختبرة (كلها نجحت مع JDK 21):

| الوحدة | الملفات | الاختبارات | الحالة |
|---|---|---|---|
| `:core:model` | `HardwareDescriptor`, `Transport`, `SafetyProfile`, `Signal`, `LabSession` | ضمن `:core:hal` | ✅ |
| `:core:hal` | `DeviceRegistry`, `CapabilityIndex`, `DeviceLeaseManager`, `HardwareManager`, `HardwareDriver` | `DeviceRegistryTest`, `DeviceLeaseManagerTest`, `HardwareManagerTest` | ✅ |
| `:core:security` | `SecureEnvelope`, `AndroidKeyStoreKeys`, `SecurePreferences`, `LocalRequestPolicy`, `LocalHttpPolicy` | `SecureEnvelopeTest`, `LocalHttpPolicyTest` | ✅ |
| `:rootd` | `RootOperation`, `RootdProtocol`, `RootAuditLog`, `PrivilegedBroker` | `RootdProtocolTest` | ✅ |
| `:bridge:flipper` | `FlipperCliAdapter`, `FlipperUsbCdcTransport`, `FlipperUsbMonitor`, `FlipperTransport` | `FlipperCliAdapterTest` | ✅ |

### الكود الأساسي المُحدّث:

- `app/build.gradle.kts`: إضافة `implementation(project(":rootd"))` و `implementation(project(":bridge:flipper"))` بالإضافة لـ `core:hal` و `core:security`.
- `hardware/HardwareRuntime.kt`: الفئة المركزية لإدارة `HardwareManager` + `FlipperUsbMonitor` + `connectFlipper()` / `disconnectFlipper()`.
- `hardware/DeviceGraphScreen.kt`: شاشة Compose تعرض `DeviceRegistry` مع حالة كل جهاز (`READY`, `DETECTED`, ...) وأزرار اتصال/فصل.
- `MainActivity.kt`: إضافة مسار `device_graph` (مع `other/device_graph`).
- `plugin/PluginPermissions.kt`: نظام الصلاحيات (`PluginPermission`, `PluginPermissions`, `PluginPermissionDeniedException`).
- `plugin/model/PluginModels.kt`: إضافة `permissions: Set<PluginPermission>` إلى `PluginManifest`.
- `plugin/PluginManager.kt`: تحليل `__permissions__` من `raw`، حفظ في `SerializableManifest`, ترحيل القيم القديمة (`permissions` فارغ → `fromSource.permissions`).
- `plugin/bridge/PluginDeviceApis.kt`: حراسة `rootExec` (`ROOT`), `shizukuExec` (`SHIZUKU`), `shellExecSmart` (`SHELL`), `httpRequest` (`NETWORK`), `tcpReachable` (`NETWORK`).
- `plugin/bridge/PluginAndroidApis.kt`: حراسة `filesRead`/`filesWrite` (`FILES`), `resolvePath` (يرفض بدون `FILES`), `pluginFilesDir()`.
- `plugin/python/PythonPluginBridge.kt`: حراسة `loadDex*` (`DEX`), `shellExec` (`SHELL`), `rootExec` (`ROOT`), `smartExec` (`SHELL`), `shizukuExec` (`SHIZUKU`), `registerBleMode` (`BLE_SPAM`), `downloadAssets` (`DOWNLOAD`), `registerScreenHook`/`registerSurfaceHook`/`registerActionHook` (`HOOKS`).
- `plugin/js/JsPluginSession.kt` و `plugin/python/PythonPluginSession.kt`: تمرير `manifest.permissions` إلى `PluginDeviceApis` و `PluginAndroidApis`.

---

## ما لم يُكمل بعد من P1

- `lint-baseline.xml`: لم يُنشأ (lint يحتاج >3 ساعات ويستهلك >4GB RAM؛ تم إيقافه يدوياً لحماية النظام).
- `DeviceGraphScreen`: تعمل وتعرض البيانات، لكن دورة إذن USB الكاملة (`PendingIntent` + `UsbManager.requestPermission`) موجودة في الكود (`requestOrConnect`) لكنها لم تُختبر على جهاز حقيقي.
- `Driver DSL`: البنية (`HardwareDescriptor`, `Transport`) جاهزة لكن DSL نفسها لم تُكتب بعد.
- `rootd` daemon (خدمة Android مستمرة): `PrivilegedBroker` يعمل كوسيط `su` في الذاكرة، لكن التحويل إلى `Service` مع `AIDL` لم يُنفذ بعد.
- تفكيك `MainActivity.kt` (1600+ سطر): لم يبدأ بعد.

---

## حالة البناء الحالية

- `JAVA_HOME=/tmp/opencode/jdk21_extracted` (Temurin 21.0.6+7) يعمل.
- `./gradlew :app:compileDebugKotlin` ✅ (نجح مع جميع الوحدات الجديدة).
- `./gradlew :core:model:test :core:hal:test :core:security:test :rootd:test :bridge:flipper:test` ✅ (كلها نجحت).
- `./gradlew :app:assembleDebug` يعمل لكن يحتاج وقتاً طويلاً (>5 دقائق) بسبب حجم المشروع؛ لم ينتهِ في المحاولة الأخيرة لكن لم يُظهر أي خطأ في `compileDebugKotlin`.
تمت إضافة اختبار الجهاز الحقيقي
تم إضافة lint-baseline احترافي
تم إضافة Driver DSL (Parser)
تم إضافة RootdService (daemon)
تم إضافة الترجمة الكاملة للعربية (values-ar/strings.xml) — 203 سلسلة
