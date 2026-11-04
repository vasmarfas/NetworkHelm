package com.vasmarfas.networkhelm.vyos

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
import com.vasmarfas.networkhelm.model.matches
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.session.SafeMode
import com.vasmarfas.networkhelm.session.polled
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.openTlsTrusted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VyosSession(
    private val api: VyosCall,
    override val device: DeviceInfo,
    private val pollInterval: Duration = 10.seconds,
    // the load of a configuration from a string needs a VyOS newer than 1.4, so each session keeps its own file
    private val snapshot: String = "/config/safe-mode-${Random.nextLong().toULong().toString(16)}.config",
) : DeviceSession {
    override val capabilities = Capabilities(
        subscriptions = false,
        streamingCommands = false,
        cancel = false,
        safeMode = SafeModeKind.CommitConfirm,
        terminal = false,
        files = false,
        export = true,
        menuContents = false,
    )
    override val ssh: SshSession? = null

    private val edits = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val safe = CommitConfirm()
    override val safeMode: SafeMode = safe

    private var unconfirmed = false

    private val shown: Map<MenuPath, suspend () -> List<Item>> = mapOf(
        MenuPath.parse("status/interfaces") to ::interfaces,
        MenuPath.parse("status/routes") to { routes(show("ip", "route", "json")) + routes(show("ipv6", "route", "json")) },
        MenuPath.parse("status/arp") to { table(show("arp")).map { Item("${it["address"]} ${it["interface"]}", it) } },
        MenuPath.parse("status/dhcp-leases") to { table(show("dhcp", "server", "leases")).map { Item(it.values.firstOrNull().orEmpty(), it) } },
        MenuPath.parse("status/log") to { log(show("log", "tail", "500")) },
    )

    override suspend fun describe(path: MenuPath): MenuSchema = calls {
        val depth = path.segments.size
        val children = (lists + settings + shown.keys).filter { it.segments.size > depth && it.segments.take(depth) == path.segments }.map { it.segments[depth] }.distinct()
        when (path) {
            in lists -> MenuSchema(path, NodeKind.List, emptyList(), listOf("print", "add", "set", "remove"), arguments(path, elements(node(path))))
            in shown -> MenuSchema(path, NodeKind.List, emptyList(), listOf("print"), emptyList())
            in settings -> MenuSchema(path, NodeKind.Settings, children, listOf("print", "set"), arguments(path, listOf(node(path))))
            else -> if (children.isEmpty()) throw missing(path) else MenuSchema(path, NodeKind.Directory, children, emptyList(), emptyList())
        }
    }

    override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> = flow {
        emitAll(polled(pollInterval, edits) { calls { read(path) }.filter { filter?.matches(it) != false } })
    }

    override suspend fun get(path: MenuPath, id: String?, properties: List<String>?): Item? = calls { read(path).firstOrNull { id == null || it.id == id } }

    override suspend fun count(path: MenuPath, filter: Filter?): Int = calls { read(path).count { filter?.matches(it) != false } }

    override suspend fun add(path: MenuPath, values: Map<String, String>): String = calls {
        if (path !in lists) throw DeviceException(ErrorCategory.Unsupported, "$path of VyOS holds settings, not elements")
        val name = values[NAME]?.trim().orEmpty()
        if (name.isEmpty()) throw DeviceException(ErrorCategory.ArgumentValue, "a new element of $path needs its value in $NAME")
        val base = path.segments + name
        configure(commands(base, null, values - NAME, types(path, elements(node(path)))).ifEmpty { listOf(command("set", base)) })
        name
    }

    override suspend fun set(path: MenuPath, id: String?, changes: Map<String, String?>) {
        calls {
            val node = node(path)
            if (path in lists) {
                val name = id ?: throw DeviceException(ErrorCategory.MissingItem, "$path holds elements, not settings")
                val element = node[name] as? JsonObject ?: throw DeviceException(ErrorCategory.MissingItem, "$path has no $name")
                if (changes[NAME]?.let { it != name } == true) throw DeviceException(ErrorCategory.Unsupported, "the API of VyOS renames no element")
                configure(commands(path.segments + name, element, changes - NAME, types(path, elements(node))))
            } else {
                if (path !in settings) throw missing(path)
                configure(commands(path.segments, node, changes, types(path, listOf(node))))
            }
        }
    }

    override suspend fun remove(path: MenuPath, ids: List<String>) {
        calls {
            if (path !in lists) throw DeviceException(ErrorCategory.Unsupported, "$path of VyOS holds settings, not elements")
            configure(ids.map { command("delete", path.segments + it) })
        }
    }

    override suspend fun move(path: MenuPath, ids: List<String>, before: String?) {
        throw DeviceException(ErrorCategory.Unsupported, "VyOS orders its rules by their numbers")
    }

    override fun run(path: MenuPath, command: String, arguments: Map<String, String>): Flow<Item> = emptyFlow()

    override suspend fun export(path: MenuPath): String = calls {
        val text = (api.call("show", command("show", listOf("configuration", "commands"))) as? JsonPrimitive)?.contentOrNull.orEmpty()
        val prefix = (listOf("set") + path.segments).joinToString(" ")
        if (path.segments.isEmpty()) text else text.lines().filter { it == prefix || it.startsWith("$prefix ") }.joinToString("\n")
    }

    override fun close() {}

    private fun missing(path: MenuPath) = DeviceException(ErrorCategory.MissingItem, "the app knows no node of VyOS at $path")

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
        in lists, in settings -> items(path, node(path))
        in shown -> shown.getValue(path)()
        RESOURCE -> listOf(Item("", resource(show("system", "memory", "detail"), show("system", "uptime"))))
        else -> throw missing(path)
    }

    private suspend fun show(vararg path: String) = (api.call("show", command("show", path.toList())) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private suspend fun interfaces(): List<Item> {
        val counters = table(show("interfaces", "counters")).associateBy { it["interface"] }
        return table(show("interfaces")).map { row -> row["interface"].orEmpty().let { name -> Item(name, row + counters[name].orEmpty()) } }
    }

    private suspend fun node(path: MenuPath): JsonObject {
        val node = try {
            api.call("retrieve", command("showConfig", path.segments)) as? JsonObject ?: JsonObject(emptyMap())
        } catch (e: DeviceException) {
            if (e.category != ErrorCategory.ArgumentValue || exists(api, path.segments)) throw e
            JsonObject(emptyMap())
        }
        val own = node[path.segments.last()]
        return if (path in lists && node.size == 1 && own is JsonObject) own else node
    }

    private fun elements(node: JsonObject) = node.values.mapNotNull { it as? JsonObject }

    private fun types(path: MenuPath, nodes: List<JsonObject>) = arguments(path, nodes).associate { it.name to it.type }

    // under Safe Mode the commit is a commit-confirm confirmed at once: a change that cuts the app off
    // stays unconfirmed, and VyOS takes it back after a minute
    private suspend fun configure(commands: List<JsonObject>) {
        if (commands.isEmpty()) return
        val confirming = safe.on.value
        if (confirming && unconfirmed) confirm()
        api.call(
            "configure",
            JsonObject(
                buildMap {
                    put("commands", JsonArray(commands))
                    if (confirming) put("confirm_time", JsonPrimitive(CONFIRM_MINUTES))
                },
            ),
        )
        edits.tryEmit(Unit)
        if (confirming) {
            unconfirmed = true
            safe.count.update { it + 1 }
            confirm()
        }
        api.call("config-file", JsonObject(mapOf("op" to JsonPrimitive("save"))))
    }

    private suspend fun confirm() {
        api.call("config-file", JsonObject(mapOf("op" to JsonPrimitive("confirm"))))
        unconfirmed = false
    }

    private inner class CommitConfirm : SafeMode {
        val on = MutableStateFlow(false)
        val count = MutableStateFlow(0)

        override val active: Flow<Boolean> = on

        override val actions: Flow<Int> = count

        override suspend fun enter(): Boolean = calls {
            if (on.value) return@calls true
            api.call("config-file", JsonObject(mapOf("op" to JsonPrimitive("save"), "file" to JsonPrimitive(snapshot))))
            count.value = 0
            on.value = true
            true
        }

        override suspend fun commit() {
            calls { if (unconfirmed) confirm() }
            on.value = false
        }

        override suspend fun rollback() {
            if (!on.value) return
            calls {
                if (unconfirmed) confirm()
                if (count.value > 0) {
                    api.call("config-file", JsonObject(mapOf("op" to JsonPrimitive("load"), "file" to JsonPrimitive(snapshot))))
                    api.call("config-file", JsonObject(mapOf("op" to JsonPrimitive("save"))))
                }
            }
            on.value = false
            edits.tryEmit(Unit)
        }
    }

    private companion object {
        const val CONFIRM_MINUTES = 1

        val RESOURCE = MenuPath.parse("system/resource")
    }
}

