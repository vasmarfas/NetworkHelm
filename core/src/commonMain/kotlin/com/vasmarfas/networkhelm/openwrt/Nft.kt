package com.vasmarfas.networkhelm.openwrt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun nftRule(expressions: List<JsonElement>): String = expressions.mapNotNull { statement(it as? JsonObject ?: return@mapNotNull null) }.joinToString(" ")

private fun statement(entry: JsonObject): String? {
    val (name, body) = entry.entries.singleOrNull() ?: return null
    val fields = body as? JsonObject
    return when (name) {
        "counter" -> null
        "match" -> match(fields ?: return name)
        "jump", "goto" -> "$name ${fields?.get("target")?.text()}"
        "reject" -> listOfNotNull("reject", fields?.get("type")?.text()?.let { "with $it" }, fields?.get("expr")?.let(::expression)).joinToString(" ")
        "limit" -> "limit rate ${fields?.get("rate")?.text()}/${fields?.get("per")?.text()}" + (fields?.get("burst")?.text()?.let { " burst $it packets" } ?: "")
        "vmap" -> "${expression(fields?.get("key") ?: JsonNull)} vmap ${expression(fields?.get("data") ?: JsonNull)}"
        "mangle" -> "${expression(fields?.get("key") ?: JsonNull)} set ${expression(fields?.get("value") ?: JsonNull)}"
        "snat", "dnat" -> listOfNotNull(name, "to", listOfNotNull(fields?.get("addr")?.let(::expression), fields?.get("port")?.let(::expression)).joinToString(":")).joinToString(" ")
        "redirect" -> "redirect" + (fields?.get("port")?.let { " to :${expression(it)}" } ?: "")
        "log" -> "log" + (fields?.get("prefix")?.text()?.let { " prefix \"$it\"" } ?: "")
        else -> name
    }
}

private fun match(fields: JsonObject): String {
    val left = fields["left"] ?: JsonNull
    val quoted = ((left as? JsonObject)?.get("meta") as? JsonObject)?.get("key")?.text() in interfaceKeys
    val right = fields["right"]?.let { if (quoted && it is JsonPrimitive && it.isString) "\"${it.content}\"" else expression(it) }
    // nft leaves == out, unless what it compares is worked out with & or |
    val worked = (left as? JsonObject)?.keys?.singleOrNull() in setOf("&", "|", "^")
    val op = fields["op"]?.text()?.takeIf { it != "in" && (it != "==" || worked) }
    return listOfNotNull(expression(left), op, right).joinToString(" ")
}

private fun expression(value: JsonElement): String = when (value) {
    is JsonPrimitive -> value.contentOrNull.orEmpty()
    is JsonArray -> value.joinToString(",") { expression(it) }
    is JsonObject -> {
        val (name, body) = value.entries.singleOrNull() ?: return value.toString()
        val fields = body as? JsonObject
        when (name) {
            "payload" -> "${fields?.get("protocol")?.text()} ${fields?.get("field")?.text()}"
            "meta" -> fields?.get("key")?.text()?.let { if (it in interfaceKeys) it else "meta $it" }.orEmpty()
            "ct", "rt" -> "$name ${fields?.get("key")?.text()}"
            "tcp option" -> "tcp option ${fields?.get("name")?.text()} ${fields?.get("field")?.text()}"
            "set" -> "{ " + (body as? JsonArray ?: JsonArray(listOf(body))).joinToString(", ") { element ->
                (element as? JsonArray)?.let { pair -> "${expression(pair[0])} : ${(pair.getOrNull(1) as? JsonObject)?.let(::statement) ?: expression(pair.getOrNull(1) ?: JsonNull)}" } ?: expression(element)
            } + " }"
            "prefix" -> "${fields?.get("addr")?.let(::expression)}/${fields?.get("len")?.text()}"
            "range" -> (body as? JsonArray)?.joinToString("-") { expression(it) }.orEmpty()
            "concat" -> (body as? JsonArray)?.joinToString(" . ") { expression(it) }.orEmpty()
            "&", "|", "^" -> (body as? JsonArray)?.let { operands ->
                val inner = operands.joinToString(" $name ") { expression(it) }
                if (name == "|") "($inner)" else inner
            }.orEmpty()
            else -> name
        }
    }
}

private fun JsonElement.text() = (this as? JsonPrimitive)?.contentOrNull

private val interfaceKeys = setOf("iif", "oif", "iifname", "oifname")
