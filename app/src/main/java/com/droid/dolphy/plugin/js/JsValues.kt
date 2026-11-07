package com.droid.dolphy.plugin.js

import com.droid.dolphy.plugin.model.UiNode
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

internal fun anyObjectMap(args: Array<Any?>, index: Int): Map<String, Any?> {
    if (index >= args.size) return emptyMap()
    val v = args[index]
    if (v is Scriptable && isPlainObject(v)) return scriptableToMap(v)
    return emptyMap()
}

internal fun isPlainObject(s: Scriptable): Boolean {
    if (s is org.mozilla.javascript.Function) return false
    if (s is NativeArray) return false

    val nodeId = ScriptableObject.getProperty(s, "__nodeId")
    if (nodeId != null && nodeId != Scriptable.NOT_FOUND && nodeId != Context.getUndefinedValue()) {
        return false
    }
    val uiNode = ScriptableObject.getProperty(s, "__uiNode")
    if (uiNode is UiNode) return false

    return ScriptableObject.getPropertyIds(s).isNotEmpty()
}

internal fun isProps(s: Scriptable): Boolean = isPlainObject(s)

internal fun scriptableToMap(s: Scriptable): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    for (id in ScriptableObject.getPropertyIds(s)) {
        val key = id.toString()
        map[key] = ScriptableObject.getProperty(s, key)
    }
    return map
}

internal fun f(args: Array<Any?>, key: String, def: Float): Float {
    val p = anyObjectMap(args, 0)
    return floatProp(p, key, def)
}

internal fun b(args: Array<Any?>, key: String, def: Boolean): Boolean {
    val p = anyObjectMap(args, 0)
    return boolProp(p, key, def)
}

internal fun str(args: Array<Any?>, i: Int, def: String): String {
    if (i >= args.size || args[i] == null || args[i] == Context.getUndefinedValue()) return def
    return Context.toString(args[i])
}

internal fun strPropOrNull(p: Map<String, Any?>, k: String, def: String?): String? {
    val v = p[k] ?: return def
    if (v == Context.getUndefinedValue()) return def
    return Context.toString(v)
}

internal fun strProp(p: Map<String, Any?>, k: String, def: String): String = strPropOrNull(p, k, def) ?: def

internal fun boolProp(p: Map<String, Any?>, k: String, def: Boolean): Boolean {
    val v = p[k] ?: return def
    if (v == Context.getUndefinedValue()) return def
    return Context.toBoolean(v)
}

internal fun floatProp(p: Map<String, Any?>, k: String, def: Float): Float {
    val v = p[k] ?: return def
    if (v == Context.getUndefinedValue()) return def
    return toFloat(v)
}

internal fun optionalFloat(p: Map<String, Any?>, k: String): Float? {
    val v = p[k] ?: return null
    if (v == Context.getUndefinedValue()) return null
    return toFloat(v)
}

internal fun intProp(p: Map<String, Any?>, k: String, def: Int): Int {
    val v = p[k] ?: return def
    if (v == Context.getUndefinedValue()) return def
    return toInt(v)
}

internal fun toFloat(v: Any): Float = when (v) {
    is Number -> v.toFloat()
    else -> Context.toNumber(v).toFloat()
}

internal fun toInt(v: Any): Int = when (v) {
    is Number -> v.toInt()
    else -> Context.toNumber(v).toInt()
}
