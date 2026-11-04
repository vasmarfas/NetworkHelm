package com.vasmarfas.networkhelm.offline

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.routeros.api.ApiEncoding
import com.vasmarfas.networkhelm.routeros.knownArguments

fun parseExport(text: String): Config {
    val menus = linkedMapOf<MenuPath, MenuBuilder>()
    val facts = linkedMapOf<String, String>()
    var path: MenuPath? = null
    for (line in joined(text)) {
        if (line.startsWith("#")) {
            if (menus.isEmpty()) fact(line.removePrefix("#").trim())?.let { (name, value) -> facts.getOrPut(name) { value } }
            continue
        }
        var words = words(line)
        menu(words)?.let { (menu, rest) ->
            path = menu
            words = rest
        }
        val command = words.firstOrNull()?.takeIf { it == "add" || it == "set" || it == "remove" } ?: continue
        val menu = menus.getOrPut(path ?: continue) { MenuBuilder(knownArguments(path)) }
        val arguments = words.drop(1)
        val target = arguments.firstOrNull { '=' !in it || it.startsWith("[") }
        val values = values(arguments)
        when (command) {
            "add" -> menu.add(values)
            "set" -> if (target == null) menu.settings(values) else menu.set(selector(target), values)
            else -> if (target != null) menu.remove(selector(target))
        }
    }
    return Config(menus.mapValues { it.value.build() }, facts)
}

fun addsOf(text: String): List<Pair<MenuPath, Map<String, String>>> {
    val adds = mutableListOf<Pair<MenuPath, Map<String, String>>>()
    var path: MenuPath? = null
    for (line in joined(text)) {
        if (line.startsWith("#")) continue
        var words = words(line)
        menu(words)?.let { (menu, rest) ->
            path = menu
            words = rest
        }
        if (words.firstOrNull() == "add") adds += (path ?: continue) to values(words.drop(1))
    }
    return adds
}

private val COMMANDS = setOf("add", "set", "remove", "unset", "enable", "disable", "move")

private fun menu(words: List<String>): Pair<MenuPath, List<String>>? {
    if (words.firstOrNull()?.startsWith("/") != true) return null
    val end = words.indexOfFirst { it in COMMANDS || '=' in it || it.startsWith("[") }.takeIf { it >= 0 } ?: words.size
    return MenuPath(words.take(end).flatMap { it.split('/') }.filter { it.isNotEmpty() }) to words.drop(end)
}

private fun values(arguments: List<String>) = arguments.filter { '=' in it && !it.startsWith("[") }.associate { it.substringBefore('=') to it.substringAfter('=') }

private fun joined(text: String): List<String> {
    val lines = mutableListOf<String>()
    val current = StringBuilder()
    for (raw in text.lines()) {
        val line = if (current.isEmpty()) raw.trim() else raw.removePrefix("    ").trimEnd()
        val backslashes = line.length - line.trimEnd('\\').length
        if (backslashes % 2 == 1) {
            current.append(line, 0, line.length - 1)
        } else {
            lines += current.append(line).toString()
            current.clear()
        }
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines
}

private fun fact(comment: String): Pair<String, String>? {
    if (" by RouterOS " in comment) return "version" to comment.substringAfter(" by RouterOS ").trim()
    val equals = comment.indexOf(" = ")
    return if (equals > 0) comment.substring(0, equals).trim() to comment.substring(equals + 3).trim() else null
}

private fun words(line: String): List<String> {
    val words = mutableListOf<String>()
    var at = 0
    while (at < line.length) {
        if (line[at].isWhitespace()) {
            at++
        } else if (line[at] == '[') {
            val start = at
            var depth = 0
            var quoted = false
            while (at < line.length) {
                val char = line[at++]
                when {
                    quoted && char == '\\' -> at++
                    char == '"' -> quoted = !quoted
                    quoted -> {}
                    char == '[' -> depth++
                    char == ']' && --depth == 0 -> break
                }
            }
            words += line.substring(start, at.coerceAtMost(line.length))
        } else {
            val bytes = mutableListOf<Byte>()
            var quoted = false
            while (at < line.length && (quoted || !line[at].isWhitespace())) {
                val char = line[at++]
                when {
                    char == '"' -> quoted = !quoted
                    char == '\\' && at < line.length -> {
                        val next = line[at++]
                        val byte = if (at < line.length) "$next${line[at]}".takeIf { it.all(::isHexDigit) }?.toInt(16) else null
                        if (byte != null) {
                            bytes += byte.toByte()
                            at++
                        } else {
                            bytes += (escapes[next] ?: next).toString().encodeToByteArray().asList()
                        }
                    }
                    else -> bytes += char.toString().encodeToByteArray().asList()
                }
            }
            words += ApiEncoding.Utf8.decode(bytes.toByteArray())
        }
    }
    return words
}

private fun isHexDigit(char: Char) = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

private val escapes = mapOf('n' to '\n', 'r' to '\r', 't' to '\t', '_' to ' ', 'a' to '\u0007', 'b' to '\b', 'f' to '\u000C', 'v' to '\u000B')

private fun selector(target: String): Map<String, String> =
    if (target.startsWith("[")) {
        words(target.removePrefix("[").removeSuffix("]")).filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    } else {
        mapOf("name" to target)
    }

private class MenuBuilder(private val known: Map<String, Argument>) {
    private val items = mutableListOf<MutableMap<String, String>>()
    private var settings: MutableMap<String, String>? = null

    fun add(values: Map<String, String>) {
        items += api(values).toMutableMap()
    }

    fun settings(values: Map<String, String>) {
        (settings ?: mutableMapOf<String, String>().also { settings = it }).putAll(api(values))
    }

    fun set(selector: Map<String, String>, values: Map<String, String>) {
        val wanted = api(selector)
        val found = items.filter { item -> wanted.all { (name, value) -> item[name] == value } }
        if (found.isEmpty()) items += (wanted + api(values)).toMutableMap() else found.forEach { it.putAll(api(values)) }
    }

    fun remove(selector: Map<String, String>) {
        val wanted = api(selector)
        items.removeAll { item -> wanted.all { (name, value) -> item[name] == value } }
    }

    fun build(): ConfigMenu {
        val one = settings
        return if (one != null && items.isEmpty()) {
            ConfigMenu(NodeKind.Settings, listOf(Item("", one)))
        } else {
            ConfigMenu(NodeKind.List, items.mapIndexed { index, values -> Item("*${index + 1}", values) })
        }
    }

    private fun api(values: Map<String, String>) = values.mapValues { (name, value) ->
        when {
            name != "disabled" && known[name]?.type != ValueType.YesNo -> value
            value == "yes" -> "true"
            value == "no" -> "false"
            else -> value
        }
    }
}

fun exportDevice(config: Config, file: String) = DeviceInfo(
    Platform.RouterOs,
    config.menus[MenuPath.parse("system/identity")]?.items?.firstOrNull()?.get("name") ?: file.substringBeforeLast('.'),
    config.facts["version"].orEmpty(),
    "",
    config.facts["model"].orEmpty(),
    setOf("routeros"),
    setOf("read"),
)
