package com.vasmarfas.networkhelm.routeros

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.ValueType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private class KnownField(
    val type: String,
    val choices: List<String> = emptyList(),
    val multiple: Boolean = false,
    val negatable: Boolean = false,
    val readOnly: Boolean = false,
) {
    fun argument(name: String) = Argument(
        name,
        when (type) {
            "yesno" -> ValueType.YesNo
            "integer" -> ValueType.Integer(scaled = true)
            "time" -> ValueType.Time
            "ports" -> ValueType.PortList
            "enum" -> ValueType.Enumeration(multiple)
            else -> if (multiple) ValueType.Many(ValueType.Text()) else ValueType.Text()
        },
        choices,
        readOnly = readOnly,
        negatable = negatable && (type == "enum" || !multiple),
    )
}

private val known: Map<String, Map<String, KnownField>> by lazy { Json.decodeFromString(tik4netFields) }

internal fun knownArguments(path: MenuPath): Map<String, Argument> =
    known[path.toString()].orEmpty().mapValues { (name, field) -> field.argument(name) }

private val absent: Map<String, Map<String, List<String>>> by lazy { Json.decodeFromString(routerOs6Absent) }

internal fun absentNames(path: MenuPath, version: RouterOsVersion): Set<String> = absent.entries
    .mapNotNull { (measured, paths) -> RouterOsVersion.parse(measured)?.let { it to paths } }
    .filter { (measured, _) -> compareValuesBy(version, measured, { it.major }, { it.minor }) <= 0 }
    .minByOrNull { (measured, _) -> measured }?.second?.get(path.toString()).orEmpty().toSet()
