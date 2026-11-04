package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.journal.EditKind
import com.vasmarfas.networkhelm.model.MenuPath

fun uciCli(path: MenuPath, kind: EditKind, ids: List<String>, values: Map<String, String?>, moveBefore: String? = null): String {
    val segments = path.segments
    if (segments.size != 2 || segments[0] in outside) {
        return "# $path ${kind.name.lowercase()} " + (ids + values.map { (name, value) -> "$name=${value.orEmpty()}" }).joinToString(" ")
    }
    val (config, type) = segments
    fun assign(section: String, written: Map<String, String?>) = written.filterKeys { it != NAME }.flatMap { (option, value) ->
        when {
            value == null -> listOf("uci delete $section.$option")
            LIST in value -> listOf("uci -q delete $section.$option") + value.split(LIST).map { "uci add_list $section.$option=${quote(it)}" }
            else -> listOf("uci set $section.$option=${quote(value)}")
        }
    }
    return when (kind) {
        EditKind.Add -> values[NAME]?.let { name -> listOf("uci set $config.$name=$type") + assign("$config.$name", values) }
            ?: (listOf("uci add $config $type") + assign("$config.@$type[-1]", values))
        EditKind.Set -> ids.ifEmpty { listOf(null) }.flatMap { id -> assign(id?.let { "$config.$it" } ?: "$config.@$type[0]", values) }
        EditKind.Remove -> ids.map { "uci delete $config.$it" }
        EditKind.Move -> ids.map { id -> "# $config.$id moved before " + (moveBefore?.let { before -> "$config.$before" } ?: "the end") }
    }.joinToString("\n")
}

private const val NAME = ".name"
private val outside = setOf("status", "tool")
