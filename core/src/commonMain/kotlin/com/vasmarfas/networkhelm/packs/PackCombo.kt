package com.vasmarfas.networkhelm.packs

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.session.DeviceSession
import kotlinx.serialization.Serializable

private const val MAX_COMBO_NUMBERS = 256

@Serializable
class PackCombo(val names: List<String> = emptyList(), val count: String? = null, val label: String = "#")

// the names first, then a number for each of the things the router counts, a line between the two
fun Argument.combo(combo: PackCombo, counted: Int?): Argument {
    val numbers = List((counted ?: 0).coerceIn(0, MAX_COMBO_NUMBERS)) { "$it" }
    return copy(
        type = ValueType.Enumeration(),
        choices = combo.names + numbers,
        labels = labels + numbers.associateWith { combo.label.replace("#", it) },
        apart = if (combo.names.isEmpty()) emptySet() else numbers.take(1).toSet(),
        editable = true,
    )
}

fun MenuSchema.withCombos(combos: Map<String, PackCombo>, counted: Map<String, Int>): MenuSchema = if (combos.isEmpty()) this else MenuSchema(
    path, kind, children, commands,
    arguments.map { argument -> combos[argument.name]?.let { argument.combo(it, counted[argument.name]) } ?: argument },
    listens, prefill, lacks,
)

// a combo without its numbers still has its names, so a router that does not tell the count costs the window nothing
suspend fun DeviceSession.comboCounts(combos: Map<String, PackCombo>): Map<String, Int> = combos.mapNotNull { (name, combo) ->
    val (path, property) = combo.count?.split(':', limit = 2)?.takeIf { it.size == 2 } ?: return@mapNotNull null
    val count = try {
        get(MenuPath.parse(path), null, listOf(property))?.get(property)?.toIntOrNull()
    } catch (e: DeviceException) {
        if (e.category == ErrorCategory.ConnectionLost) throw e
        null
    }
    count?.let { name to it }
}.toMap()
