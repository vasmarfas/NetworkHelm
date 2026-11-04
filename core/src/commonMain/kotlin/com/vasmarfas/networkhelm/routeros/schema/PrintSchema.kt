package com.vasmarfas.networkhelm.routeros.schema

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.routeros.api.ApiTrapException
import com.vasmarfas.networkhelm.routeros.api.RouterOsLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PrintSchema(private val link: RouterOsLink) {
    private val known = mutableMapOf<MenuPath, MenuSchema>()
    private val counted = mutableMapOf<MenuPath, Pair<MenuSchema, Int?>>()
    private val lock = Mutex()

    suspend fun describe(path: MenuPath): MenuSchema {
        lock.withLock { known[path] }?.let { return it }
        val (outline, count) = count(path)
        val schema = when (outline.kind) {
            NodeKind.Settings -> MenuSchema(path, NodeKind.Settings, emptyList(), outline.commands, arguments(print(path)))
            NodeKind.List -> {
                val rows = if (count != null && count <= MAX_ROWS) print(path) else emptyList()
                val arguments = arguments(rows)
                val switches = if (arguments.any { it.name == "disabled" }) listOf("enable", "disable") else emptyList()
                MenuSchema(path, NodeKind.List, emptyList(), outline.commands + switches, arguments)
            }
            else -> outline
        }
        lock.withLock { known[path] = schema }
        return schema
    }

    suspend fun outline(path: MenuPath): MenuSchema = lock.withLock { known[path] } ?: count(path).first

    suspend fun forget() = lock.withLock {
        known.clear()
        counted.clear()
    }

    private suspend fun count(path: MenuPath): Pair<MenuSchema, Int?> {
        lock.withLock { counted[path] }?.let { return it }
        val result = probe(path)
        lock.withLock { counted[path] = result }
        return result
    }

    private suspend fun probe(path: MenuPath): Pair<MenuSchema, Int?> {
        if (path == MenuPath.Root) throw unlisted(path)
        val count = try {
            link.call(listOf(path.apiCommand("print"), "=count-only=")).done["ret"]?.toIntOrNull()
        } catch (e: ApiTrapException) {
            val message = e.message.orEmpty()
            return when {
                message == "unknown parameter" -> MenuSchema(path, NodeKind.Settings, emptyList(), listOf("print", "set"), emptyList()) to null
                message == "no such command" -> MenuSchema(path, NodeKind.Command, emptyList(), emptyList(), emptyList()) to null
                message.endsWith("(print)") -> throw unlisted(path)
                message.startsWith("no such command") -> throw DeviceException(ErrorCategory.MissingItem, message, e)
                else -> throw e
            }
        }
        return MenuSchema(path, NodeKind.List, emptyList(), listOf("print", "add", "set", "remove"), emptyList()) to count
    }

    private suspend fun print(path: MenuPath) = link.call(listOf(path.apiCommand("print"))).rows

    private fun arguments(rows: List<Map<String, String>>): List<Argument> =
        rows.flatMap { it.keys }.distinct().filter { it != ".id" }.map { name ->
            val values = rows.mapNotNull { it[name] }
            Argument(name, if (values.isNotEmpty() && values.all { it == "true" || it == "false" }) ValueType.YesNo else ValueType.Text())
        }

    private fun unlisted(path: MenuPath) = DeviceException(ErrorCategory.Unsupported, "the router does not say what $path holds")

    private companion object {
        const val MAX_ROWS = 500
    }
}
