package com.vasmarfas.networkhelm.keenetic

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Capabilities
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.SafeModeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.model.duration
import com.vasmarfas.networkhelm.model.matches
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.session.SafeMode
import com.vasmarfas.networkhelm.session.polled
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.transport.openTlsTrusted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val INTERFACE = MenuPath.parse("interface")
private val RESOURCE = MenuPath.parse("system/resource")

private val lists = listOf("interface", "ip/route", "ip/arp", "ip/dhcp/bindings", "ip/hotspot", "log", "system/resource").map(MenuPath::parse)

class KeeneticSession(
    private val api: KeeneticCall,
    override val device: DeviceInfo,
    encrypted: Boolean = false,
    private val pollInterval: Duration = 3.seconds,
) : DeviceSession {
    override val capabilities = Capabilities(
        subscriptions = false,
        streamingCommands = false,
        cancel = false,
        safeMode = SafeModeKind.None,
        terminal = false,
        files = false,
        export = false,
        encrypted = encrypted,
        menuContents = false,
    )
    override val safeMode: SafeMode? = null
    override val ssh: SshSession? = null

    override suspend fun describe(path: MenuPath): MenuSchema = calls {
        val depth = path.segments.size
        val children = lists.filter { it.segments.size > depth && it.segments.take(depth) == path.segments }.map { it.segments[depth] }.distinct()
        when {
            path in lists -> MenuSchema(
                path, if (path == RESOURCE) NodeKind.Settings else NodeKind.List, emptyList(), listOf("print"),
                read(path).flatMap { it.values.keys }.distinct().map { Argument(it, ValueType.Text(), readOnly = true) },
            )
            children.isNotEmpty() -> MenuSchema(path, NodeKind.Directory, children, emptyList(), emptyList())
            else -> throw missing(path)
        }
    }

    override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> = flow {
        emitAll(polled(pollInterval) { calls { read(path) }.filter { filter?.matches(it) != false } })
    }

    override suspend fun get(path: MenuPath, id: String?, properties: List<String>?): Item? = calls { read(path).firstOrNull { id == null || it.id == id } }

    override suspend fun count(path: MenuPath, filter: Filter?): Int = calls { read(path).count { filter?.matches(it) != false } }

    override suspend fun add(path: MenuPath, values: Map<String, String>): String = throw readOnly()

    override suspend fun set(path: MenuPath, id: String?, changes: Map<String, String?>) {
        throw readOnly()
    }

    override suspend fun remove(path: MenuPath, ids: List<String>) {
        throw readOnly()
    }

    override suspend fun move(path: MenuPath, ids: List<String>, before: String?) {
        throw readOnly()
    }

    override fun run(path: MenuPath, command: String, arguments: Map<String, String>): Flow<Item> = emptyFlow()

    override suspend fun export(path: MenuPath): String = throw readOnly()

    override fun close() {}

    private fun missing(path: MenuPath) = DeviceException(ErrorCategory.MissingItem, "the app knows no list of Keenetic at $path")

    private fun readOnly() = DeviceException(ErrorCategory.Unsupported, "the app only reads Keenetic")

    private suspend fun <T> calls(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DeviceException) {
        throw e
    } catch (e: Exception) {
        throw DeviceException(ErrorCategory.ConnectionLost, e.message ?: e::class.simpleName.orEmpty(), e)
    }

    private suspend fun read(path: MenuPath): List<Item> = when (path) {
        !in lists -> throw missing(path)
        RESOURCE -> listOf(Item("", resource()))
        INTERFACE -> rows(api.show("show/interface")).map { row ->
            val flags = listOfNotNull(row["link"]?.let { "running" to "${it == "up"}" }, row["state"]?.let { "disabled" to "${it == "down"}" })
            Item(row.id, mapOf("id" to row.id) + row.values + flags)
        }
        else -> rows(api.show("show/$path"))
    }

    private suspend fun resource(): Map<String, String> {
        val system = (api.show("show/system") as? JsonObject)?.let(::fields).orEmpty()
        return system + listOfNotNull(
            system["cpuload"]?.let { "cpu-load" to it },
            system["memtotal"]?.toLongOrNull()?.let { "total-memory" to "${it * 1024}" },
            system["memfree"]?.toLongOrNull()?.let { "free-memory" to "${it * 1024}" },
            "version" to device.version,
            "board-name" to device.board,
            "architecture-name" to device.architecture,
        )
    }
}

private fun rows(json: JsonElement): List<Item> = when (json) {
    is JsonArray -> json.mapIndexedNotNull { index, row -> (row as? JsonObject)?.let { Item("$index", fields(it)) } }
    is JsonObject -> json.values.singleOrNull { it is JsonArray }?.let(::rows) ?: json.mapNotNull { (key, row) -> (row as? JsonObject)?.let { Item(key, fields(it)) } }
    else -> emptyList()
}

private fun fields(json: JsonObject, prefix: String = ""): Map<String, String> = buildMap {
    for ((key, value) in json) {
        val name = prefix + key
        when (value) {
            is JsonObject -> putAll(fields(value, "$name."))
            is JsonArray -> put(name, value.joinToString(",") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() })
            is JsonPrimitive -> {
                val text = value.contentOrNull.orEmpty()
                put(name, if (key == "uptime") text.toLongOrNull()?.let(::duration) ?: text else text)
            }
        }
    }
}

class KeeneticLogin(val host: String, val user: String, val secret: String, val tls: Boolean = false, val port: Int = if (tls) 443 else 80)

suspend fun connectKeenetic(
    login: KeeneticLogin,
    trust: TrustPolicy,
    open: suspend () -> ByteStream = { if (login.tls) openTlsTrusted(login.host, login.port, trust) else openTcp(login.host, login.port) },
): KeeneticSession {
    val api = HttpKeenetic(login.host, login.user, login.secret, open)
    api.login()
    val system = (api.show("show/system") as? JsonObject)?.let(::fields).orEmpty()
    val version = (api.show("show/version") as? JsonObject)?.let(::fields).orEmpty()
    val device = DeviceInfo(
        Platform.Keenetic,
        system["hostname"].orEmpty().ifEmpty { login.host },
        version["title"].orEmpty().ifEmpty { version["release"].orEmpty() },
        version["arch"].orEmpty(),
        version["description"].orEmpty().ifEmpty { version["model"].orEmpty() },
        emptySet(),
        setOf("read"),
    )
    return KeeneticSession(api, device, login.tls)
}
