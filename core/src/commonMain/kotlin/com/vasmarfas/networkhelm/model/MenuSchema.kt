package com.vasmarfas.networkhelm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class NodeKind {
    Directory,
    List,
    Settings,
    Command,
}

@Serializable
class MenuSchema(
    val path: MenuPath,
    val kind: NodeKind,
    val children: List<String>,
    val commands: List<String>,
    val arguments: List<Argument>,
    val listens: Boolean = true,
    val prefill: Map<String, String> = emptyMap(),
    val lacks: Set<String> = emptySet(),
) {
    fun argument(name: String) = arguments.firstOrNull { it.name == name }
}

@Serializable
data class Argument(
    val name: String,
    val type: ValueType,
    val choices: List<String> = emptyList(),
    val description: String? = null,
    val readOnly: Boolean = false,
    val negatable: Boolean = false,
    val optional: Boolean = false,
    val label: String? = null,
    val tab: String? = null,
    val depends: List<Map<String, String>> = emptyList(),
    val default: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val fixed: Boolean = false,
    val placeholder: String? = null,
    val apart: Set<String> = emptySet(),
    // the choices stand as radio buttons in a row; editable: besides the choices the field takes a number that is typed
    val radio: Boolean = false,
    val editable: Boolean = false,
) {
    fun word(on: Boolean): String = when {
        type != ValueType.Flag -> if (on) "yes" else "no"
        choices.size == 2 -> if (on) choices[0] else choices[1]
        else -> if (on) "1" else "0"
    }
}

@Serializable
sealed interface ValueType {
    @Serializable
    @SerialName("text")
    data class Text(val maxLength: Int? = null) : ValueType

    @Serializable
    @SerialName("integer")
    data class Integer(val min: Long? = null, val max: Long? = null, val unit: String? = null, val scaled: Boolean = false) : ValueType {
        fun number(value: String): Long? {
            value.toLongOrNull()?.let { return it }
            if (!scaled) return null
            if (value.startsWith("0x")) return value.drop(2).toLongOrNull(16)
            val factor = when (value.lastOrNull()) {
                'k', 'K' -> 1_000L
                'M' -> 1_000_000L
                'G' -> 1_000_000_000L
                else -> return null
            }
            val base = value.dropLast(1).toLongOrNull() ?: return null
            return if (base > Long.MAX_VALUE / factor || base < Long.MIN_VALUE / factor) null else base * factor
        }
    }

    @Serializable
    @SerialName("ip4")
    data object Ip4 : ValueType

    @Serializable
    @SerialName("ip4-prefix")
    data object Ip4Prefix : ValueType

    @Serializable
    @SerialName("ip4-range")
    data object Ip4Range : ValueType

    @Serializable
    @SerialName("ip6")
    data object Ip6 : ValueType

    @Serializable
    @SerialName("ip6-prefix")
    data object Ip6Prefix : ValueType

    @Serializable
    @SerialName("ip")
    data object Ip : ValueType

    @Serializable
    @SerialName("mac")
    data object Mac : ValueType

    @Serializable
    @SerialName("decimal")
    data class Decimal(val min: Double? = null, val max: Double? = null, val unit: String? = null) : ValueType

    @Serializable
    @SerialName("hex")
    data object Hex : ValueType

    @Serializable
    @SerialName("route-distinguisher")
    data object RouteDistinguisher : ValueType

    @Serializable
    @SerialName("time")
    data object Time : ValueType

    @Serializable
    @SerialName("date")
    data object Date : ValueType

    @Serializable
    @SerialName("time-of-day")
    data object TimeOfDay : ValueType

    @Serializable
    @SerialName("offset")
    data object Offset : ValueType

    @Serializable
    @SerialName("date-time")
    data object DateTime : ValueType

    @Serializable
    @SerialName("yes-no")
    data object YesNo : ValueType

    @Serializable
    @SerialName("flag")
    data object Flag : ValueType {
        val on = setOf("1", "yes", "on", "true", "enabled")
    }

    // the router takes any value given, even no, as the flag set
    @Serializable
    @SerialName("switch")
    data object Switch : ValueType

    @Serializable
    @SerialName("port-list")
    data object PortList : ValueType

    @Serializable
    @SerialName("secret")
    data object Secret : ValueType

    @Serializable
    @SerialName("enumeration")
    data class Enumeration(val multiple: Boolean = false, val closed: Boolean = false) : ValueType

    @Serializable
    @SerialName("many")
    data class Many(val element: ValueType, val separator: String = ",", val negatable: Boolean = false) : ValueType

    @Serializable
    @SerialName("time-window")
    data class TimeWindow(val days: List<String>) : ValueType

    @Serializable
    @SerialName("rate")
    data class Rate(val modes: List<String>, val perFlow: Boolean = false) : ValueType

    @Serializable
    @SerialName("connection-limit")
    data class ConnectionLimit(val prefix: Int) : ValueType

    @Serializable
    @SerialName("classifier")
    data object Classifier : ValueType

    @Serializable
    @SerialName("icmp")
    data class Icmp(val six: Boolean = false) : ValueType

    @Serializable
    @SerialName("headers")
    data class Headers(val kinds: List<String>, val modes: List<String>) : ValueType

    @Serializable
    @SerialName("host")
    data class Host(val address: ValueType, val names: Boolean, val notation: String) : ValueType

    @Serializable
    @SerialName("span")
    data class Span(val element: Integer, val names: List<String>, val open: Boolean, val separator: String = "-") : ValueType

    @Serializable
    @SerialName("adjustment")
    data class Adjustment(val modes: List<String>, val element: Integer) : ValueType

    @Serializable
    @SerialName("unparsed")
    data class Unparsed(val notation: String) : ValueType
}
