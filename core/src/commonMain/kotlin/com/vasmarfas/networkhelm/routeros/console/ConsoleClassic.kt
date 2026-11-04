package com.vasmarfas.networkhelm.routeros.console

import com.vasmarfas.networkhelm.model.duration

internal const val PRELUDE = ":global rdrow do={ :foreach k,v in=\$1 do={ :put (\"\\01\" . \$k . \"\\02\" . [:typeof \$v] . \"\\02\" . [:tostr \$v]) }; :put \"\\03\" }"

private const val PROPERTY = '\u0001'
private const val TYPE = '\u0002'
private const val END = "\u0003"
private val TIME = Regex("(?:(\\d+)w)?(?:(\\d+)d)?(\\d\\d):(\\d\\d):(\\d\\d)(?:\\.\\d+)?")

internal class Records(val rows: List<Map<String, String>>, val flags: Set<String>)

internal fun records(text: String): Records {
    val rows = ArrayList<Map<String, String>>()
    val flags = HashSet<String>()
    var row = LinkedHashMap<String, String>()
    var name: String? = null
    var type = ""
    val value = StringBuilder()
    fun close() {
        name?.let { property -> convert(type, value.toString())?.let { row[property] = it } }
        name = null
    }
    for (line in text.split('\n')) {
        when {
            line.startsWith(PROPERTY) -> {
                close()
                val (property, kind, first) = line.substring(1).split(TYPE, limit = 3).let { Triple(it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
                name = property
                type = kind
                if (kind == "bool") flags += property
                value.clear().append(first)
            }
            line == END -> {
                close()
                rows += row
                row = LinkedHashMap()
            }
            name != null -> value.append('\n').append(line)
        }
    }
    return Records(rows, flags)
}

private fun convert(type: String, value: String): String? = when (type) {
    "nil", "nothing" -> null
    "array" -> value.split(';').joinToString(",") { unmapped(it) }
    "time" -> TIME.matchEntire(value)?.let { time ->
        val (weeks, days, hours, minutes, seconds) = time.destructured
        duration((weeks.toLongOrNull() ?: 0) * 604_800 + (days.toLongOrNull() ?: 0) * 86_400 + hours.toLong() * 3_600 + minutes.toLong() * 60 + seconds.toLong())
    } ?: value
    else -> unmapped(value)
}

// on RouterOS 6 a count of a menu without a list is a syntax error, and of a non-menu a bad command name
internal fun probed(message: String): String = when {
    message.startsWith("expected end of command") || message.startsWith("bad parameter count-only") -> "unknown parameter"
    message.startsWith("bad parameter print") -> "no such command"
    message.startsWith("bad command name") -> "no such command or directory (${message.removePrefix("bad command name ").substringBefore(' ')})"
    else -> message
}
