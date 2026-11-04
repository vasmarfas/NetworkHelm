package com.vasmarfas.networkhelm.ui.table

import com.vasmarfas.networkhelm.app.HiddenValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun <T> rowsAsTabs(columns: List<TableColumn<T>>, rows: List<T>): String =
    (listOf(columns.map { it.title }) + rows.map { row -> columns.map { it.shown(it.value(row)) } })
        .joinToString("\n") { line -> line.joinToString("\t") { cell -> cell.replace(BREAKS, " ") } }

fun <T> rowsAsCsv(columns: List<TableColumn<T>>, rows: List<T>): String =
    (listOf(columns.map { it.title }) + rows.map { row -> columns.map { raw(it, row) } })
        .joinToString("\n") { line -> line.joinToString(",", transform = ::csvField) }

fun <T> rowsAsJson(columns: List<TableColumn<T>>, rows: List<T>): String =
    PRETTY.encodeToString(JsonArray.serializer(), JsonArray(rows.map { row -> JsonObject(columns.associate { it.key to JsonPrimitive(raw(it, row)) }) }))

private val PRETTY = Json { prettyPrint = true }

private val BREAKS = Regex("[\t\r\n]+")

private fun <T> raw(column: TableColumn<T>, row: T): String = column.value(row).let { if (column.shown(it) == HiddenValue) HiddenValue else it }

private fun csvField(field: String) = if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
