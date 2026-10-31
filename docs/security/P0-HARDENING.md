# P0 — الإسعاف الأمني (Dolphy 3.0)

## الهدف

إغلاق أخطر الثغرات قبل بناء أي ميزات جديدة، وإدخال بوابات جودة (CI + اختبارات + lint) تمنع التراجع.

## ما نُفّذ

### 1. توقيع الإصدار
- `release` لم يعد يستخدم مفتاح debug.
- التوقيع يُقرأ من متغيرات بيئة أو `keystore.properties` (git-ignored).
- `assembleRelease` يفشل برسالة واضحة إذا غاب المفتاح أو ملف keystore.
- التفاصيل: [`RELEASE_SIGNING.md`](RELEASE_SIGNING.md).

### 2. حماية الودجت
- إزالة كل الإجراءات المخصّصة من `intent-filter` للودجت (بقي `APPWIDGET_UPDATE` فقط).
- كل إجراء يغيّر الحالة يحمل رمز تثبيت عشوائياً 256-bit في `PendingIntent` غير قابل للتعديل.
- `BleSpamWidgetProvider` و`FlipperWidgetProvider` يرفضان أي Broadcast بدون الرمز.
- المقارنة عبر `MessageDigest.isEqual` (ثابت الزمن).

### 3. تشفير الأسرار
- `SecureEnvelope`: AES-256-GCM، IV عشوائي 12 بايت لكل عملية، tag 128-bit.
- `AndroidKeyStoreKeys`: مفتاح غير قابل للتصدير داخل Android Keystore.
- `SecurePreferences`: تشفير عند الكتابة، فك عند القراءة، وترحيل تلقائي للقيم النصية القديمة.
- `wifi_passwords` (مجموعات كلمات المرور) انتقلت إلى `SecurePreferences`.

### 4. موافقة تثبيت الإضافات
- `PluginPreviewScreen` يعرض مربع موافقة صريحاً.
- زر التثبيت معطّل حتى تُفعّل الموافقة.
- الموافقة مطلوبة أيضاً عند التحديث.

### 5. تحصين خادم QR/Ktor
- الربط بعنوان LAN الفعلي بدل `0.0.0.0` (افتراضياً `127.0.0.1` إذا لم يُمرَّر عنوان).
- رفض أي طلب يحمل `Host` لا يطابق العنوان/المنفذ (مضاد DNS rebinding).
- رفض WebSocket إذا كان `Origin` غير مطابق (مضاد CSWSH).
- حد أقصى لإطار WebSocket = 64KB بدل `Long.MAX_VALUE`.
- رؤوس أمان: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`.
- تقييد CSP إلى `connect-src 'self'` و`frame-ancestors 'none'`.
- إخفاء محتوى الإدخال من السجلات (يُسجَّل الطول فقط).

### 6. الجودة
- تفعيل lint مع baseline.
- اختبارات وحدات لـ`SecureEnvelope` و`LocalRequestPolicy`.
- CI على GitHub Actions: JDK 21 + Android 36 + unit tests + lint + assembleDebug.
- `gradlew` أصبح جزءاً من المستودع.

## التحقق

```bash
JAVA_HOME=/path/to/jdk21 ./gradlew :app:testDebugUnitTest
JAVA_HOME=/path/to/jdk21 ./gradlew :app:lintDebug
JAVA_HOME=/path/to/jdk21 ./gradlew :app:assembleDebug
```

## مخاطر متبقية (مقصودة، تُعالَج في مراحل لاحقة)

| الخطر | المعالجة اللاحقة |
|---|---|
| الإضافات تعمل بصلاحيات التطبيق كاملة | عزل + توقيع + صلاحيات مُعلنة (P3) |
| `usesCleartextTraffic=true` لأجل أجهزة LAN | `LocalHttpPolicy` يفرض RFC1918/loopback قبل كل اتصال (P1) |
| أوامر `su` متناثرة | `rootd` + allowlist + تدقيق (P1) |
| لا توقيع لحزم التعريف | Driver Packs موقعة (P3) |
| لا rate limiting على `/ws` | إضافة في P2 |
| بعض الأذونات قد تكون غير مستخدمة | تدقيق كامل عند تفكيك الوحدات (P1) |

## معيار الخروج

- [x] لا يستخدم release مفتاح debug.
- [x] ودجت مصدّر بدون تنفيذ إجراءات غير موثقة.
- [x] لا تُخزَّن كلمات مرور Wi-Fi كنص صريح.
- [x] تثبيت الإضافة يتطلب موافقة صريحة.
- [x] خادم LAN لا يستمع على كل الواجهات.
- [x] اختبارات وحدات + CI.
- [x] توثيق التوقيع ونموذج التهديد.
