# rootd daemon (P1 — MVP مكتمل، P2 — خدمة مستمرة)

## الحالة الحالية (P1 MVP):
- `rootd/src/main/kotlin/com/droid/dolphy/rootd/PrivilegedBroker.kt`: وسيط `su` يعمل عبر `ProcessBuilder("su", "-c", command)`. يطبق `RootOperation` مع `RootAuditLog`.
- `RootdProtocol.kt`: يبني أوامر ثابتة فقط (لا `exec(String)` حرة). يتحقق من `IFACE_PATTERN` و `PROP_PATTERN`.
- `RootAuditLog.kt`: سجل JSONL محلي (2MB max، تدوير تلقائي).

## ما تبقى لـ P2 (خدمة مستمرة):
1. تحويل `PrivilegedBroker` إلى `Android Service` مع `AIDL` (`IRootdInterface.aidl`).
2. إضافة `startForeground()` مع `NotificationChannel` دائم.
3. ربط `PluginPermissions.ROOT` و `SHIZUKU` بـ `RootAuditLog.record()` مع `pluginId` و `userId`.
4. إضافة `RootdService.bind()` من `MainActivity` أو `DolphyApplication`.
