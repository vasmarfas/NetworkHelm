package com.vasmarfas.networkhelm.vyos

import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.syslogLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun table(text: String): List<Map<String, String>> {
    val lines = text.lines()
    val rule = lines.indexOfFirst { line -> line.isNotBlank() && line.all { it == '-' || it == ' ' } }
    if (rule < 1) return emptyList()
    val starts = Regex("-+").findAll(lines[rule]).map { it.range.first }.toList()
    fun cells(line: String) = starts.mapIndexed { index, start ->
        val end = starts.getOrNull(index + 1)?.coerceAtMost(line.length) ?: line.length
        if (start >= end) "" else line.substring(start, end).trim()
    }
    val heading = lines[rule - 1]
    val names = cells(heading).map { it.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') }
    val rows = mutableListOf<MutableMap<String, String>>()
    for (line in lines.drop(rule + 1)) {
        if (line.isBlank() || line == heading || line.all { it == '-' || it == ' ' }) continue
        val values = cells(line)
        val above = rows.lastOrNull()
        if (values.first().isEmpty() && above != null) {
            for ((name, value) in names.zip(values)) if (value.isNotEmpty()) above[name] = listOfNotNull(above[name]?.ifEmpty { null }, value).joinToString(",")
        } else {
            rows += names.zip(values).toMap(LinkedHashMap())
        }
    }
    return rows.distinct()
}

internal fun resource(meminfo: String, uptime: String): Map<String, String> {
    val kibibytes = meminfo.lines().mapNotNull { Regex("""(\w+):\s+(\d+) kB""").matchEntire(it.trim())?.destructured }.associate { (name, size) -> name to size.toLong() * 1024 }
    val lines = uptime.lines().map { it.trim() }
    return listOfNotNull(
        lines.firstOrNull { it.startsWith("Uptime:") }?.let { "uptime" to it.removePrefix("Uptime:").replace(" ", "") },
        lines.firstOrNull { it.startsWith("1 ") }?.let { "load" to it.substringAfter(':').trim() },
        kibibytes["MemTotal"]?.let { "total-memory" to "$it" },
        (kibibytes["MemAvailable"] ?: kibibytes["MemFree"])?.let { "free-memory" to "$it" },
    ).toMap()
}

internal fun log(text: String): List<Item> {
    val seen = mutableMapOf<String, Int>()
    return text.lines().filter { it.isNotBlank() }.asReversed().map { line ->
        val count = (seen[line] ?: 0) + 1
        seen[line] = count
        Item("$line#$count", mapOf("text" to line) + syslogLine(line).orEmpty())
    }
}

internal fun routes(json: String): List<Item> {
    val prefixes = Json.parseToJsonElement(json) as? JsonObject ?: return emptyList()
    return prefixes.flatMap { (prefix, routes) ->
        (routes as? JsonArray).orEmpty().mapIndexedNotNull { index, element ->
            val route = element as? JsonObject ?: return@mapIndexedNotNull null
            fun text(of: JsonObject, name: String) = (of[name] as? JsonPrimitive)?.contentOrNull
            val hops = (route["nexthops"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val interfaces = hops.mapNotNull { text(it, "interfaceName") }.distinct()
            val protocol = text(route, "protocol").orEmpty()
            val values = listOfNotNull(
                "dst-address" to prefix,
                "protocol" to protocol,
                "gateway" to (hops.mapNotNull { text(it, "ip") }.ifEmpty { interfaces }).joinToString(","),
                "interface" to interfaces.joinToString(","),
                text(route, "distance")?.let { "distance" to it },
                text(route, "metric")?.let { "metric" to it },
                text(route, "uptime")?.let { "uptime" to it },
                text(route, "vrfName")?.let { "vrf" to it },
                "active" to (text(route, "selected") ?: "false"),
                "dynamic" to "${protocol != "static"}",
                "connect" to "${protocol == "connected"}",
                "local" to "${protocol == "local"}",
                "static" to "${protocol == "static"}",
            ).toMap()
            Item("$prefix $protocol $index", values)
        }
    }
}
