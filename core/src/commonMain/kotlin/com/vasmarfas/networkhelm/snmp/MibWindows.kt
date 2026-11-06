package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.Pack
import com.vasmarfas.networkhelm.packs.PackPath
import com.vasmarfas.networkhelm.packs.PackTab
import com.vasmarfas.networkhelm.packs.PackWindow
import com.vasmarfas.networkhelm.packs.packName
import com.vasmarfas.networkhelm.routeros.schema.label
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

internal const val MIB_PATH = "mib"

internal val MenuPath.inMib: Boolean get() = segments.firstOrNull() == MIB_PATH

// the windows of what the walk found: a tab for each table and group of scalars of a module, and the subtrees the dictionary does not know as raw OIDs
internal class MibWindows(
    private val client: SnmpClient,
    private val mib: SnmpMib,
    private val budget: WalkBudget,
    private val time: TimeSource,
) {
    private sealed interface View

    private class TableView(val entry: MibObject, val title: String, val columns: List<MibObject>, val parts: List<MibObject>, val status: MibObject?, val invalid: MibObject?) : View

    private class ScalarView(val objects: List<MibObject>) : View

    private class RawView(val root: Oid) : View

    private class Tab(val path: MenuPath, val window: String, val title: String, val view: View)

    private val walking = Mutex()
    private var cached: List<Tab>? = null

    private suspend fun tabs(): List<Tab> = walking.withLock { cached ?: arranged(client.discover(mib, budget, time)).also { cached = it } }

    private fun arranged(found: Discovery): List<Tab> = buildList {
        // an empty table has no rows to walk, so one that creates rows stands beside the objects found in its group
        val groups = (found.scalars.keys.map { it.oid.parent() } + found.tables.keys.map { it.oid.parent().parent() }).toSet()
        val creatable = mib.known.filter { it.kind == MibKind.Column && it.tc == "RowStatus" && it.writable }.mapNotNull { mib[it.oid.parent()] }
            .filter { it.oid.parent().parent() in groups }
        for (entry in (found.tables.keys + creatable).distinct().sortedBy { it.oid }) {
            val table = mib[entry.oid.parent()] ?: continue
            if (table.name in COVERED) continue
            val all = mib.columns(entry)
            val status = all.firstOrNull { it.tc == "RowStatus" && it.writable }
            val columns = if (status != null) all.filter { it.readable || it.writable } else all.filter { it in found.tables[entry].orEmpty() }
            val parts = entry.index.mapNotNull(mib::named).filter { it !in columns }
            val invalid = all.firstOrNull { column -> column.writable && column.enums.values.any { it == "invalid" } }
            add(Tab(MenuPath(listOf(MIB_PATH, entry.module, table.name)), entry.module, table.name, TableView(entry, table.name, columns, parts, status, invalid)))
        }
        for ((group, objects) in found.scalars.keys.groupBy { it.oid.parent() }) {
            val node = mib[group] ?: continue
            if (node.name in COVERED) continue
            add(Tab(MenuPath(listOf(MIB_PATH, node.module, node.name)), node.module, node.name, ScalarView(objects.sortedBy { it.oid })))
        }
        for (root in found.unknown) {
            val window = mib.vendor(root) ?: mib.find(root.parent())?.name ?: "$root"
            add(Tab(MenuPath(listOf(MIB_PATH, RAW_PATH, "$root")), window, mib.describe(root), RawView(root)))
        }
    }

    suspend fun packs(): List<Pack> = tabs().groupBy { it.window }.map { (window, tabs) ->
        Pack(
            platform = Platform.Snmp.packName,
            menu = listOf(MENU, window),
            window = PackWindow(window, tabs.map { PackTab(it.title, "${it.path}") }),
            paths = tabs.associate { tab -> "${tab.path}" to PackPath(columns = shown(tab.view)) },
            labels = tabs.flatMap { tab -> labels(tab.view) }.toMap(),
        )
    }

    private fun shown(view: View): List<String> = when (view) {
        is TableView -> (view.parts + view.columns.filter { it.readable }).map { it.name }
        is ScalarView -> emptyList()
        is RawView -> RAW_COLUMNS
    }

    // the columns of a table share the name of its entry as a prefix, which the title of a column leaves out
    private fun labels(view: View): List<Pair<String, String>> {
        if (view !is TableView) return emptyList()
        val prefix = view.entry.name.removeSuffix("Entry")
        return (view.parts + view.columns).map { column ->
            column.name to label(column.name.removePrefix(prefix).takeIf { it != column.name && it.isNotEmpty() } ?: column.name)
        }
    }

    suspend fun describe(path: MenuPath): MenuSchema {
        val all = tabs()
        if (path.segments.size < 3) {
            val children = all.filter { it.path.segments.take(path.segments.size) == path.segments }.map { it.path.segments[path.segments.size] }.distinct()
            if (children.isEmpty()) throw missing(path)
            return MenuSchema(path, NodeKind.Directory, children.sorted(), emptyList(), emptyList())
        }
        return when (val view = view(path)) {
            is TableView -> {
                val arguments = listOfNotNull(view.status?.let { Argument(INDEX, ValueType.Text(), fixed = true, placeholder = view.entry.index.joinToString(", ")) }) +
                    view.parts.map { argument(it, readOnly = true) } + view.columns.map { argument(it, readOnly = !it.writable) }
                val commands = listOf("print") + listOfNotNull(
                    "set".takeIf { view.columns.any { it.writable && it != view.status } },
                    "add".takeIf { view.status != null },
                    "remove".takeIf { view.status != null || view.invalid != null },
                )
                MenuSchema(path, NodeKind.List, emptyList(), commands, arguments, prefill = view.status?.let { free(view, it) }?.let { mapOf(INDEX to it) }.orEmpty())
            }
            is ScalarView -> MenuSchema(path, NodeKind.Settings, emptyList(), listOf("print") + listOfNotNull("set".takeIf { view.objects.any { it.writable } }),
                view.objects.map { argument(it, readOnly = !it.writable) })
            is RawView -> MenuSchema(path, NodeKind.List, emptyList(), listOf("print", "set"),
                RAW_COLUMNS.map { Argument(it, ValueType.Text(), readOnly = it != RAW_VALUE) })
        }
    }

    // the next number after the highest index, for a table whose index is one number
    private suspend fun free(view: TableView, status: MibObject): String? {
        if (view.entry.index.singleOrNull()?.let(mib::named)?.let { it.type in NUMBERS && it.enums.isEmpty() } != true) return null
        val rows = client.columns(listOf(status.oid), most = TABLE_ROWS).getValue(status.oid).keys
        return "${(rows.mapNotNull { it.singleOrNull() }.maxOrNull() ?: 0L) + 1}"
    }

    private fun argument(column: MibObject, readOnly: Boolean): Argument = when {
        column.tc == "TruthValue" -> Argument(column.name, ValueType.YesNo, readOnly = readOnly)
        column.type == "Bits" -> Argument(column.name, ValueType.Enumeration(multiple = true, closed = true), column.enums.values.toList(), readOnly = readOnly)
        column.enums.isNotEmpty() -> Argument(column.name, ValueType.Enumeration(closed = true), column.enums.values.toList(), readOnly = readOnly)
        column.type == "IpAddress" -> Argument(column.name, ValueType.Ip4, readOnly = readOnly)
        column.tc == "MacAddress" -> Argument(column.name, ValueType.Mac, readOnly = readOnly)
        column.type in NUMBERS || column.type.startsWith("Counter") -> Argument(column.name, ValueType.Integer(), readOnly = readOnly)
        else -> Argument(column.name, ValueType.Text(), readOnly = readOnly)
    }

    suspend fun read(path: MenuPath): List<Item> = when (val view = view(path)) {
        is TableView -> rows(view)
        is ScalarView -> {
            val readable = view.objects.filter { it.readable }
            val values = readable.chunked(40).flatMap { chunk -> client.get(chunk.map { it.oid + listOf(0L) }) }.associate { it.oid to it.value }
            listOf(Item("", readable.associate { it.name to mib.shown(it, values[it.oid + listOf(0L)]) }))
        }
        is RawView -> client.subtree(view.root, RAW_ROWS).map { binding ->
            val id = binding.oid.index(view.root).joinToString(".")
            Item(id, mapOf(RAW_OID to id, RAW_TYPE to typeOf(binding.value), RAW_VALUE to text(binding.value)))
        }
    }

    // a table past TABLE_ROWS rows shows its first rows only
    private suspend fun rows(view: TableView): List<Item> {
        val readable = view.columns.filter { it.readable }
        val values = client.columns(readable.map { it.oid }, most = TABLE_ROWS)
        val indexes = values.values.flatMap { it.keys }.distinct().sortedBy { Oid(it) }
        return indexes.map { index ->
            Item(index.joinToString("."), buildMap {
                val parts = parts(view.entry, index)
                if (view.status != null) put(INDEX, parts?.joinToString(", ") { (part, value) -> mib.shown(part, value) } ?: index.joinToString("."))
                for (part in view.parts) put(part.name, parts?.firstOrNull { it.first == part }?.let { (_, value) -> mib.shown(part, value) }.orEmpty())
                for (column in readable) put(column.name, mib.shown(column, values.getValue(column.oid)[index]))
            })
        }
    }

    // all the changes of one row go in one SET, which an agent applies whole or not at all
    suspend fun set(path: MenuPath, id: String?, changes: Map<String, String?>) {
        val values = when (val view = view(path)) {
            is TableView -> {
                val index = arcs(id ?: throw DeviceException(ErrorCategory.MissingItem, "a row of ${view.title} is named by its index"))
                changes.map { (name, value) -> writable(view.columns, name, view.title).let { it.oid + index to mib.written(it, value) } }
            }
            is ScalarView -> changes.map { (name, value) -> writable(view.objects, name, path.segments.last()).let { it.oid + listOf(0L) to mib.written(it, value) } }
            is RawView -> {
                val change = changes.entries.singleOrNull()?.takeIf { it.key == RAW_VALUE }
                    ?: throw DeviceException(ErrorCategory.Unsupported, "an OID the dictionary does not know changes only its value")
                val target = view.root + arcs(id ?: throw DeviceException(ErrorCategory.MissingItem, "an OID is named by its arcs under ${view.root}"))
                listOf(target to raw(client.get(listOf(target)).single().value, change.value.orEmpty()))
            }
        }
        client.set(values)
    }

    // createAndGo with every given column in one SET; an agent that wants the row built in steps answers wrongValue or inconsistentValue
    suspend fun add(path: MenuPath, values: Map<String, String>): String {
        val view = view(path) as? TableView ?: throw DeviceException(ErrorCategory.Unsupported, "SNMP creates rows only in tables")
        val status = view.status ?: throw DeviceException(ErrorCategory.Unsupported, "${view.title} has no RowStatus, so its rows are not created over SNMP")
        val index = index(view, values[INDEX] ?: throw DeviceException(ErrorCategory.ArgumentValue, "a new row of ${view.title} needs its index"))
        val row = status.oid + index
        val given = values.filterKeys { it != INDEX && it != status.name }.map { (name, value) -> writable(view.columns, name, view.title).let { it.oid + index to mib.written(it, value) } }
        try {
            client.set(listOf(row to SnmpValue.Number(INTEGER, CREATE_AND_GO)) + given)
        } catch (e: SnmpError) {
            if (e.status != SnmpError.WRONG_VALUE && e.status != SnmpError.INCONSISTENT_VALUE) throw e
            client.set(listOf(row to SnmpValue.Number(INTEGER, CREATE_AND_WAIT)))
            try {
                if (given.isNotEmpty()) client.set(given)
                client.set(listOf(row to SnmpValue.Number(INTEGER, ACTIVE)))
            } catch (failed: DeviceException) {
                // a row left in notReady would hold the index, so it goes before the error is shown
                try {
                    client.set(listOf(row to SnmpValue.Number(INTEGER, DESTROY)))
                } catch (other: DeviceException) {
                    failed.addSuppressed(other)
                }
                throw failed
            }
        }
        return index.joinToString(".")
    }

    suspend fun remove(path: MenuPath, ids: List<String>) {
        val view = view(path) as? TableView ?: throw DeviceException(ErrorCategory.Unsupported, "SNMP removes only rows of tables")
        val (column, value) = view.status?.let { it to DESTROY } ?: view.invalid?.let { invalid -> invalid to invalid.enums.entries.first { it.value == "invalid" }.key }
            ?: throw DeviceException(ErrorCategory.Unsupported, "${view.title} has no RowStatus, so its rows are not removed over SNMP")
        client.set(ids.map { column.oid + arcs(it) to SnmpValue.Number(INTEGER, value) })
    }

    private suspend fun view(path: MenuPath): View = tabs().firstOrNull { it.path == path }?.view ?: throw missing(path)

    private fun missing(path: MenuPath) = DeviceException(ErrorCategory.MissingItem, "the SNMP agent has nothing at $path")

    private fun writable(columns: List<MibObject>, name: String, title: String): MibObject =
        columns.firstOrNull { it.name == name }?.takeIf { it.writable } ?: throw DeviceException(ErrorCategory.Unsupported, "$name of $title cannot be written over SNMP")

    private fun arcs(id: String): List<Long> = id.split('.').map { it.toLongOrNull() ?: throw DeviceException(ErrorCategory.MissingItem, "$id is no index of a row") }

    // the parts of an index as RFC 2578 lays them out: numbers in one arc, strings and OIDs after their length unless IMPLIED or of a fixed size
    private fun parts(entry: MibObject, index: List<Long>): List<Pair<MibObject, SnmpValue>>? {
        var at = 0
        val parts = entry.index.mapIndexed { i, name ->
            val part = mib.named(name) ?: return null
            val implied = entry.implied && i == entry.index.lastIndex
            val size = when (part.type) {
                "IpAddress" -> 4
                "OctetString", "Bits", "Opaque", "ObjectIdentifier" -> FIXED[part.tc] ?: if (implied) index.size - at else index.getOrNull(at++)?.toInt() ?: return null
                else -> 1
            }
            if (size < 0 || at + size > index.size) return null
            val arcs = index.subList(at, at + size)
            at += size
            part to when (part.type) {
                "IpAddress" -> SnmpValue.Address(bytes(arcs) ?: return null)
                "ObjectIdentifier" -> SnmpValue.Id(Oid(arcs))
                "OctetString", "Bits", "Opaque" -> SnmpValue.Octets(bytes(arcs) ?: return null)
                "TimeTicks" -> SnmpValue.Number(TIMETICKS, arcs.single())
                "Integer32" -> SnmpValue.Number(INTEGER, arcs.single())
                else -> SnmpValue.Number(GAUGE32, arcs.single())
            }
        }
        return parts.takeIf { at == index.size }
    }

    private fun index(view: TableView, typed: String): List<Long> {
        val entry = view.entry
        val texts = if (entry.index.size == 1) listOf(typed.trim()) else typed.split(',').map { it.trim() }
        if (texts.size != entry.index.size) throw DeviceException(ErrorCategory.ArgumentValue, "the index of ${view.title} has ${entry.index.size} parts, ${entry.index.joinToString()}, separated by commas")
        return entry.index.zip(texts).flatMapIndexed { i, (name, text) ->
            val part = mib.named(name) ?: throw DeviceException(ErrorCategory.Unsupported, "the dictionary does not know $name, a part of the index of ${view.title}")
            val implied = entry.implied && i == entry.index.lastIndex
            when (val value = mib.written(part, text)) {
                is SnmpValue.Number -> listOf(value.value)
                is SnmpValue.Address -> value.bytes.map { it.toLong() and 0xFF }
                is SnmpValue.Octets -> value.bytes.map { it.toLong() and 0xFF }.let { if (implied || part.tc in FIXED) it else listOf(it.size.toLong()) + it }
                is SnmpValue.Id -> value.oid.arcs.let { if (implied) it else listOf(it.size.toLong()) + it }
                else -> throw DeviceException(ErrorCategory.Unsupported, "$name cannot be a part of an index")
            }
        }
    }

    // a value of an OID the dictionary does not know goes in the type the agent gave it
    private fun raw(current: SnmpValue, typed: String): SnmpValue {
        fun refused(): Nothing = throw DeviceException(ErrorCategory.ArgumentValue, "$typed is no value of the type ${typeOf(current)}")
        return when (current) {
            is SnmpValue.Number -> when (current.tag) {
                INTEGER, GAUGE32, TIMETICKS -> SnmpValue.Number(current.tag, typed.trim().toLongOrNull() ?: refused())
                else -> throw DeviceException(ErrorCategory.Unsupported, "a counter is not written over SNMP")
            }
            is SnmpValue.Octets -> SnmpValue.Octets(if (text(current) == hex(current.bytes) && current.bytes.isNotEmpty()) hexBytes(typed) ?: refused() else typed.encodeToByteArray())
            is SnmpValue.Address -> SnmpValue.Address(ipv4(typed.trim()) ?: refused())
            is SnmpValue.Id -> SnmpValue.Id(mib.oidOf(typed) ?: refused())
            else -> throw DeviceException(ErrorCategory.Unsupported, "a value of the type ${typeOf(current)} is not written here")
        }
    }

    private companion object {
        const val MENU = "MIB"
        const val RAW_PATH = "oid"
        const val INDEX = "index"
        const val RAW_OID = "oid"
        const val RAW_TYPE = "type"
        const val RAW_VALUE = "value"
        val RAW_COLUMNS = listOf(RAW_OID, RAW_TYPE, RAW_VALUE)
        const val TABLE_ROWS = 1_000
        const val RAW_ROWS = 2_000
        const val ACTIVE = 1L
        const val CREATE_AND_GO = 4L
        const val CREATE_AND_WAIT = 5L
        const val DESTROY = 6L
        val NUMBERS = setOf("Integer32", "Unsigned32", "Gauge32")

        // the hand-made windows show these already
        val COVERED = setOf(
            "system", "ifTable", "ifXTable", "ipAddressTable", "ipAddrTable", "inetCidrRouteTable", "ipCidrRouteTable", "ipRouteTable", "ipNetToPhysicalTable",
            "ipNetToMediaTable", "hrStorageTable",
        )

        // textual conventions of a fixed size, which an index holds without a length
        val FIXED = mapOf("MacAddress" to 6, "BridgeId" to 8)
    }
}

private fun Oid.parent() = Oid(arcs.dropLast(1))

private fun bytes(arcs: List<Long>): ByteArray? = if (arcs.all { it in 0..255 }) ByteArray(arcs.size) { arcs[it].toByte() } else null

private fun typeOf(value: SnmpValue): String = when (value) {
    is SnmpValue.Number -> when (value.tag) {
        INTEGER -> "INTEGER"
        COUNTER32 -> "Counter32"
        GAUGE32 -> "Gauge32"
        TIMETICKS -> "TimeTicks"
        COUNTER64 -> "Counter64"
        else -> "0x${value.tag.toString(16)}"
    }
    is SnmpValue.Octets -> "OCTET STRING"
    is SnmpValue.Address -> "IpAddress"
    is SnmpValue.Id -> "OBJECT IDENTIFIER"
    SnmpValue.Null -> "NULL"
    is SnmpValue.Missing -> "no value"
    is SnmpValue.Other -> "0x${value.tag.toString(16)}"
}
