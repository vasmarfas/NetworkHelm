package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.ValueType

fun uciArgument(name: String, datatype: String): Argument {
    val inner = negated(datatype)
    val (type, choices) = type(inner ?: datatype)
    return Argument(name, type, choices, negatable = inner != null)
}

internal fun uciSwitch(name: String, values: List<String>): Argument? =
    uciArgument(name, "bool").takeIf { name in switches && values.all { it == "0" || it == "1" } }

private val switches = setOf("enable", "enabled", "disabled")

private fun type(datatype: String): Pair<ValueType, List<String>> {
    val name = datatype.substringBefore('(').trim()
    val args = arguments(datatype)
    val numbers = args.map { it.toLongOrNull() }
    val type = when (name) {
        "bool" -> ValueType.Flag
        "integer" -> ValueType.Integer()
        "uinteger" -> ValueType.Integer(min = 0)
        "float" -> ValueType.Decimal()
        "ufloat" -> ValueType.Decimal(min = 0.0)
        "port" -> ValueType.Integer(0, 65535)
        "range" -> if (numbers.size == 2 && numbers.all { it != null }) ValueType.Integer(numbers[0], numbers[1]) else null
        "min" -> numbers.singleOrNull()?.let { ValueType.Integer(min = it) }
        "max" -> numbers.singleOrNull()?.let { ValueType.Integer(max = it) }
        "ip4prefix" -> ValueType.Integer(0, 32)
        "ip6prefix" -> ValueType.Integer(0, 128)
        "ip4addr", "netmask4" -> ValueType.Ip4
        "ip6addr", "ip6ll", "ip6ula", "netmask6", "iprange6" -> ValueType.Ip6
        "cidr4", "ipnet4", "ipmask4" -> ValueType.Ip4Prefix
        "cidr6", "ipnet6", "ipmask6" -> ValueType.Ip6Prefix
        "iprange4" -> ValueType.Ip4Range
        "ipaddr", "cidr", "ipmask", "iprange" -> ValueType.Ip
        "macaddr" -> ValueType.Mac
        "portrange" -> ValueType.PortList
        "hexstring" -> ValueType.Hex
        "timehhmmss" -> ValueType.TimeOfDay
        "dateyyyymmdd" -> ValueType.Date
        "maxlength", "length" -> ValueType.Text(numbers.singleOrNull()?.toInt())
        "rangelength" -> ValueType.Text(numbers.getOrNull(1)?.toInt())
        "string", "hostname", "host", "hostport", "ipaddrport", "ip4addrport", "ip6addrport", "file", "directory", "device", "uciname",
        "ucifw4zonename", "netdevname", "network", "ip6hostid", "wpakey", "wepkey", "base64", "minlength", "phonedigit" -> ValueType.Text()
        "unique" -> args.singleOrNull()?.let { return type(it) } ?: ValueType.Text()
        "list" -> args.singleOrNull()?.let { element ->
            val inner = negated(element)
            type(inner ?: element).let { (type, choices) -> return ValueType.Many(type, LIST, inner != null) to choices }
        }
        "or" -> if (args.isNotEmpty()) {
            val types = args.filterNot(::literal).map { type(it).first }.distinct()
            return when {
                types.isEmpty() -> ValueType.Enumeration()
                types.size == 1 -> types.single()
                else -> ValueType.Text()
            } to args.filter(::literal).map { it.trim('"', '\'') }
        } else null
        "and" -> args.map { type(it).first }.let { types ->
            val ranges = types.filterIsInstance<ValueType.Integer>()
            val typed = types.filter { it !is ValueType.Text }.distinct()
            when {
                ranges.isNotEmpty() && ranges.size == types.size -> ValueType.Integer(ranges.mapNotNull { it.min }.maxOrNull(), ranges.mapNotNull { it.max }.minOrNull())
                typed.isEmpty() -> ValueType.Text(types.mapNotNull { (it as? ValueType.Text)?.maxLength }.minOrNull())
                typed.size == 1 && typed.single() !is ValueType.Unparsed -> typed.single()
                else -> null
            }
        }
        else -> null
    }
    return (type ?: ValueType.Unparsed(datatype)) to emptyList()
}

private fun negated(datatype: String) = datatype.trim().takeIf { it.startsWith("neg(") }?.let { arguments(it).singleOrNull() }

private fun arguments(datatype: String): List<String> {
    if ('(' !in datatype) return emptyList()
    val inner = datatype.substringAfter('(').substringBeforeLast(')')
    val args = mutableListOf<String>()
    var depth = 0
    var start = 0
    inner.forEachIndexed { index, char ->
        when (char) {
            '(' -> depth++
            ')' -> depth--
            ',' -> if (depth == 0) {
                args += inner.substring(start, index).trim()
                start = index + 1
            }
        }
    }
    args += inner.substring(start).trim()
    return args.filter { it.isNotEmpty() }
}

const val LIST = "\n"

private fun literal(arg: String) = arg.startsWith('"') || arg.startsWith('\'') || ('(' !in arg && arg !in datatypes)

private val datatypes = setOf(
    "integer", "uinteger", "float", "ufloat", "bool", "string", "hostname", "host", "network", "ipaddr", "ip4addr", "ip6addr", "ip6ll",
    "ip6ula", "cidr", "cidr4", "cidr6", "ipnet4", "ipnet6", "ipmask", "ipmask4", "ipmask6", "iprange", "iprange4", "iprange6", "netmask4",
    "netmask6", "ip4prefix", "ip6prefix", "ip6hostid", "port", "portrange", "macaddr", "file", "directory", "device", "uciname",
    "ucifw4zonename", "netdevname", "wpakey", "wepkey", "hexstring", "base64", "hostport", "ipaddrport", "ip4addrport", "ip6addrport",
    "phonedigit", "timehhmmss", "dateyyyymmdd", "unique",
)