private suspend fun exists(api: VyosCall, path: List<String>) = (api.call("retrieve", command("exists", path)) as? JsonPrimitive)?.booleanOrNull == true

class VyosLogin(val host: String, val key: String, val port: Int = 443)

suspend fun connectVyos(login: VyosLogin, trust: TrustPolicy, open: suspend () -> ByteStream = { openTlsTrusted(login.host, login.port, trust) }): VyosSession {
    val api = HttpsVyos(login.host, login.key, open)
    val named = exists(api, listOf("system", "host-name"))
    val info = try {
        api.info() as? JsonObject
    } catch (e: DeviceException) {
        if (e.category != ErrorCategory.MissingItem) throw e
        null
    }
    fun text(key: String) = (info?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
    val hostname = text("hostname") ?: if (named) (api.call("retrieve", command("returnValue", listOf("system", "host-name"))) as? JsonPrimitive)?.contentOrNull else null
    val version = text("version") ?: (api.call("show", command("show", listOf("version"))) as? JsonPrimitive)?.contentOrNull.orEmpty()
        .lines().firstOrNull { it.startsWith("Version:") }?.substringAfter(':')?.trim()?.removePrefix("VyOS ")
    val device = DeviceInfo(Platform.Vyos, hostname ?: login.host, version.orEmpty(), "", "VyOS", emptySet(), setOf("read", "write"))
    return VyosSession(api, device)
}
