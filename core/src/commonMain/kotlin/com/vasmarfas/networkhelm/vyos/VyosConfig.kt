package com.vasmarfas.networkhelm.vyos

import com.vasmarfas.networkhelm.journal.EditKind
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.offline.Config
import com.vasmarfas.networkhelm.offline.ConfigMenu
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val NAME = ".name"

internal val lists = listOf(
    "interfaces/ethernet", "interfaces/bridge", "interfaces/bonding", "interfaces/wireguard", "interfaces/pppoe", "interfaces/dummy", "interfaces/loopback",
    "firewall/ipv4/forward/filter/rule", "firewall/ipv4/input/filter/rule", "firewall/ipv4/output/filter/rule",
    "firewall/group/address-group", "firewall/group/network-group", "firewall/group/port-group",
    "nat/source/rule", "nat/destination/rule",
    "protocols/static/route", "protocols/static/route6",
    "service/dhcp-server/shared-network-name",
    "system/login/user",
).map(MenuPath::parse)

internal val settings = listOf("system", "service/ssh", "service/dns/forwarding", "service/ntp").map(MenuPath::parse)

private val switchable = lists.filter { it.segments.first() != "system" && it.segments.getOrNull(1) != "group" }

private val known: Map<MenuPath, Map<String, ValueType>> = run {
    val text = ValueType.Text()
    val many = ValueType.Many(ValueType.Text())
    val flag = ValueType.YesNo
    val addresses = mapOf("description" to text, "address" to many)
    val rule = mapOf(
        "action" to text, "protocol" to text, "source address" to text, "destination address" to text, "destination port" to text,
        "state" to many, "description" to text,
    )
    val route = mapOf("next-hop" to many, "interface" to many, "blackhole" to flag, "description" to text)
    mapOf(
        "interfaces/ethernet" to addresses + mapOf("mtu" to text, "speed" to text, "duplex" to text, "vif" to many),
        "interfaces/bridge" to addresses + mapOf("member interface" to many, "vif" to many),
        "interfaces/bonding" to addresses + mapOf("mode" to text, "member interface" to many),
        "interfaces/wireguard" to addresses + mapOf("port" to text, "private-key" to ValueType.Secret, "peer" to many),
        "interfaces/pppoe" to mapOf("description" to text, "source-interface" to text, "authentication username" to text, "authentication password" to ValueType.Secret),
        "interfaces/dummy" to addresses,
        "interfaces/loopback" to addresses,
        "firewall/ipv4/forward/filter/rule" to rule,
        "firewall/ipv4/input/filter/rule" to rule,
        "firewall/ipv4/output/filter/rule" to rule,
        "firewall/group/address-group" to mapOf("address" to many, "description" to text),
        "firewall/group/network-group" to mapOf("network" to many, "description" to text),
        "firewall/group/port-group" to mapOf("port" to many, "description" to text),
        "nat/source/rule" to mapOf(
            "outbound-interface name" to text, "protocol" to text, "source address" to text, "destination address" to text,
            "translation address" to text, "description" to text,
        ),
        "nat/destination/rule" to mapOf(
            "inbound-interface name" to text, "protocol" to text, "destination address" to text, "destination port" to text,
            "translation address" to text, "translation port" to text, "description" to text,
        ),
        "protocols/static/route" to route,
        "protocols/static/route6" to route,
        "service/dhcp-server/shared-network-name" to mapOf("subnet" to many, "authoritative" to flag, "description" to text),
        "system/login/user" to mapOf("full-name" to text, "authentication plaintext-password" to ValueType.Secret),
    ).mapKeys { MenuPath.parse(it.key) }
}

private val tags = setOf("vif", "vif-s", "vif-c", "peer", "interface", "next-hop", "subnet", "static-mapping", "range", "server", "name-server", "rule")

private val secrets = setOf("encrypted-password", "plaintext-password", "password", "private-key", "preshared-key", "secret", "key")

internal fun items(path: MenuPath, node: JsonObject): List<Item> = if (path in lists) {
    node.mapNotNull { (name, element) ->
        (element as? JsonObject)?.let {
            val values = mapOf(NAME to name) + leaves(it).mapValues { (_, leaf) -> leaf.first }
            Item(name, if (path in switchable) values - "disable" + ("disabled" to "${it["disable"] is JsonObject}") else values)
        }
    }
} else {
    listOf(Item("", leaves(node).mapValues { it.value.first }))
}

internal fun arguments(path: MenuPath, nodes: List<JsonObject>): List<Argument> {
    val types = LinkedHashMap<String, ValueType>()
    if (path in lists) types[NAME] = ValueType.Text()
    if (path in switchable) types["disabled"] = ValueType.YesNo
    types += known[path].orEmpty()
    for (node in nodes) {
        for ((name, leaf) in leaves(node)) if (name !in types || leaf.second is ValueType.Many) types[name] = leaf.second
    }
    if (path in switchable) types.remove("disable")
    return types.map { (name, type) -> Argument(name, type) }
}

private fun leaves(node: JsonObject, prefix: String = ""): Map<String, Pair<String, ValueType>> = buildMap {
    for ((key, value) in node) {
        val name = prefix + key
        when {
            value is JsonObject && value.isEmpty() -> put(name, "true" to ValueType.YesNo)
            value is JsonObject && key in tags -> {
                put(name, value.keys.joinToString(",") to ValueType.Many(ValueType.Text()))
                for ((child, inner) in value) (inner as? JsonObject)?.let { putAll(leaves(it, "$name $child ")) }
            }
            value is JsonObject -> putAll(leaves(value, "$name "))
            value is JsonArray -> put(name, values(value).joinToString(",") to ValueType.Many(ValueType.Text()))
            else -> put(name, values(value).joinToString(",") to if (key in secrets) ValueType.Secret else ValueType.Text())
        }
    }
}

