package com.droid.dolphy.hid

import kotlinx.coroutines.delay

sealed interface DuckyCommand {
    data class Delay(val ms: Long) : DuckyCommand
    data class TypeText(val text: String, val submit: Boolean) : DuckyCommand
    data class Press(val modifiers: List<String>, val key: String?) : DuckyCommand
}

object DuckyUtils {
    fun parse(raw: String): List<DuckyCommand> {
        var defaultDelay = 0L
        val result = mutableListOf<DuckyCommand>()

        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("REM", true) || line.startsWith("//")) return@forEach

            val upper = line.uppercase()
            when {
                upper.startsWith("DEFAULT_DELAY ") || upper.startsWith("DEFAULTDELAY ") -> {
                    defaultDelay = line.substringAfter(' ').trim().toLongOrNull() ?: 0L
                }
                upper.startsWith("DELAY ") -> {
                    result += DuckyCommand.Delay(line.substringAfter(' ').trim().toLongOrNull() ?: 0L)
                }
                upper.startsWith("STRINGLN ") -> {
                    result += DuckyCommand.TypeText(line.substringAfter(' '), submit = true)
                    if (defaultDelay > 0) result += DuckyCommand.Delay(defaultDelay)
                }
                upper.startsWith("STRING ") -> {
                    result += DuckyCommand.TypeText(line.substringAfter(' '), submit = false)
                    if (defaultDelay > 0) result += DuckyCommand.Delay(defaultDelay)
                }
                else -> {
                    val tokens = line.split(Regex("\\s+")).flatMap { token ->
                        token.split("+").flatMap { it.split("-") }.filter { it.isNotBlank() }
                    }
                    val mods = mutableListOf<String>()
                    var key: String? = null
                    tokens.forEach { token ->
                        val mapped = mapToken(token)
                        when {
                            mapped.startsWith("MOD_") -> mods += mapped
                            mapped.isNotBlank() -> key = mapped
                        }
                    }
                    if (mods.isNotEmpty() || key != null) {
                        result += DuckyCommand.Press(modifiers = mods, key = key)
                        if (defaultDelay > 0) result += DuckyCommand.Delay(defaultDelay)
                    }
                }
            }
        }
        return result
    }

    private fun mapToken(token: String): String {
        return when (token.uppercase()) {
            "CTRL", "CONTROL" -> "MOD_LCTRL"
            "SHIFT" -> "MOD_LSHIFT"
            "ALT" -> "MOD_LALT"
            "GUI", "WINDOWS", "WIN", "COMMAND" -> "MOD_LMETA"
            "ALTGR" -> "MOD_RALT"
            "MENU", "APP", "APPLICATION" -> "APPLICATION"
            "SPACE" -> "SPACE"
            "ENTER", "RETURN" -> "ENTER"
            "TAB" -> "TAB"
            "ESC", "ESCAPE" -> "ESC"
            "UPARROW", "UP" -> "UP"
            "DOWNARROW", "DOWN" -> "DOWN"
            "LEFTARROW", "LEFT" -> "LEFT"
            "RIGHTARROW", "RIGHT" -> "RIGHT"
            "DELETE", "DEL" -> "DELETE"
            "HOME" -> "HOME"
            "END" -> "END"
            "PAGEUP" -> "PAGEUP"
            "PAGEDOWN" -> "PAGEDOWN"
            "CAPSLOCK" -> "CAPSLOCK"
            "BACKSPACE" -> "BACKSPACE"
            else -> {
                val up = token.uppercase()
                when {
                    up.length == 1 && up[0].isLetterOrDigit() -> up
                    up in keyCodes.keys -> up
                    else -> ""
                }
            }
        }
    }

    suspend fun execute(connection: HidController, commands: List<DuckyCommand>, onProgress: (Float) -> Unit = {}) {
        val total = commands.size.toFloat()
        try {
            commands.forEachIndexed { index, command ->
                try {
                    when (command) {
                        is DuckyCommand.Delay -> delay(command.ms)
                        is DuckyCommand.TypeText -> {
                            typeText(connection, command.text)
                            if (command.submit) tapKey(connection, "ENTER")
                        }
                        is DuckyCommand.Press -> {
                            if (command.key != null) {
                                tapModifiersAndKey(connection, command.modifiers, command.key)
                            } else if (command.modifiers.isNotEmpty()) {

                                command.modifiers.forEach { connection.modifierDown(it) }
                                delay(20)
                                command.modifiers.forEach { connection.modifierUp(it) }
                            }
                        }
                    }
                } catch (e: SecurityException) {

                }
                onProgress((index + 1f) / total)
            }
        } finally {
            try {
                connection.releaseAllKeyboard()
            } catch (e: SecurityException) { }
        }
    }

    suspend fun typeText(connection: HidController, text: String) {
        text.forEach { ch ->
            try {
                when {
                    ch in 'a'..'z' -> tapKey(connection, ch.uppercaseChar().toString())
                    ch in 'A'..'Z' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), ch.toString())
                    ch in '0'..'9' -> tapKey(connection, ch.toString())
                    ch == ' ' -> tapKey(connection, "SPACE")
                    ch == '\n' -> tapKey(connection, "ENTER")
                    ch == '-' -> tapKey(connection, "MINUS")
                    ch == '_' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "MINUS")
                    ch == '=' -> tapKey(connection, "EQUAL")
                    ch == '+' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "EQUAL")
                    ch == '.' -> tapKey(connection, "DOT")
                    ch == ',' -> tapKey(connection, "COMMA")
                    ch == '/' -> tapKey(connection, "SLASH")
                    ch == ':' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "SEMICOLON")
                    ch == ';' -> tapKey(connection, "SEMICOLON")
                    ch == '\\' -> tapKey(connection, "BACKSLASH")
                    ch == '"' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "APOSTROPHE")
                    ch == '\'' -> tapKey(connection, "APOSTROPHE")
                    ch == '(' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "9")
                    ch == ')' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "0")
                    ch == '!' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "1")
                    ch == '?' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "SLASH")
                    ch == '@' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "2")
                    ch == '#' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "3")
                    ch == '$' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "4")
                    ch == '%' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "5")
                    ch == '^' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "6")
                    ch == '&' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "7")
                    ch == '*' -> tapModifiersAndKey(connection, listOf("MOD_LSHIFT"), "8")
                    else -> Unit
                }
            } catch (e: SecurityException) {

            }
            delay(10)
        }
    }

    suspend fun tapKey(connection: HidController, key: String) {
        if (!keyCodes.containsKey(key)) return
        try {
            connection.keyDown(key)
            delay(15)
            connection.keyUp(key)
            delay(15)
        } catch (e: SecurityException) { }
    }

    suspend fun tapModifiersAndKey(connection: HidController, modifiers: List<String>, key: String) {
        try {
            modifiers.forEach { if (keyCodes.containsKey(it)) connection.modifierDown(it) }
            tapKey(connection, key)
            modifiers.reversed().forEach { if (keyCodes.containsKey(it)) connection.modifierUp(it) }
        } catch (e: SecurityException) { }
    }

    suspend fun type(connection: HidController, text: String) {
        typeText(connection, text)
    }

}

