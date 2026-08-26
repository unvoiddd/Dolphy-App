package com.droid.dolphy.plugin

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object PluginDexRegistry {
    private const val TAG = "PluginDexRegistry"
    private const val MAX_DEX_BYTES = 32 * 1024 * 1024

    data class Module(
        val pluginId: String,
        val name: String,
        val sha256: String,
        val classLoader: ClassLoader,
        val file: File?,
    )

    data class ClassExport(
        val pluginId: String,
        val name: String,
        val className: String,
        val moduleName: String?,
        val type: Class<*>,
    )

    data class ObjectExport(
        val pluginId: String,
        val name: String,
        val value: Any,
    )

    private val modules = ConcurrentHashMap<String, Module>()
    private val classes = ConcurrentHashMap<String, ClassExport>()
    private val objects = ConcurrentHashMap<String, ObjectExport>()

    fun loadBase64(
        context: Context,
        pluginId: String,
        moduleName: String,
        encoded: String,
        expectedSha256: String,
        dependencies: List<String>,
    ): ClassLoader {
        val compact = encoded.filterNot(Char::isWhitespace)
        val bytes = Base64.decode(compact, Base64.DEFAULT)
        return loadBytes(context, pluginId, moduleName, bytes, expectedSha256, dependencies)
    }

    fun loadFile(
        context: Context,
        pluginId: String,
        moduleName: String,
        source: File,
        expectedSha256: String,
        dependencies: List<String>,
    ): ClassLoader {
        if (!source.isFile) throw IllegalArgumentException("DEX/JAR/APK file not found")
        if (source.length() > MAX_DEX_BYTES) throw IllegalArgumentException("DEX/JAR/APK is larger than 32 MB")
        return loadBytes(context, pluginId, moduleName, source.readBytes(), expectedSha256, dependencies)
    }

    fun loadBytes(
        context: Context,
        pluginId: String,
        moduleName: String,
        bytes: ByteArray,
        expectedSha256: String,
        dependencies: List<String>,
    ): ClassLoader {
        if (bytes.isEmpty()) throw IllegalArgumentException("DEX/JAR/APK payload is empty")
        if (bytes.size > MAX_DEX_BYTES) throw IllegalArgumentException("DEX/JAR/APK is larger than 32 MB")
        val safePlugin = sanitize(pluginId)
        val safeModule = sanitize(moduleName).ifBlank { "main" }
        val digest = sha256(bytes)
        val expected = expectedSha256.trim().lowercase()
        if (expected.isNotEmpty() && expected != digest) throw SecurityException("DEX SHA-256 mismatch")
        val parent = dependencyParent(context.classLoader, dependencies)
        val rawDex = bytes.size >= 4 && bytes[0] == 'd'.code.toByte() && bytes[1] == 'e'.code.toByte() && bytes[2] == 'x'.code.toByte()
        val file: File?
        val loader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && rawDex) {
            file = null
            InMemoryDexClassLoader(ByteBuffer.wrap(bytes), parent)
        } else {
            val dir = File(context.codeCacheDir, "dolphy_plugin_dex/$safePlugin").apply { mkdirs() }
            val extension = if (rawDex) "dex" else "jar"
            val target = File(dir, "${safeModule}_${digest.take(16)}.$extension")
            val temporary = File(dir, ".${target.name}.${System.nanoTime()}.tmp")
            temporary.writeBytes(bytes)
            if (!temporary.setReadOnly()) {
                temporary.delete()
                throw SecurityException("Unable to make dynamic code read-only")
            }
            if (!temporary.renameTo(target)) {
                if (target.exists()) temporary.delete()
                else {
                    temporary.delete()
                    throw IllegalStateException("Unable to store dynamic code")
                }
            }
            target.setReadOnly()
            file = target
            DexClassLoader(target.absolutePath, dir.absolutePath, null, parent)
        }
        val module = Module(pluginId, safeModule, digest, loader, file)
        modules[moduleKey(pluginId, safeModule)] = module
        Log.i(TAG, "loaded $pluginId/$safeModule sha256=$digest")
        return loader
    }

    fun loader(pluginId: String, moduleName: String = "main"): ClassLoader? {
        return modules[moduleKey(pluginId, sanitize(moduleName).ifBlank { "main" })]?.classLoader
    }

    fun loadClass(pluginId: String, moduleName: String, className: String): Class<*> {
        val loader = loader(pluginId, moduleName) ?: throw IllegalStateException("DEX module is not loaded: $moduleName")
        return loader.loadClass(className.trim())
    }

    fun exportClass(
        pluginId: String,
        exportName: String,
        type: Class<*>,
        moduleName: String? = null,
    ): Boolean {
        val name = exportName.trim()
        if (name.isEmpty()) return false
        classes[name] = ClassExport(pluginId, name, type.name, moduleName, type)
        return true
    }

    fun exportObject(pluginId: String, exportName: String, value: Any): Boolean {
        val name = exportName.trim()
        if (name.isEmpty()) return false
        objects[name] = ObjectExport(pluginId, name, value)
        return true
    }

    fun importClass(exportName: String): Class<*>? = classes[exportName.trim()]?.type

    fun importObject(exportName: String): Any? = objects[exportName.trim()]?.value

    fun dependencyParent(appClassLoader: ClassLoader, dependencyIds: List<String>): ClassLoader {
        val delegates = dependencyIds
            .flatMap { dependency -> modules.values.filter { it.pluginId == dependency } }
            .sortedBy { it.name }
            .map { it.classLoader }
            .distinct()
        return if (delegates.isEmpty()) appClassLoader else DependencyClassLoader(appClassLoader, delegates)
    }

    fun listExportsJson(): String {
        val result = JSONObject()
        result.put("classes", JSONArray(classes.values.sortedBy { it.name }.map {
            JSONObject()
                .put("name", it.name)
                .put("pluginId", it.pluginId)
                .put("className", it.className)
                .put("module", it.moduleName)
        }))
        result.put("objects", JSONArray(objects.values.sortedBy { it.name }.map {
            JSONObject().put("name", it.name).put("pluginId", it.pluginId).put("className", it.value.javaClass.name)
        }))
        result.put("modules", JSONArray(modules.values.sortedWith(compareBy<Module> { it.pluginId }.thenBy { it.name }).map {
            JSONObject().put("pluginId", it.pluginId).put("name", it.name).put("sha256", it.sha256)
        }))
        return result.toString()
    }

    fun clearPlugin(pluginId: String) {
        modules.entries.removeIf { it.value.pluginId == pluginId }
        classes.entries.removeIf { it.value.pluginId == pluginId }
        objects.entries.removeIf { it.value.pluginId == pluginId }
    }

    fun clearAll() {
        modules.clear()
        classes.clear()
        objects.clear()
    }

    private fun moduleKey(pluginId: String, moduleName: String): String = "$pluginId::$moduleName"

    private fun sanitize(value: String): String = value.trim().replace(Regex("[^A-Za-z0-9_.-]"), "_").take(96)

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private class DependencyClassLoader(parent: ClassLoader, private val delegates: List<ClassLoader>) : ClassLoader(parent) {
        override fun findClass(name: String): Class<*> {
            delegates.forEach { loader ->
                runCatching { loader.loadClass(name) }.getOrNull()?.let { return it }
            }
            throw ClassNotFoundException(name)
        }
    }
}