private enum class Kind { Single, Many, Flag }

// a changed value is deleted before it is set: showConfig gives a leaf that takes several values as plain
// while it holds one, and set alone would add to it
internal fun commands(base: List<String>, node: JsonObject?, changes: Map<String, String?>, types: Map<String, ValueType>): List<JsonObject> = buildList {
    for ((field, value) in changes) {
        val words = if (field == "disabled") listOf("disable") else field.split(' ')
        val now = words.fold<String, JsonElement?>(node) { at, word -> (at as? JsonObject)?.get(word) }
        val tag = now is JsonObject && now.isNotEmpty() && words.last() in tags
        if (now is JsonObject && now.isNotEmpty() && !tag) throw DeviceException(ErrorCategory.ArgumentValue, "$field is a node of VyOS, not a value")
        val kind = when {
            now is JsonArray || tag || types[field] is ValueType.Many -> Kind.Many
            now is JsonObject || now == null && types[field] == ValueType.YesNo -> Kind.Flag
            else -> Kind.Single
        }
        val path = base + words
        when (kind) {
            Kind.Flag -> {
                val on = value in ValueType.Flag.on
                if (on && now == null) add(command("set", path))
                if (!on && now != null) add(command("delete", path))
            }
            Kind.Many -> {
                val before = values(now)
                val after = value.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
                (before - after.toSet()).forEach { add(command("delete", path + it)) }
                (after - before.toSet()).forEach { add(command("set", path + it)) }
            }
            Kind.Single -> if (value != (now as? JsonPrimitive)?.contentOrNull) {
                if (now != null) add(command("delete", path))
                if (!value.isNullOrEmpty()) add(command("set", path + value))
            }
        }
    }
}

internal fun command(op: String, path: List<String>) = JsonObject(mapOf("op" to JsonPrimitive(op), "path" to JsonArray(path.map(::JsonPrimitive))))

private fun values(element: JsonElement?): List<String> = when (element) {
    is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonObject -> element.keys.toList()
    is JsonPrimitive -> listOfNotNull(element.contentOrNull)
    null -> emptyList()
}

internal fun configTree(commands: String): JsonObject {
    val root = Node()
    for (line in commands.lineSequence()) {
        val words = words(line)
        if (words.firstOrNull() != ("set" to false)) continue
        val valued = words.size > 2 && words.last().second
        val node = words.subList(1, if (valued) words.size - 1 else words.size).fold(root) { at, (word, _) -> at.children.getOrPut(word, ::Node) }
        if (valued) node.values += words.last().first
    }
    return JsonObject(root.children.mapValues { it.value.json() })
}

private class Node {
    val children = LinkedHashMap<String, Node>()
    val values = mutableListOf<String>()

    fun json(): JsonElement = when {
        children.isNotEmpty() || values.isEmpty() -> JsonObject(children.mapValues { it.value.json() })
        values.size == 1 -> JsonPrimitive(values.single())
        else -> JsonArray(values.map(::JsonPrimitive))
    }
}

private fun words(line: String): List<Pair<String, Boolean>> = buildList {
    var at = 0
    while (at < line.length) {
        val char = line[at]
        when {
            char.isWhitespace() -> at++
            char == '\'' || char == '"' -> {
                val end = line.indexOf(char, at + 1).let { if (it < 0) line.length else it }
                add(line.substring(at + 1, end) to true)
                at = end + 1
            }
            else -> {
                val end = (at until line.length).firstOrNull { line[it].isWhitespace() } ?: line.length
                add(line.substring(at, end) to false)
                at = end
            }
        }
    }
}

internal fun subtree(tree: JsonObject, path: MenuPath): JsonObject? = path.segments.fold<String, JsonObject?>(tree) { at, segment -> at?.get(segment) as? JsonObject }

fun vyosConfig(commands: String): Config {
    val tree = configTree(commands)
    return Config(
        (lists + settings).mapNotNull { path ->
            subtree(tree, path)?.let { path to ConfigMenu(if (path in lists) NodeKind.List else NodeKind.Settings, items(path, it)) }
        }.toMap(),
    )
}

fun vyosCli(path: MenuPath, kind: EditKind, ids: List<String>, values: Map<String, String?>): String {
    fun lines(base: List<String>) = (values - NAME).map { (field, value) ->
        val words = (base + if (field == "disabled") listOf("disable") else field.split(' ')).joinToString(" ")
        when {
            field == "disabled" -> if (value in ValueType.Flag.on) "set $words" else "delete $words"
            value.isNullOrEmpty() -> "delete $words"
            else -> "set $words ${quoted(value)}"
        }
    }
    val bases = if (path in lists) ids.map { path.segments + it } else listOf(path.segments)
    return when (kind) {
        EditKind.Add -> lines(path.segments + values[NAME].orEmpty()).ifEmpty { listOf("set " + (path.segments + values[NAME].orEmpty()).joinToString(" ")) }.joinToString("\n")
        EditKind.Set -> bases.flatMap(::lines).joinToString("\n")
        EditKind.Remove -> bases.joinToString("\n") { "delete " + it.joinToString(" ") }
        EditKind.Move -> "# $path: VyOS orders its rules by their numbers"
    }
}

private fun quoted(value: String) = if ('\'' in value) "\"$value\"" else "'$value'"
