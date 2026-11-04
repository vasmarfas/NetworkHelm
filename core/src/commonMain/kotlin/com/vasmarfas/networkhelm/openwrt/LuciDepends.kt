package com.vasmarfas.networkhelm.openwrt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal enum class FileKind { Absent, File, Executable, Directory, EmptyDirectory, Other, Unknown }

internal fun satisfied(fs: JsonElement?, uci: JsonElement?, files: Map<String, FileKind>, sections: (String) -> List<UciSection>?): Boolean =
    alternatives(fs).let { it.isEmpty() || it.any { spec -> filesHold(spec, files) } } &&
        alternatives(uci).let { it.isEmpty() || it.any { spec -> configsHold(spec, sections) } }

internal fun conditionPaths(fs: JsonElement?): Set<String> = alternatives(fs).flatMap { it.keys }.toSet()

internal fun conditionConfigs(uci: JsonElement?): Set<String> = alternatives(uci).flatMap { it.keys }.toSet()

internal suspend fun fileKinds(ubus: Ubus, objects: Set<String>, paths: Collection<String>): Map<String, FileKind> {
    val shell = ubus as? SshUbus
    if (paths.isEmpty()) return emptyMap()
    if (shell != null) {
        val script = paths.joinToString("; ") { path ->
            val p = quote(path)
            "if [ -d $p ]; then if [ -n \"\$(ls -A $p 2>/dev/null)\" ]; then echo Directory; else echo EmptyDirectory; fi; " +
                "elif [ -f $p ]; then if [ -x $p ]; then echo Executable; else echo File; fi; elif [ -e $p ]; then echo Other; else echo Absent; fi"
        }
        val kinds = shell.sh(script).output.lines().filter { it.isNotBlank() }
        return paths.zip(kinds) { path, kind -> path to FileKind.valueOf(kind) }.toMap()
    }
    if ("file" !in objects) return paths.associateWith { FileKind.Unknown }
    return paths.associateWith { path -> statKind(ubus, path) }
}

private suspend fun statKind(ubus: Ubus, path: String): FileKind {
    val argument = JsonObject(mapOf("path" to JsonPrimitive(path)))
    val stat = try {
        ubus.call("file", "stat", argument)
    } catch (e: UbusException) {
        return if (e.status == UbusStatus.NOT_FOUND) FileKind.Absent else FileKind.Unknown
    }
    return when (stat["type"]?.jsonPrimitive?.contentOrNull) {
        "file" -> if ((stat["mode"]?.jsonPrimitive?.intOrNull ?: 0) and 0b001_000_000 != 0) FileKind.Executable else FileKind.File
        "directory" -> try {
            if (ubus.call("file", "list", argument)["entries"]?.jsonArray.isNullOrEmpty()) FileKind.EmptyDirectory else FileKind.Directory
        } catch (e: UbusException) {
            FileKind.Unknown
        }
        null -> FileKind.Unknown
        else -> FileKind.Other
    }
}

private fun alternatives(spec: JsonElement?): List<JsonObject> = when (spec) {
    is JsonObject -> listOf(spec)
    is JsonArray -> spec.mapNotNull { it as? JsonObject }
    else -> emptyList()
}

private fun filesHold(spec: JsonObject, files: Map<String, FileKind>) = spec.all { (path, kind) ->
    val found = files[path] ?: FileKind.Absent
    found == FileKind.Unknown || when ((kind as? JsonPrimitive)?.contentOrNull) {
        "directory" -> found == FileKind.Directory
        "executable" -> found == FileKind.Executable
        "file" -> found == FileKind.File || found == FileKind.Executable
        "absent" -> found == FileKind.Absent
        else -> true
    }
}

private fun configsHold(spec: JsonObject, sections: (String) -> List<UciSection>?) = spec.all { (config, wanted) ->
    val present = sections(config).orEmpty()
    when {
        wanted is JsonPrimitive && wanted.booleanOrNull == true -> present.isNotEmpty()
        wanted is JsonObject -> wanted.all { (name, options) ->
            val type = sectionType.matchEntire(name)?.groupValues?.get(1)
            present.any { (if (type != null) it.type == type else it.name == name) && sectionHolds(it, options) }
        }
        else -> true
    }
}

private fun sectionHolds(section: UciSection, options: JsonElement): Boolean = when {
    options is JsonPrimitive && options.isString -> section.type == options.content
    options is JsonPrimitive && options.booleanOrNull == true -> section.options.isNotEmpty() || section.lists.isNotEmpty()
    options is JsonObject -> options.all { (option, value) ->
        val list = section.lists[option]
        val primitive = value as? JsonPrimitive
        when {
            list != null -> primitive?.contentOrNull in list
            primitive?.booleanOrNull == true -> section.options[option] != null
            else -> section.options[option] == primitive?.contentOrNull
        }
    }
    else -> true
}

private val sectionType = Regex("^@([A-Za-z0-9_-]+)$")
