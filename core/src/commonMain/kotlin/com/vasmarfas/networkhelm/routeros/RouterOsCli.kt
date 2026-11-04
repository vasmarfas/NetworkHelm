package com.vasmarfas.networkhelm.routeros

import com.vasmarfas.networkhelm.journal.EditKind
import com.vasmarfas.networkhelm.journal.EditRecord
import com.vasmarfas.networkhelm.model.MenuPath

fun EditRecord.toCli(): String = cli(path, kind, ids, after, moveBefore)

fun cli(path: MenuPath, kind: EditKind, ids: List<String>, values: Map<String, String?>, moveBefore: String? = null): String {
    val menu = path.cli
    val target = ids.joinToString(",").let { if (it.isEmpty()) "" else " $it" }
    return when (kind) {
        EditKind.Add -> "$menu add" + arguments(values)
        EditKind.Set -> buildList {
            val written = values.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
            if (written.isNotEmpty()) add("$menu set$target" + arguments(written))
            values.filterValues { it == null }.keys.forEach { add("$menu unset$target value-name=$it") }
        }.joinToString("\n")
        EditKind.Remove -> "$menu remove$target"
        EditKind.Move -> "$menu move$target" + (moveBefore?.let { " destination=$it" } ?: "")
    }
}

private fun arguments(values: Map<String, String?>) = values.entries.joinToString("") { (name, value) -> " $name=${cliValue(value.orEmpty())}" }

fun cliValue(value: String): String {
    if (value.isNotEmpty() && value.all { it.isLetterOrDigit() || it in PLAIN }) return value
    return buildString(value.length + 2) {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '$' -> append("\\$")
                '?' -> append("\\?")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}

private const val PLAIN = "._-:/,+*@!%"
