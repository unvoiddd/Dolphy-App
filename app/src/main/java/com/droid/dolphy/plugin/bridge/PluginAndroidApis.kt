package com.droid.dolphy.plugin.bridge

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothSocket
import android.os.ParcelUuid








class PluginAndroidApis(
    private val context: Context,
    private val pluginId: String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val appCtx = context.applicationContext

    private val filesDir by lazy {
        File(appCtx.filesDir, "dolphy_plugins_data/$pluginId").apply { mkdirs() }
    }


    private var advertiseCallback: AdvertiseCallback? = null


    private var discoveryReceiver: BroadcastReceiver? = null
    private var discoveryCallback: ((String) -> Unit)? = null


    private val gattMap = ConcurrentHashMap<String, BluetoothGatt>()


    @Volatile private var mediaPlayer: MediaPlayer? = null


    private var nsdListener: NsdManager.DiscoveryListener? = null
    private val nsdFound = ConcurrentHashMap<String, NsdServiceInfo>()


    private val sensorListeners = ConcurrentHashMap<String, SensorEventListener>()
    private val sensorSeq = AtomicInteger(0)

    private val rfcommMap = ConcurrentHashMap<String, BluetoothSocket>()

    private var gattServer: BluetoothGattServer? = null
    private var gattServerCallback: ((String) -> Unit)? = null

    private val btAdapter: BluetoothAdapter?
        get() = (appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val wifiManager: WifiManager?
        get() = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager


    fun clipboardGet(): String {
        return try {
            val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(appCtx)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun clipboardSet(text: String): Boolean = try {
        val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dolphy", text))
        true
    } catch (_: Exception) {
        false
    }


    @Suppress("DEPRECATION")
    fun vibrate(ms: Long): Boolean = try {
        val v = vibrator()
        if (Build.VERSION.SDK_INT >= 26) {
            v?.vibrate(VibrationEffect.createOneShot(ms.coerceIn(1, 5000), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v?.vibrate(ms.coerceIn(1, 5000))
        }
        true
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun vibratePattern(patternMs: LongArray, repeat: Int = -1): Boolean = try {
        val v = vibrator()
        if (Build.VERSION.SDK_INT >= 26) {
            v?.vibrate(VibrationEffect.createWaveform(patternMs, repeat))
        } else {
            v?.vibrate(patternMs, repeat)
        }
        true
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun vibrateCancel() {
        try {
            vibrator()?.cancel()
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= 31) {
            (appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }


    fun notifyShow(id: Int, title: String, text: String, channelId: String = "dolphy_plugins"): Boolean {
        return try {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(channelId, "Dolphy Plugins", NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(ch)
            }
            val n = NotificationCompat.Builder(appCtx, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify("plugin_$pluginId", id, n)
            true
        } catch (e: Exception) {
            Log.w(TAG, "notifyShow", e)
            false
        }
    }

    fun notifyCancel(id: Int) {
        try {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel("plugin_$pluginId", id)
        } catch (_: Exception) {
        }
    }


    fun openUrl(url: String): Boolean = try {
        val u = if (url.startsWith("http")) url else "https://$url"
        appCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun shareText(text: String, title: String = "Share"): Boolean = try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appCtx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun openSettings(action: String?): Boolean = try {
        val act = when (action?.lowercase()) {
            null, "", "app" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bt", "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "wireless" -> Settings.ACTION_WIRELESS_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "nfc_payment" -> Settings.ACTION_NFC_PAYMENT_SETTINGS
            else -> action
        }
        val intent = if (act == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
            Intent(act, Uri.parse("package:${appCtx.packageName}"))
        } else {
            Intent(act)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appCtx.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    fun startActivity(action: String, dataUri: String? = null, extrasJson: String? = null): Boolean = try {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!dataUri.isNullOrBlank()) intent.data = Uri.parse(dataUri)
        if (!extrasJson.isNullOrBlank()) {
            val o = JSONObject(extrasJson)
            o.keys().forEach { k ->
                when (val v = o.get(k)) {
                    is Boolean -> intent.putExtra(k, v)
                    is Int -> intent.putExtra(k, v)
                    is Long -> intent.putExtra(k, v)
                    is Double -> intent.putExtra(k, v)
                    else -> intent.putExtra(k, v.toString())
                }
            }
        }
        appCtx.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "startActivity", e)
        false
    }

    fun dial(number: String): Boolean = try {
        appCtx.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        false
    }


    fun filesList(sub: String = ""): String {
        val dir = resolvePath(sub, dirOnly = true) ?: return "[]"
        val arr = JSONArray()
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            arr.put(
                JSONObject()
                    .put("name", f.name)
                    .put("path", relativePath(f))
                    .put("isDir", f.isDirectory)
                    .put("size", if (f.isFile) f.length() else 0)
                    .put("modified", f.lastModified())
            )
        }
        return arr.toString()
    }

    fun filesRead(path: String): String? {
        val f = resolvePath(path) ?: return null
        if (!f.isFile) return null
        return try {
            f.readText(Charsets.UTF_8).take(2_000_000)
        } catch (_: Exception) {
            null
        }
    }

    fun filesWrite(path: String, content: String, append: Boolean = false): Boolean {
        val f = resolvePath(path) ?: return false
        return try {
            f.parentFile?.mkdirs()
            if (append) f.appendText(content, Charsets.UTF_8) else f.writeText(content, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun filesDelete(path: String): Boolean {
        val f = resolvePath(path) ?: return false
        return try {
            f.deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }

    fun filesExists(path: String): Boolean = resolvePath(path)?.exists() == true

    fun filesWriteBase64(path: String, b64: String): Boolean {
        val f = resolvePath(path) ?: return false
        return try {
            f.parentFile?.mkdirs()
            f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun filesReadBase64(path: String): String? {
        val f = resolvePath(path) ?: return null
        if (!f.isFile || f.length() > 5_000_000) return null
        return try {
            Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun filesMkdir(path: String): Boolean {
        val f = resolvePath(path, dirOnly = true) ?: return false
        return try {
            f.mkdirs() || f.isDirectory
        } catch (_: Exception) {
            false
        }
    }

    fun filesStat(path: String): String {
        val f = resolvePath(path) ?: return JSONObject().put("ok", false).put("error", "invalid").toString()
        if (!f.exists()) return JSONObject().put("ok", false).put("exists", false).toString()
        return try {
            JSONObject()
                .put("ok", true)
                .put("exists", true)
                .put("path", relativePath(f))
                .put("name", f.name)
                .put("isDir", f.isDirectory)
                .put("isFile", f.isFile)
                .put("size", if (f.isFile) f.length() else 0)
                .put("modified", f.lastModified())
                .put("absolute", f.absolutePath)
                .put("canRead", f.canRead())
                .put("canWrite", f.canWrite())
                .toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message).toString()
        }
    }

    fun filesCopy(from: String, to: String): Boolean {
        val src = resolvePath(from) ?: return false
        val dst = resolvePath(to) ?: return false
        if (!src.exists()) return false
        return try {
            dst.parentFile?.mkdirs()
            if (src.isDirectory) {
                src.copyRecursively(dst, overwrite = true)
            } else {
                src.copyTo(dst, overwrite = true)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun filesMove(from: String, to: String): Boolean {
        if (!filesCopy(from, to)) return false
        return filesDelete(from)
    }

    fun filesAppend(path: String, content: String): Boolean = filesWrite(path, content, append = true)

    fun filesAbsolute(path: String): String? = resolvePath(path)?.absolutePath

    fun filesShare(path: String, mime: String? = null, title: String = "Share"): Boolean {
        val f = resolvePath(path) ?: return false
        if (!f.isFile) return false
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appCtx,
                "${appCtx.packageName}.provider",
                f,
            )
            val type = mime ?: guessMime(f.name)
            val send = Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appCtx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.w(TAG, "filesShare", e)
            false
        }
    }

    fun filesOpenWith(path: String, mime: String? = null): Boolean {
        val f = resolvePath(path) ?: return false
        if (!f.isFile) return false
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appCtx,
                "${appCtx.packageName}.provider",
                f,
            )
            val type = mime ?: guessMime(f.name)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appCtx.startActivity(view)
            true
        } catch (e: Exception) {
            Log.w(TAG, "filesOpenWith", e)
            false
        }
    }

    fun exportSandboxToUri(sandboxPath: String, destUri: Uri): String {
        val f = resolvePath(sandboxPath) ?: return JSONObject().put("ok", false).put("error", "not_found").toString()
        if (!f.isFile) return JSONObject().put("ok", false).put("error", "not_file").toString()
        return try {
            appCtx.contentResolver.openOutputStream(destUri)?.use { out ->
                f.inputStream().use { it.copyTo(out) }
            } ?: return JSONObject().put("ok", false).put("error", "open_failed").toString()
            JSONObject()
                .put("ok", true)
                .put("path", relativePath(f))
                .put("uri", destUri.toString())
                .put("size", f.length())
                .toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message).toString()
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "txt", "log", "md", "ir" -> "text/plain"
            "json" -> "application/json"
            "html", "htm" -> "text/html"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }


    fun cryptoHash(algo: String, text: String): String? = try {
        val md = java.security.MessageDigest.getInstance(
            when (algo.lowercase()) {
                "md5" -> "MD5"
                "sha1", "sha-1" -> "SHA-1"
                "sha256", "sha-256" -> "SHA-256"
                "sha512", "sha-512" -> "SHA-512"
                else -> algo
            },
        )
        md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    fun cryptoHashBytes(algo: String, bytes: ByteArray): String? = try {
        val md = java.security.MessageDigest.getInstance(
            when (algo.lowercase()) {
                "md5" -> "MD5"
                "sha1", "sha-1" -> "SHA-1"
                "sha256", "sha-256" -> "SHA-256"
                "sha512", "sha-512" -> "SHA-512"
                else -> algo
            },
        )
        md.digest(bytes).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    fun cryptoHashFile(algo: String, path: String): String? {
        val f = resolvePath(path) ?: return null
        if (!f.isFile || f.length() > 50_000_000) return null
        return try {
            val md = java.security.MessageDigest.getInstance(
                when (algo.lowercase()) {
                    "md5" -> "MD5"
                    "sha1", "sha-1" -> "SHA-1"
                    "sha256", "sha-256" -> "SHA-256"
                    "sha512", "sha-512" -> "SHA-512"
                    else -> algo
                },
            )
            f.inputStream().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun cryptoBase64Encode(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun cryptoBase64Decode(b64: String): String? = try {
        String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    fun cryptoHexEncode(text: String): String =
        text.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    fun cryptoHexDecode(hex: String): String? {
        val clean = hex.trim().replace(" ", "").replace(":", "")
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return try {
            String(ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun cryptoRandomHex(bytes: Int = 16): String {
        val n = bytes.coerceIn(1, 1024)
        val arr = ByteArray(n)
        java.security.SecureRandom().nextBytes(arr)
        return arr.joinToString("") { "%02x".format(it) }
    }

    fun cryptoUuid(): String = UUID.randomUUID().toString()

    
    fun pluginFilesDir(): File = filesDir

    
    fun importUriToSandbox(
        uri: Uri,
        destRelative: String? = null,
        includeBase64: Boolean = false,
        maxBytes: Long = 12_000_000L,
    ): String {
        return try {
            val cr = appCtx.contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val displayName = queryDisplayName(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "import_${System.currentTimeMillis()}"
            val safeName = displayName.replace(Regex("""[^\w.\- ()\[\]]+"""), "_").take(120)
            val rel = when {
                !destRelative.isNullOrBlank() -> destRelative.trim().removePrefix("/")
                mime.startsWith("image/") -> "media/images/$safeName"
                mime.startsWith("video/") -> "media/videos/$safeName"
                mime.startsWith("audio/") -> "media/audio/$safeName"
                else -> "media/files/$safeName"
            }
            val dest = resolvePath(rel) ?: return JSONObject()
                .put("ok", false)
                .put("error", "invalid dest")
                .toString()
            dest.parentFile?.mkdirs()
            cr.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > maxBytes) {
                    return JSONObject()
                        .put("ok", false)
                        .put("error", "file_too_large")
                        .put("maxBytes", maxBytes)
                        .toString()
                }
                dest.writeBytes(bytes)
            } ?: return JSONObject().put("ok", false).put("error", "open_failed").toString()

            val o = JSONObject()
                .put("ok", true)
                .put("path", relativePath(dest))
                .put("name", dest.name)
                .put("size", dest.length())
                .put("mime", mime)
                .put("uri", uri.toString())
            if (includeBase64 && dest.length() <= 5_000_000) {
                o.put("base64", Base64.encodeToString(dest.readBytes(), Base64.NO_WRAP))
            }
            o.toString()
        } catch (e: Exception) {
            Log.w(TAG, "importUriToSandbox", e)
            JSONObject().put("ok", false).put("error", e.message ?: "import_failed").toString()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            appCtx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    
    fun createCameraCaptureTarget(fileName: String? = null): Pair<File, Uri>? {
        return try {
            val name = (fileName ?: "camera_${System.currentTimeMillis()}.jpg")
                .replace(Regex("""[^\w.\-]+"""), "_")
            val dir = File(appCtx.cacheDir, "plugin_camera/$pluginId").apply { mkdirs() }
            val file = File(dir, name)
            if (file.exists()) file.delete()
            file.createNewFile()
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appCtx,
                "${appCtx.packageName}.provider",
                file,
            )
            Pair(file, uri)
        } catch (e: Exception) {
            Log.w(TAG, "createCameraCaptureTarget", e)
            null
        }
    }

    
    fun importCameraFile(
        captureFile: File,
        destRelative: String? = null,
        includeBase64: Boolean = false,
    ): String {
        return try {
            if (!captureFile.isFile || captureFile.length() == 0L) {
                return JSONObject().put("ok", false).put("error", "empty_capture").toString()
            }
            val rel = destRelative?.trim()?.removePrefix("/")
                ?: "media/camera/${captureFile.name}"
            val dest = resolvePath(rel) ?: return JSONObject()
                .put("ok", false)
                .put("error", "invalid dest")
                .toString()
            dest.parentFile?.mkdirs()
            captureFile.copyTo(dest, overwrite = true)
            try {
                captureFile.delete()
            } catch (_: Exception) {
            }
            val o = JSONObject()
                .put("ok", true)
                .put("path", relativePath(dest))
                .put("name", dest.name)
                .put("size", dest.length())
                .put("mime", "image/jpeg")
                .put("source", "camera")
            if (includeBase64 && dest.length() <= 5_000_000) {
                o.put("base64", Base64.encodeToString(dest.readBytes(), Base64.NO_WRAP))
            }
            o.toString()
        } catch (e: Exception) {
            Log.w(TAG, "importCameraFile", e)
            JSONObject().put("ok", false).put("error", e.message ?: "camera_import_failed").toString()
        }
    }

    fun hasCamera(): Boolean = hasFeature(PackageManager.FEATURE_CAMERA_ANY)
        || hasFeature(PackageManager.FEATURE_CAMERA)


    
    private fun sanitizeAssetPath(path: String): String {
        return path.trim()
            .replace('\\', '/')
            .removePrefix("/")
            .split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")
    }

    fun assetsExists(path: String): Boolean {
        val clean = sanitizeAssetPath(path)
        if (clean.isEmpty()) return true
        return try {
            appCtx.assets.open(clean).use { true }
        } catch (_: Exception) {
            try {
                appCtx.assets.list(clean)?.isNotEmpty() == true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun assetsList(path: String = ""): String {
        val clean = sanitizeAssetPath(path)
        return try {
            val names = appCtx.assets.list(clean).orEmpty().sorted()
            val out = JSONArray()
            for (name in names) {
                val child = if (clean.isEmpty()) name else "$clean/$name"
                val canOpen = runCatching {
                    appCtx.assets.open(child).use { }
                    true
                }.getOrDefault(false)
                val kids = runCatching { appCtx.assets.list(child) }.getOrNull()
                val isDir = !canOpen && kids != null
                out.put(
                    JSONObject()
                        .put("name", name)
                        .put("path", child)
                        .put("isDir", isDir),
                )
            }
            out.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    fun assetsReadText(path: String, maxBytes: Int = 2_000_000): String? {
        val clean = sanitizeAssetPath(path)
        if (clean.isEmpty()) return null
        return try {
            appCtx.assets.open(clean).use { input ->
                val bytes = input.readBytes()
                if (bytes.size > maxBytes) {
                    String(bytes, 0, maxBytes, Charsets.UTF_8)
                } else {
                    String(bytes, Charsets.UTF_8)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun assetsReadBase64(path: String, maxBytes: Int = 5_000_000): String? {
        val clean = sanitizeAssetPath(path)
        if (clean.isEmpty()) return null
        return try {
            appCtx.assets.open(clean).use { input ->
                val bytes = input.readBytes()
                if (bytes.size > maxBytes) return null
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
    }

    
    fun assetsDataUri(path: String, maxBytes: Int = 5_000_000): String? {
        val clean = sanitizeAssetPath(path)
        if (clean.isEmpty()) return null
        val b64 = assetsReadBase64(clean, maxBytes) ?: return null
        val mime = when (clean.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "txt", "ir", "md" -> "text/plain"
            else -> "application/octet-stream"
        }
        return "data:$mime;base64,$b64"
    }


    private fun resolvePath(path: String, dirOnly: Boolean = false): File? {
        val clean = path.trim().removePrefix("/").replace("..", "")
        val f = if (clean.isEmpty()) filesDir else File(filesDir, clean)
        val canon = try {
            f.canonicalFile
        } catch (_: Exception) {
            f
        }
        if (!canon.path.startsWith(filesDir.canonicalFile.path)) return null
        if (dirOnly && !canon.exists()) canon.mkdirs()
        return canon
    }

    private fun relativePath(f: File): String {
        val base = filesDir.canonicalFile.path
        val p = f.canonicalFile.path
        return if (p.startsWith(base)) p.removePrefix(base).removePrefix("/") else f.name
    }


    fun irHasEmitter(): Boolean {
        val cm = appCtx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        return cm?.hasIrEmitter() == true
    }

    fun irCarrierFrequenciesJson(): String {
        val cm = appCtx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            ?: return "[]"
        return try {
            val arr = JSONArray()
            cm.carrierFrequencies?.forEach { r ->
                arr.put(JSONObject().put("min", r.minFrequency).put("max", r.maxFrequency))
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    fun irTransmit(freqHz: Int, pattern: IntArray): Boolean {
        val cm = appCtx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager ?: return false
        return try {
            if (!cm.hasIrEmitter()) return false
            cm.transmit(freqHz, pattern)
            true
        } catch (e: Exception) {
            Log.w(TAG, "irTransmit", e)
            false
        }
    }


    @SuppressLint("MissingPermission")
    fun btBondedDevicesJson(): String {
        val arr = JSONArray()
        return try {
            btAdapter?.bondedDevices?.forEach { d ->
                arr.put(deviceJson(d))
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    @SuppressLint("MissingPermission")
    fun btStartDiscovery(onDevice: (String) -> Unit): Boolean {
        btStopDiscovery()
        val adapter = btAdapter ?: return false
        discoveryCallback = onDevice
        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (d != null) {
                            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                            val json = deviceJson(d).put("rssi", rssi).toString()
                            mainHandler.post { discoveryCallback?.invoke(json) }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {

                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            ContextCompat.registerReceiver(appCtx, discoveryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            @Suppress("DEPRECATION")
            return adapter.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "btStartDiscovery", e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun btStopDiscovery() {
        try {
            @Suppress("DEPRECATION")
            btAdapter?.cancelDiscovery()
        } catch (_: Exception) {
        }
        try {
            discoveryReceiver?.let { appCtx.unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        discoveryReceiver = null
        discoveryCallback = null
    }

    fun btOpenSettings(): Boolean = openSettings("bluetooth")

    fun btAddress(): String = try {
        @Suppress("DEPRECATION", "MissingPermission")
        btAdapter?.address ?: ""
    } catch (_: Exception) {
        ""
    }

    fun btName(): String = try {
        @Suppress("MissingPermission")
        btAdapter?.name ?: ""
    } catch (_: Exception) {
        ""
    }

    @SuppressLint("MissingPermission")
    private fun deviceJson(d: BluetoothDevice): JSONObject {
        return JSONObject()
            .put("name", try {
                d.name ?: ""
            } catch (_: Exception) {
                ""
            })
            .put("address", d.address ?: "")
            .put("bondState", d.bondState)
            .put("type", d.type)
            .put("deviceClass", try {
                d.bluetoothClass?.deviceClass ?: -1
            } catch (_: Exception) {
                -1
            })
    }


    @SuppressLint("MissingPermission")
    fun bleAdvertiseStart(
        manufacturerId: Int,
        payloadHex: String,
        connectable: Boolean = false,
        includeName: Boolean = false,
    ): Boolean {
        bleAdvertiseStop()
        val advertiser = btAdapter?.bluetoothLeAdvertiser ?: return false
        val payload = hexToBytes(payloadHex) ?: return false
        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(includeName)
            .addManufacturerData(manufacturerId, payload)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(connectable)
            .setTimeout(0)
            .build()
        val cb = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "advertise fail $errorCode")
            }
        }
        advertiseCallback = cb
        return try {
            advertiser.startAdvertising(settings, dataBuilder.build(), cb)
            true
        } catch (e: Exception) {
            Log.w(TAG, "bleAdvertiseStart", e)
            advertiseCallback = null
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun bleAdvertiseStop() {
        val cb = advertiseCallback ?: return
        try {
            btAdapter?.bluetoothLeAdvertiser?.stopAdvertising(cb)
        } catch (_: Exception) {
        }
        advertiseCallback = null
    }


    @SuppressLint("MissingPermission")
    fun gattConnect(address: String, onEvent: (String) -> Unit): Boolean {
        val device = try {
            btAdapter?.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        } ?: return false
        gattDisconnect(address)
        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val state = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> "connected"
                    BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
                    else -> "state_$newState"
                }
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "connection")
                            .put("state", state)
                            .put("status", status)
                            .put("address", address)
                            .toString()
                    )
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        gatt.discoverServices()
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val arr = JSONArray()
                gatt.services?.forEach { s ->
                    val chars = JSONArray()
                    s.characteristics?.forEach { c ->
                        chars.put(
                            JSONObject()
                                .put("uuid", c.uuid.toString())
                                .put("props", c.properties)
                        )
                    }
                    arr.put(JSONObject().put("uuid", s.uuid.toString()).put("characteristics", chars))
                }
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "services")
                            .put("status", status)
                            .put("services", arr)
                            .put("address", address)
                            .toString()
                    )
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: ByteArray(0)
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "read")
                            .put("uuid", characteristic.uuid.toString())
                            .put("status", status)
                            .put("hex", bytesToHex(value))
                            .put("address", address)
                            .toString()
                    )
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: ByteArray(0)
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "notify")
                            .put("uuid", characteristic.uuid.toString())
                            .put("hex", bytesToHex(value))
                            .put("address", address)
                            .toString()
                    )
                }
            }
        }
        return try {
            val gatt = if (Build.VERSION.SDK_INT >= 23) {
                device.connectGatt(appCtx, false, cb, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appCtx, false, cb)
            }
            if (gatt != null) {
                gattMap[address.uppercase()] = gatt
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "gattConnect", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun gattDisconnect(address: String) {
        val g = gattMap.remove(address.uppercase()) ?: return
        try {
            g.disconnect()
            g.close()
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun gattWrite(address: String, serviceUuid: String, charUuid: String, hex: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        val bytes = hexToBytes(hex) ?: return false
        return try {
            val svc = gatt.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        } catch (e: Exception) {
            Log.w(TAG, "gattWrite", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun gattRead(address: String, serviceUuid: String, charUuid: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        return try {
            val svc = gatt.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            gatt.readCharacteristic(ch)
        } catch (_: Exception) {
            false
        }
    }


    @SuppressLint("MissingPermission")
    fun wifiDisconnect(): Boolean = try {
        @Suppress("DEPRECATION")
        wifiManager?.disconnect() == true
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun wifiReconnect(): Boolean = try {
        @Suppress("DEPRECATION")
        wifiManager?.reconnect() == true
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun wifiAddSuggestion(ssid: String, passphrase: String?): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
            if (!passphrase.isNullOrBlank()) builder.setWpa2Passphrase(passphrase)
            val status = wifiManager?.addNetworkSuggestions(listOf(builder.build()))
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "wifiAddSuggestion", e)
            false
        }
    }


    fun netInterfacesJson(): String {
        val arr = JSONArray()
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp) return@forEach
                val addrs = JSONArray()
                ni.inetAddresses.toList().forEach { a ->
                    addrs.put(
                        JSONObject()
                            .put("host", a.hostAddress)
                            .put("ipv4", a is Inet4Address)
                            .put("loopback", a.isLoopbackAddress)
                    )
                }
                arr.put(
                    JSONObject()
                        .put("name", ni.name)
                        .put("display", ni.displayName)
                        .put("mtu", ni.mtu)
                        .put("addresses", addrs)
                )
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    fun nsdDiscover(serviceType: String, onEvent: (String) -> Unit): Boolean {
        nsdStop()
        val nsd = appCtx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return false
        val type = if (serviceType.endsWith(".")) serviceType else "$serviceType."
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                mainHandler.post {
                    onEvent(JSONObject().put("event", "started").put("type", regType).toString())
                }
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                nsdFound[service.serviceName] = service
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "found")
                            .put("name", service.serviceName)
                            .put("type", service.serviceType)
                            .toString()
                    )
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                nsdFound.remove(service.serviceName)
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("event", "lost")
                            .put("name", service.serviceName)
                            .toString()
                    )
                }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                mainHandler.post {
                    onEvent(JSONObject().put("event", "stopped").toString())
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                mainHandler.post {
                    onEvent(JSONObject().put("event", "error").put("code", errorCode).toString())
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }
        nsdListener = listener
        return try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        } catch (e: Exception) {
            Log.w(TAG, "nsdDiscover", e)
            false
        }
    }

    fun nsdStop() {
        val nsd = appCtx.getSystemService(Context.NSD_SERVICE) as? NsdManager
        try {
            nsdListener?.let { nsd?.stopServiceDiscovery(it) }
        } catch (_: Exception) {
        }
        nsdListener = null
        nsdFound.clear()
    }


    fun usbDevicesJson(): String {
        val usb = appCtx.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return "[]"
        val arr = JSONArray()
        return try {
            usb.deviceList?.values?.forEach { d ->
                arr.put(
                    JSONObject()
                        .put("deviceName", d.deviceName)
                        .put("vendorId", d.vendorId)
                        .put("productId", d.productId)
                        .put("class", d.deviceClass)
                        .put("subclass", d.deviceSubclass)
                        .put("protocol", d.deviceProtocol)
                        .put("interfaceCount", d.interfaceCount)
                )
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }


    @SuppressLint("MissingPermission")
    fun locationLastJson(): String {
        return try {
            val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var best: android.location.Location? = null
            for (p in providers) {
                try {
                    val loc = lm.getLastKnownLocation(p) ?: continue
                    if (best == null || loc.time > best!!.time) best = loc
                } catch (_: Exception) {
                }
            }
            if (best == null) return JSONObject().put("ok", false).toString()
            JSONObject()
                .put("ok", true)
                .put("lat", best.latitude)
                .put("lon", best.longitude)
                .put("accuracy", best.accuracy)
                .put("provider", best.provider)
                .put("time", best.time)
                .put("altitude", best.altitude)
                .toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message).toString()
        }
    }

    fun locationIsEnabled(): Boolean = try {
        val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    } catch (_: Exception) {
        false
    }


    fun sensorsListJson(): String {
        val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return "[]"
        val arr = JSONArray()
        sm.getSensorList(Sensor.TYPE_ALL).forEach { s ->
            arr.put(
                JSONObject()
                    .put("name", s.name)
                    .put("type", s.type)
                    .put("vendor", s.vendor)
                    .put("maxRange", s.maximumRange)
                    .put("resolution", s.resolution)
            )
        }
        return arr.toString()
    }

    fun sensorStart(type: Int, onEvent: (String) -> Unit): String? {
        val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val sensor = sm.getDefaultSensor(type) ?: return null
        val id = "s${sensorSeq.incrementAndGet()}"
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val vals = JSONArray()
                event.values.forEach { vals.put(it.toDouble()) }
                mainHandler.post {
                    onEvent(
                        JSONObject()
                            .put("id", id)
                            .put("type", event.sensor.type)
                            .put("values", vals)
                            .put("accuracy", event.accuracy)
                            .put("timestamp", event.timestamp)
                            .toString()
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorListeners[id] = listener
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        return id
    }

    fun sensorStop(id: String) {
        val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val l = sensorListeners.remove(id) ?: return
        try {
            sm?.unregisterListener(l)
        } catch (_: Exception) {
        }
    }

    fun sensorStopAll() {
        sensorListeners.keys.toList().forEach { sensorStop(it) }
    }


    fun audioPlayUrl(url: String, onDone: ((String) -> Unit)? = null): Boolean {
        audioStop()
        return try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(url)
            mp.setOnCompletionListener {
                mainHandler.post { onDone?.invoke(JSONObject().put("ok", true).toString()) }
                audioStop()
            }
            mp.setOnErrorListener { _, what, extra ->
                mainHandler.post {
                    onDone?.invoke(JSONObject().put("ok", false).put("what", what).put("extra", extra).toString())
                }
                audioStop()
                true
            }
            mp.prepareAsync()
            mp.setOnPreparedListener { it.start() }
            true
        } catch (e: Exception) {
            Log.w(TAG, "audioPlayUrl", e)
            false
        }
    }

    fun audioPlayFile(path: String): Boolean {
        val f = resolvePath(path) ?: return false
        if (!f.isFile) return false
        return audioPlayUrl(f.absolutePath)
    }

    fun audioStop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun audioTone(toneType: Int = ToneGenerator.TONE_PROP_BEEP, durationMs: Int = 200): Boolean = try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        tg.startTone(toneType, durationMs.coerceIn(50, 3000))
        mainHandler.postDelayed({
            try {
                tg.release()
            } catch (_: Exception) {
            }
        }, (durationMs + 100).toLong())
        true
    } catch (_: Exception) {
        false
    }


    fun batteryJson(): String {
        return try {
            val bm = appCtx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            JSONObject()
                .put("level", level)
                .put("charging", charging)
                .put("status", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS))
                .toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    fun hasPermission(permission: String): Boolean = try {
        ContextCompat.checkSelfPermission(appCtx, permission) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun hasFeature(feature: String): Boolean = try {
        appCtx.packageManager.hasSystemFeature(feature)
    } catch (_: Exception) {
        false
    }

    fun featuresJson(): String {
        val features = listOf(
            PackageManager.FEATURE_BLUETOOTH,
            PackageManager.FEATURE_BLUETOOTH_LE,
            PackageManager.FEATURE_WIFI,
            PackageManager.FEATURE_NFC,
            PackageManager.FEATURE_CONSUMER_IR,
            PackageManager.FEATURE_LOCATION,
            PackageManager.FEATURE_LOCATION_GPS,
            PackageManager.FEATURE_USB_HOST,
            PackageManager.FEATURE_CAMERA,
            PackageManager.FEATURE_MICROPHONE,
            PackageManager.FEATURE_TELEPHONY,
            PackageManager.FEATURE_SENSOR_ACCELEROMETER,
            PackageManager.FEATURE_SENSOR_GYROSCOPE,
            "android.hardware.vibrator",
        )
        val o = JSONObject()
        features.forEach { f ->
            o.put(f.removePrefix("android.hardware.").removePrefix("android.software."), hasFeature(f))
        }
        return o.toString()
    }

    fun deviceInfoExtendedJson(): String {
        return try {
            val pi = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("product", Build.PRODUCT)
                .put("board", Build.BOARD)
                .put("hardware", Build.HARDWARE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("release", Build.VERSION.RELEASE)
                .put("securityPatch", if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "")
                .put("package", appCtx.packageName)
                .put("versionName", pi.versionName)
                .put("versionCode", if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong())
                .put("abi", Build.SUPPORTED_ABIS.joinToString(","))
                .put("btName", btName())
                .put("btAddress", btAddress())
                .toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message).toString()
        }
    }

    fun connectivityDetailJson(): String {
        return try {
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            JSONObject()
                .put("hasInternet", caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                .put("validated", caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                .put("wifi", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
                .put("cellular", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                .put("vpn", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true)
                .put("ethernet", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true)
                .put("bluetooth", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH) == true)
                .put("downKbps", caps?.linkDownstreamBandwidthKbps ?: 0)
                .put("upKbps", caps?.linkUpstreamBandwidthKbps ?: 0)
                .toString()
        } catch (_: Exception) {
            "{}"
        }
    }



    @SuppressLint("MissingPermission")
    fun btSetName(name: String): Boolean = try {
        btAdapter?.name = name; true
    } catch (_: Exception) { false }

    @SuppressLint("MissingPermission")
    fun btEnable(): Boolean = try {
        @Suppress("DEPRECATION")
        btAdapter?.enable() == true
    } catch (_: Exception) { false }

    @SuppressLint("MissingPermission")
    fun btDisable(): Boolean = try {
        @Suppress("DEPRECATION")
        btAdapter?.disable() == true
    } catch (_: Exception) { false }

    @SuppressLint("MissingPermission")
    fun btCreateBond(address: String): Boolean {
        val device = try { btAdapter?.getRemoteDevice(address) } catch (_: Exception) { null } ?: return false
        return try { device.createBond() } catch (_: Exception) { false }
    }

    @SuppressLint("MissingPermission")
    fun btRemoveBond(address: String): Boolean {
        val device = try { btAdapter?.getRemoteDevice(address) } catch (_: Exception) { null } ?: return false
        return try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device) as? Boolean ?: false
        } catch (_: Exception) { false }
    }

    fun btGetBondState(address: String): Int {
        val device = try { btAdapter?.getRemoteDevice(address) } catch (_: Exception) { null } ?: return -1
        return try { device.bondState } catch (_: Exception) { -1 }
    }

    @SuppressLint("MissingPermission")
    fun btIsEnabled(): Boolean = try { btAdapter?.isEnabled == true } catch (_: Exception) { false }

    fun btScanMode(): Int = try {
        @Suppress("MissingPermission")
        btAdapter?.scanMode ?: -1
    } catch (_: Exception) { -1 }

    @SuppressLint("MissingPermission")
    fun btDeviceInfoJson(address: String): String {
        val device = try { btAdapter?.getRemoteDevice(address) } catch (_: Exception) { null }
            ?: return JSONObject().put("ok", false).toString()
        return try {
            JSONObject()
                .put("ok", true)
                .put("name", device.name ?: "")
                .put("address", device.address ?: "")
                .put("bondState", device.bondState)
                .put("type", device.type)
                .put("deviceClass", device.bluetoothClass?.deviceClass ?: -1)
                .put("majorClass", device.bluetoothClass?.majorDeviceClass ?: -1)
                .put("uuids", JSONArray().also { arr ->
                    device.uuids?.forEach { arr.put(it.uuid.toString()) }
                })
                .toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message).toString()
        }
    }


    @SuppressLint("MissingPermission")
    fun btConnectRfcomm(address: String, uuidStr: String, onEvent: (String) -> Unit): Boolean {
        btDisconnectRfcomm(address)
        val device = try { btAdapter?.getRemoteDevice(address) } catch (_: Exception) { null } ?: return false
        io.execute {
            try {
                val uuid = UUID.fromString(uuidStr)
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                try { @Suppress("DEPRECATION") btAdapter?.cancelDiscovery() } catch (_: Exception) {}
                socket.connect()
                rfcommMap[address.uppercase()] = socket
                mainHandler.post {
                    onEvent(JSONObject().put("event", "connected").put("address", address).toString())
                }
                io.execute {
                    try {
                        val input = socket.inputStream
                        val buf = ByteArray(4096)
                        while (socket.isConnected) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            val hex = bytesToHex(buf.copyOf(n))
                            val text = try { String(buf, 0, n, Charsets.UTF_8) } catch (_: Exception) { "" }
                            mainHandler.post {
                                onEvent(JSONObject()
                                    .put("event", "data")
                                    .put("hex", hex)
                                    .put("text", text)
                                    .put("size", n)
                                    .put("address", address)
                                    .toString())
                            }
                        }
                    } catch (_: Exception) {}
                    mainHandler.post {
                        onEvent(JSONObject().put("event", "disconnected").put("address", address).toString())
                    }
                    rfcommMap.remove(address.uppercase())
                }
            } catch (e: Exception) {
                mainHandler.post {
                    onEvent(JSONObject().put("event", "error").put("error", e.message).put("address", address).toString())
                }
            }
        }
        return true
    }

    fun btSendRfcomm(address: String, hex: String): Boolean {
        val socket = rfcommMap[address.uppercase()] ?: return false
        val bytes = hexToBytes(hex) ?: return false
        return try { socket.outputStream.write(bytes); socket.outputStream.flush(); true } catch (_: Exception) { false }
    }

    fun btSendRfcommText(address: String, text: String): Boolean {
        val socket = rfcommMap[address.uppercase()] ?: return false
        return try { socket.outputStream.write(text.toByteArray()); socket.outputStream.flush(); true } catch (_: Exception) { false }
    }

    fun btDisconnectRfcomm(address: String) {
        val socket = rfcommMap.remove(address.uppercase()) ?: return
        try { socket.close() } catch (_: Exception) {}
    }

    fun btRfcommConnectedJson(): String {
        val arr = JSONArray()
        rfcommMap.keys.forEach { arr.put(it) }
        return arr.toString()
    }


    @SuppressLint("MissingPermission")
    fun gattEnableNotifications(address: String, serviceUuid: String, charUuid: String, enable: Boolean = true): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        return try {
            val svc = gatt.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            gatt.setCharacteristicNotification(ch, enable)
            val cccUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val desc = ch.getDescriptor(cccUuid) ?: return true
            @Suppress("DEPRECATION")
            desc.value = if (enable) {
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(desc)
        } catch (e: Exception) { Log.w(TAG, "gattEnableNotifications", e); false }
    }

    @SuppressLint("MissingPermission")
    fun gattRequestMtu(address: String, mtu: Int): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        return try { gatt.requestMtu(mtu.coerceIn(23, 517)) } catch (_: Exception) { false }
    }

    @SuppressLint("MissingPermission")
    fun gattReadRssi(address: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        return try { gatt.readRemoteRssi() } catch (_: Exception) { false }
    }

    @SuppressLint("MissingPermission")
    fun gattSetPreferredPhy(address: String, txPhy: Int, rxPhy: Int, phyOptions: Int = 0): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val gatt = gattMap[address.uppercase()] ?: return false
        return try { gatt.setPreferredPhy(txPhy, rxPhy, phyOptions); true } catch (_: Exception) { false }
    }

    @SuppressLint("MissingPermission")
    fun gattDiscoverServices(address: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        return try { gatt.discoverServices() } catch (_: Exception) { false }
    }

    @SuppressLint("MissingPermission")
    fun gattServicesJson(address: String): String {
        val gatt = gattMap[address.uppercase()] ?: return "[]"
        val arr = JSONArray()
        gatt.services?.forEach { s ->
            val chars = JSONArray()
            s.characteristics?.forEach { c ->
                val descs = JSONArray()
                c.descriptors?.forEach { d -> descs.put(JSONObject().put("uuid", d.uuid.toString())) }
                chars.put(JSONObject()
                    .put("uuid", c.uuid.toString())
                    .put("props", c.properties)
                    .put("permissions", c.permissions)
                    .put("propsStr", charPropsStr(c.properties))
                    .put("descriptors", descs))
            }
            arr.put(JSONObject()
                .put("uuid", s.uuid.toString())
                .put("type", s.type)
                .put("characteristics", chars))
        }
        return arr.toString()
    }

    private fun charPropsStr(props: Int): String {
        val list = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) list += "READ"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) list += "WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) list += "WRITE_NR"
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) list += "NOTIFY"
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) list += "INDICATE"
        if (props and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) list += "BROADCAST"
        if (props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) list += "SIGNED_WRITE"
        return list.joinToString("|")
    }

    @SuppressLint("MissingPermission")
    fun gattWriteDescriptor(address: String, serviceUuid: String, charUuid: String, descUuid: String, hex: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        val bytes = hexToBytes(hex) ?: return false
        return try {
            val svc = gatt.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            val desc = ch.getDescriptor(UUID.fromString(descUuid)) ?: return false
            @Suppress("DEPRECATION") desc.value = bytes
            @Suppress("DEPRECATION") gatt.writeDescriptor(desc)
        } catch (e: Exception) { Log.w(TAG, "gattWriteDescriptor", e); false }
    }

    @SuppressLint("MissingPermission")
    fun gattReadDescriptor(address: String, serviceUuid: String, charUuid: String, descUuid: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        return try {
            val svc = gatt.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            val desc = ch.getDescriptor(UUID.fromString(descUuid)) ?: return false
            gatt.readDescriptor(desc)
        } catch (_: Exception) { false }
    }

    @SuppressLint("MissingPermission")
    fun gattWriteNoResponse(address: String, serviceUuid: String, charUuid: String, hex: String): Boolean {
        val gatt = gattMap[address.uppercase()] ?: return false
        val bytes = hexToBytes(hex) ?: return false
        return try {
            val svc = gatt.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            @Suppress("DEPRECATION") ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION") ch.value = bytes
            @Suppress("DEPRECATION") gatt.writeCharacteristic(ch)
        } catch (e: Exception) { Log.w(TAG, "gattWriteNoResponse", e); false }
    }


    @SuppressLint("MissingPermission")
    fun bleAdvertiseCustom(
        manufacturerId: Int?, manufacturerData: String?,
        serviceUuids: List<String>?, serviceDataUuid: String?, serviceData: String?,
        includeName: Boolean = false, includeTxPower: Boolean = false,
        connectable: Boolean = false,
        mode: Int = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
        txPower: Int = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
    ): Boolean {
        bleAdvertiseStop()
        val advertiser = btAdapter?.bluetoothLeAdvertiser ?: return false
        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(includeName)
            .setIncludeTxPowerLevel(includeTxPower)
        if (manufacturerId != null && manufacturerData != null) {
            dataBuilder.addManufacturerData(manufacturerId, hexToBytes(manufacturerData) ?: ByteArray(0))
        }
        serviceUuids?.forEach { u -> try { dataBuilder.addServiceUuid(ParcelUuid(UUID.fromString(u))) } catch (_: Exception) {} }
        if (serviceDataUuid != null && serviceData != null) {
            try { dataBuilder.addServiceData(ParcelUuid(UUID.fromString(serviceDataUuid)), hexToBytes(serviceData) ?: ByteArray(0)) } catch (_: Exception) {}
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(mode).setTxPowerLevel(txPower).setConnectable(connectable).setTimeout(0).build()
        val cb = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) { Log.w(TAG, "advertise custom fail $errorCode") }
        }
        advertiseCallback = cb
        return try { advertiser.startAdvertising(settings, dataBuilder.build(), cb); true
        } catch (e: Exception) { Log.w(TAG, "bleAdvertiseCustom", e); advertiseCallback = null; false }
    }


    @SuppressLint("MissingPermission")
    fun bleServerStart(servicesJson: String, onEvent: (String) -> Unit): Boolean {
        bleServerStop()
        val bm = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        gattServerCallback = onEvent
        val cb = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                val state = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> "connected"
                    BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
                    else -> "state_$newState"
                }
                mainHandler.post { onEvent(JSONObject()
                    .put("event", "connection").put("state", state)
                    .put("address", device.address).put("status", status).toString()) }
            }
            override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, ch: BluetoothGattCharacteristic) {
                mainHandler.post { onEvent(JSONObject()
                    .put("event", "readRequest").put("address", device.address)
                    .put("uuid", ch.uuid.toString()).put("requestId", requestId).put("offset", offset).toString()) }
                try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ch.value ?: ByteArray(0)) } catch (_: Exception) {}
            }
            override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, ch: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                mainHandler.post { onEvent(JSONObject()
                    .put("event", "writeRequest").put("address", device.address)
                    .put("uuid", ch.uuid.toString()).put("hex", bytesToHex(value ?: ByteArray(0)))
                    .put("requestId", requestId).toString()) }
                if (responseNeeded) try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) } catch (_: Exception) {}
            }
            override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, desc: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                mainHandler.post { onEvent(JSONObject()
                    .put("event", "descriptorWrite").put("address", device.address)
                    .put("uuid", desc.uuid.toString())
                    .put("charUuid", desc.characteristic?.uuid?.toString() ?: "")
                    .put("hex", bytesToHex(value ?: ByteArray(0))).toString()) }
                if (responseNeeded) try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) } catch (_: Exception) {}
            }
        }
        val server = bm.openGattServer(appCtx, cb) ?: return false
        gattServer = server
        try {
            val arr = JSONArray(servicesJson)
            for (i in 0 until arr.length()) {
                val sObj = arr.getJSONObject(i)
                val service = BluetoothGattService(UUID.fromString(sObj.getString("uuid")),
                    sObj.optInt("type", BluetoothGattService.SERVICE_TYPE_PRIMARY))
                val chars = sObj.optJSONArray("characteristics") ?: JSONArray()
                for (j in 0 until chars.length()) {
                    val cObj = chars.getJSONObject(j)
                    val props = cObj.optInt("properties",
                        BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY)
                    val perms = cObj.optInt("permissions",
                        BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE)
                    val ch = BluetoothGattCharacteristic(UUID.fromString(cObj.getString("uuid")), props, perms)
                    if (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                        ch.addDescriptor(BluetoothGattDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
                    }
                    service.addCharacteristic(ch)
                }
                server.addService(service)
            }
        } catch (e: Exception) { Log.w(TAG, "bleServerStart parse", e) }
        return true
    }

    @SuppressLint("MissingPermission")
    fun bleServerStop() {
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null; gattServerCallback = null
    }

    @SuppressLint("MissingPermission")
    fun bleServerNotify(address: String, serviceUuid: String, charUuid: String, hex: String): Boolean {
        val server = gattServer ?: return false
        val bytes = hexToBytes(hex) ?: return false
        val device = try { btAdapter?.getRemoteDevice(address) } catch (_: Exception) { null } ?: return false
        return try {
            val svc = server.getService(UUID.fromString(serviceUuid)) ?: return false
            val ch = svc.getCharacteristic(UUID.fromString(charUuid)) ?: return false
            @Suppress("DEPRECATION") ch.value = bytes
            @Suppress("DEPRECATION") server.notifyCharacteristicChanged(device, ch, false)
            true
        } catch (e: Exception) { Log.w(TAG, "bleServerNotify", e); false }
    }

    @SuppressLint("MissingPermission")
    fun bleServerSendResponse(address: String, requestId: Int, status: Int, offset: Int, hex: String?): Boolean {
        val server = gattServer ?: return false
        val device = try { btAdapter?.getRemoteDevice(address) } catch (_: Exception) { null } ?: return false
        return try { server.sendResponse(device, requestId, status, offset, if (hex != null) hexToBytes(hex) else null); true } catch (_: Exception) { false }
    }


    @SuppressLint("MissingPermission")
    fun wifiDetailedInfoJson(): String {
        val wm = wifiManager ?: return "{}"
        return try {
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            val dhcp = wm.dhcpInfo
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            JSONObject()
                .put("ssid", info?.ssid?.trim('"') ?: "")
                .put("bssid", info?.bssid ?: "")
                .put("rssi", info?.rssi ?: 0)
                .put("linkSpeed", info?.linkSpeed ?: 0)
                .put("frequency", if (Build.VERSION.SDK_INT >= 21) info?.frequency ?: 0 else 0)
                .put("channel", freqToChannel(info?.frequency ?: 0))
                .put("ip", intToIpLocal(info?.ipAddress ?: 0))
                .put("gateway", intToIpLocal(dhcp?.gateway ?: 0))
                .put("netmask", intToIpLocal(dhcp?.netmask ?: 0))
                .put("dns1", intToIpLocal(dhcp?.dns1 ?: 0))
                .put("dns2", intToIpLocal(dhcp?.dns2 ?: 0))
                .put("serverAddress", intToIpLocal(dhcp?.serverAddress ?: 0))
                .put("leaseDuration", dhcp?.leaseDuration ?: 0)
                .put("networkId", info?.networkId ?: -1)
                .put("isWifi", caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
                .put("downKbps", caps?.linkDownstreamBandwidthKbps ?: 0)
                .put("upKbps", caps?.linkUpstreamBandwidthKbps ?: 0)
                .put("macAddress", wifiMacAddress())
                .toString()
        } catch (e: Exception) { Log.w(TAG, "wifiDetailedInfoJson", e); "{}" }
    }

    fun wifiDhcpInfoJson(): String {
        val wm = wifiManager ?: return "{}"
        return try {
            val dhcp = wm.dhcpInfo ?: return "{}"
            JSONObject()
                .put("ip", intToIpLocal(dhcp.ipAddress))
                .put("gateway", intToIpLocal(dhcp.gateway))
                .put("netmask", intToIpLocal(dhcp.netmask))
                .put("dns1", intToIpLocal(dhcp.dns1))
                .put("dns2", intToIpLocal(dhcp.dns2))
                .put("serverAddress", intToIpLocal(dhcp.serverAddress))
                .put("leaseDuration", dhcp.leaseDuration)
                .toString()
        } catch (_: Exception) { "{}" }
    }

    @SuppressLint("MissingPermission")
    fun wifiChannelsJson(): String {
        val wm = wifiManager ?: return "[]"
        return try {
            @Suppress("DEPRECATION")
            val results = wm.scanResults.orEmpty()
            val channels = results.groupBy { freqToChannel(it.frequency) }
            val arr = JSONArray()
            channels.entries.sortedBy { it.key }.forEach { (ch, aps) ->
                val freq = aps.firstOrNull()?.frequency ?: 0
                arr.put(JSONObject()
                    .put("channel", ch)
                    .put("frequency", freq)
                    .put("band", if (freq < 3000) "2.4GHz" else if (freq < 6000) "5GHz" else "6GHz")
                    .put("networkCount", aps.size)
                    .put("strongestRssi", aps.maxOfOrNull { it.level } ?: -100)
                    .put("ssids", JSONArray(aps.mapNotNull { it.SSID?.takeIf { s -> s.isNotEmpty() } }.distinct())))
            }
            arr.toString()
        } catch (_: Exception) { "[]" }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    fun wifiMacAddress(): String = try {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        val wlan = ifaces.firstOrNull { it.name.startsWith("wlan") }
        if (wlan != null) {
            val mac = wlan.hardwareAddress
            if (mac != null && mac.isNotEmpty()) mac.joinToString(":") { "%02X".format(it) }
            else ""
        } else {
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo?.macAddress ?: ""
        }
    } catch (_: Exception) { "" }

    fun wifiIsEnabled(): Boolean = try { wifiManager?.isWifiEnabled == true } catch (_: Exception) { false }

    private fun freqToChannel(freq: Int): Int = when {
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        freq == 2484 -> 14
        freq in 5170..5825 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5955) / 5 + 1
        else -> 0
    }

    private fun intToIpLocal(value: Int): String {
        if (value == 0) return ""
        return "${value and 0xff}.${value shr 8 and 0xff}.${value shr 16 and 0xff}.${value shr 24 and 0xff}"
    }


    fun release() {
        btStopDiscovery()
        bleAdvertiseStop()
        bleServerStop()
        gattMap.keys.toList().forEach { gattDisconnect(it) }
        rfcommMap.keys.toList().forEach { btDisconnectRfcomm(it) }
        audioStop()
        nsdStop()
        sensorStopAll()
        try {
            io.shutdownNow()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "PluginAndroidApis"

        fun hexToBytes(hex: String): ByteArray? {
            val clean = hex.trim().removePrefix("0x").replace(" ", "").replace(":", "")
            if (clean.isEmpty() || clean.length % 2 != 0) return null
            return try {
                ByteArray(clean.length / 2) { i ->
                    clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } catch (_: Exception) {
                null
            }
        }

        fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02X".format(it) }
    }
}

