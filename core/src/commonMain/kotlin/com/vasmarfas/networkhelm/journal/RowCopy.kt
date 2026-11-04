package com.vasmarfas.networkhelm.journal

import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.Platform

fun addValues(schema: MenuSchema, row: Item): Map<String, String> = row.values.filter { (name, value) ->
    val argument = schema.argument(name)
    argument != null && !argument.readOnly && value.isNotEmpty() && value != argument.default && !(name == "disabled" && value in OFF)
}

private val OFF = setOf("false", "no", "0")

fun addCommands(platform: Platform, path: MenuPath, schema: MenuSchema, rows: List<Item>): String {
    if ("add" !in schema.commands) return ""
    val commands = rows.filter { it["dynamic"] != "true" }.map { command(platform, path, EditKind.Add, emptyList(), addValues(schema, it)) }
    val written = platform == Platform.OpenWrt && commands.any { it.startsWith("uci ") }
    return (commands + listOfNotNull("uci commit ${path.segments.first()}".takeIf { written })).joinToString("\n")
}

fun addScript(platform: Platform, path: MenuPath, schema: MenuSchema, rows: List<Item>): String {
    val commands = addCommands(platform, path, schema, rows)
    if (commands.isEmpty() || platform != Platform.RouterOs && platform != Platform.Ssh) return commands
    return (listOf(path.cli) + commands.lines().map { it.removePrefix(path.cli + " ") }).joinToString("\n")
}
