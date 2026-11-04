package com.vasmarfas.networkhelm.demo

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Capabilities
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import kotlinx.coroutines.flow.Flow

class Rows internal constructor(private val ids: (Int) -> String) {
    internal val items = mutableListOf<Item>()

    fun row(vararg fields: String) {
        val values = fields.associate { it.substringBefore('=') to it.substringAfter('=') }
        items += Item(values[".id"] ?: values[".name"] ?: ids(items.size + 1), values - ".id")
    }
}

class DemoBuilder internal constructor(private val ids: (Int) -> String) {
    internal val menus = LinkedHashMap<MenuPath, DemoMenu>()

    fun list(
        path: String,
        ordered: Boolean = false,
        writable: Boolean = true,
        fields: List<Argument> = emptyList(),
        live: Live? = null,
        rows: Rows.() -> Unit,
    ) {
        menus[MenuPath.parse(path)] = DemoMenu(NodeKind.List, Rows(ids).apply(rows).items, fields, writable, ordered, live, null)
    }

    fun settings(path: String, vararg values: String, writable: Boolean = true, fields: List<Argument> = emptyList(), live: Live? = null) {
        val row = Rows(ids).apply { row(*values) }.items.single()
        menus[MenuPath.parse(path)] = DemoMenu(NodeKind.Settings, listOf(Item("", row.values)), fields, writable, false, live, null)
    }

    fun command(path: String, vararg fields: Argument, run: (Map<String, String>) -> Flow<Item>) {
        menus[MenuPath.parse(path)] = DemoMenu(NodeKind.Command, emptyList(), fields.toList(), false, false, null, run)
    }
}

internal fun demoSession(device: DeviceInfo, capabilities: Capabilities, ids: (Int) -> String = { "$it" }, content: DemoBuilder.() -> Unit): DemoSession =
    DemoSession(device, capabilities, DemoBuilder(ids).apply(content).menus, ids)

internal fun choice(name: String, vararg values: String) = Argument(name, ValueType.Enumeration(closed = true), values.toList())

internal fun flags(vararg names: String) = names.map { Argument(it, ValueType.Flag) }

internal fun text(name: String) = Argument(name, ValueType.Text())
