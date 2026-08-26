package com.droid.dolphy.plugin

import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object PluginReflection {
    fun findClass(classLoader: ClassLoader, className: String): Class<*>? {
        return runCatching { classLoader.loadClass(className.trim()) }.getOrNull()
    }

    fun getField(target: Any, fieldName: String): Any? {
        val type = targetType(target)
        val field = findField(type, fieldName) ?: throw NoSuchFieldException("${type.name}.$fieldName")
        field.isAccessible = true
        return field.get(receiver(target, field.modifiers))
    }

    fun setField(target: Any, fieldName: String, value: Any?): Boolean {
        val type = targetType(target)
        val field = findField(type, fieldName) ?: throw NoSuchFieldException("${type.name}.$fieldName")
        field.isAccessible = true
        field.set(receiver(target, field.modifiers), value)
        return true
    }

    fun invoke(target: Any, methodName: String, args: Array<Any?>): Any? {
        val type = targetType(target)
        val method = methods(type)
            .filter { it.name == methodName && it.parameterTypes.size == args.size }
            .firstOrNull { compatible(it.parameterTypes, args) }
            ?: throw NoSuchMethodException("${type.name}.$methodName/${args.size}")
        method.isAccessible = true
        return method.invoke(receiver(target, method.modifiers), *args)
    }

    fun newInstance(type: Class<*>, args: Array<Any?>): Any {
        val constructor = constructors(type)
            .filter { it.parameterTypes.size == args.size }
            .firstOrNull { compatible(it.parameterTypes, args) }
            ?: throw NoSuchMethodException("${type.name}.<init>/${args.size}")
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    fun membersJson(type: Class<*>): String {
        val result = JSONObject()
        result.put("className", type.name)
        result.put("fields", JSONArray(fields(type).map { field ->
            JSONObject()
                .put("name", field.name)
                .put("type", field.type.name)
                .put("static", Modifier.isStatic(field.modifiers))
                .put("declaredBy", field.declaringClass.name)
        }))
        result.put("methods", JSONArray(methods(type).map { method ->
            JSONObject()
                .put("name", method.name)
                .put("parameters", JSONArray(method.parameterTypes.map { it.name }))
                .put("returns", method.returnType.name)
                .put("static", Modifier.isStatic(method.modifiers))
                .put("declaredBy", method.declaringClass.name)
        }))
        result.put("constructors", JSONArray(constructors(type).map { constructor ->
            JSONObject().put("parameters", JSONArray(constructor.parameterTypes.map { it.name }))
        }))
        return result.toString()
    }

    private fun targetType(target: Any): Class<*> = if (target is Class<*>) target else target.javaClass

    private fun receiver(target: Any, modifiers: Int): Any? = if (Modifier.isStatic(modifiers)) null else target

    private fun findField(type: Class<*>, name: String): Field? = fields(type).firstOrNull { it.name == name }

    private fun fields(type: Class<*>): List<Field> {
        val result = LinkedHashMap<String, Field>()
        var current: Class<*>? = type
        while (current != null) {
            current.declaredFields.forEach { result.putIfAbsent(it.name, it) }
            current = current.superclass
        }
        return result.values.toList()
    }

    private fun methods(type: Class<*>): List<Method> {
        val result = LinkedHashMap<String, Method>()
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.forEach { method ->
                val key = method.name + method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name }
                result.putIfAbsent(key, method)
            }
            current = current.superclass
        }
        type.methods.forEach { method ->
            val key = method.name + method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name }
            result.putIfAbsent(key, method)
        }
        return result.values.toList()
    }

    private fun constructors(type: Class<*>): List<Constructor<*>> = type.declaredConstructors.toList()

    private fun compatible(types: Array<Class<*>>, args: Array<Any?>): Boolean {
        return types.indices.all { index ->
            val value = args[index]
            value == null && !types[index].isPrimitive || value != null && boxed(types[index]).isAssignableFrom(value.javaClass)
        }
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Void.TYPE -> java.lang.Void::class.java
        else -> type
    }
}
