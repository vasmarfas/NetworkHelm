package com.vasmarfas.networkhelm.session

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
import com.vasmarfas.networkhelm.model.matches
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.ssh.connectSshTrusted
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class SshHostSession(
    override val ssh: SshSession,
    override val device: DeviceInfo,
    private val linux: Boolean = false,
    private val pollInterval: Duration = 3.seconds,
    private val time: TimeSource = TimeSource.Monotonic,
) : DeviceSession {
    override val capabilities = Capabilities(
        subscriptions = false,
        streamingCommands = linux,
        cancel = false,
        safeMode = SafeModeKind.None,
        terminal = true,
        files = true,
        export = false,
        menuContents = false,
    )
    override val safeMode: SafeMode? = null

    private val host = LinuxHost(ssh)
    private val files = HostFiles(ssh)

    override suspend fun describe(path: MenuPath): MenuSchema = calls {
        val known = schemas.keys.filter { it == FILES || linux && host.has(it) }
        val children = known.flatMap { key -> (1..key.segments.size).map { MenuPath(key.segments.take(it)) } }.filter { it.parent == path }.map { it.segments.last() }.distinct().sorted()
        val arguments = schemas[path]?.takeIf { path in known }
        when {
            arguments != null -> MenuSchema(
                path, if (path.toString() in settings) NodeKind.Settings else NodeKind.List, emptyList(),
                when (path.toString()) {
                    "interface" -> listOf("print", "monitor-traffic")
                    "file" -> listOf("print", "remove")
                    else -> listOf("print")
                },
                arguments.map { Argument(it, ValueType.Text(), readOnly = true) },
            )
            children.isNotEmpty() || path == MenuPath.Root -> MenuSchema(path, NodeKind.Directory, children, emptyList(), emptyList())
            else -> throw DeviceException(ErrorCategory.MissingItem, "$path is not on this host")
        }
    }

    override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> = flow {
        if (path != FILES && (!linux || path !in schemas)) throw DeviceException(ErrorCategory.MissingItem, "$path is not on this host")
        emitAll(polled(pollInterval) { calls { read(path) }.filter { filter?.matches(it) != false } })
    }

    override suspend fun get(path: MenuPath, id: String?, properties: List<String>?): Item? =
        if (linux || path == FILES) calls { read(path).firstOrNull { id == null || it.id == id } } else null

    override suspend fun count(path: MenuPath, filter: Filter?): Int =
        if (linux || path == FILES) calls { read(path).count { filter?.matches(it) != false } } else 0

    private suspend fun read(path: MenuPath) = if (path == FILES) files.read() else host.read(path)

    override suspend fun add(path: MenuPath, values: Map<String, String>): String = throw readOnly()

    override suspend fun set(path: MenuPath, id: String?, changes: Map<String, String?>) {
        throw readOnly()
    }

    override suspend fun remove(path: MenuPath, ids: List<String>) {
        if (path != FILES) throw readOnly()
        calls { files.remove(ids) }
    }

    override suspend fun move(path: MenuPath, ids: List<String>, before: String?) {
        throw readOnly()
    }

    override fun run(path: MenuPath, command: String, arguments: Map<String, String>): Flow<Item> = flow {
        if (!linux || path.toString() != "interface" || command != "monitor-traffic") throw DeviceException(ErrorCategory.MissingItem, "$path/$command is not on this host")
        val wanted = arguments["interface"].orEmpty().split(',').filter { it.isNotEmpty() }
        var before: Pair<Map<String, List<Long>>, TimeMark>? = null
        var round = 0
        while (true) {
            val now = calls { host.traffic() }
            before?.let { (then, at) ->
                val seconds = at.elapsedNow().inWholeMilliseconds / 1000.0
                for (name in wanted) {
                    val counters = now[name] ?: continue
                    val previous = then[name] ?: continue
                    fun rate(i: Int, scale: Int) = if (seconds > 0) "${((counters[i] - previous[i]).coerceAtLeast(0) * scale / seconds).toLong()}" else "0"
                    emit(Item("", mapOf(
                        "name" to name, "rx-bits-per-second" to rate(0, 8), "tx-bits-per-second" to rate(1, 8), "rx-packets-per-second" to rate(2, 1),
                        "tx-packets-per-second" to rate(3, 1), ".section" to "$round",
                    )))
                }
                round++
            }
            before = now to time.markNow()
            delay(1.seconds)
        }
    }

    override suspend fun export(path: MenuPath): String = throw readOnly()

    override fun close() = ssh.close()

    private fun readOnly() = DeviceException(ErrorCategory.Unsupported, "only the files of a host reached over SSH alone can be changed here")

    private suspend fun <T> calls(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DeviceException) {
        throw e
    } catch (e: Exception) {
        throw DeviceException(ErrorCategory.ConnectionLost, e.message ?: e::class.simpleName.orEmpty(), e)
    }

    private companion object {
        val settings = setOf("system/resource", "system/clock")

        val FILES = MenuPath.parse("file")

        val schemas = mapOf(
            "interface" to listOf(
                "name", "type", "mtu", "mac-address", "running", "disabled", "comment", "master", "parent", "rx-byte", "tx-byte", "rx-packet", "tx-packet",
                "rx-drop", "tx-drop", "rx-error", "tx-error",
            ),
            "ip/address" to listOf("address", "network", "interface", "scope", "dynamic"),
            "ipv6/address" to listOf("address", "interface", "scope", "dynamic"),
            "ip/route" to listOf("dst-address", "gateway", "interface", "protocol", "metric", "routing-table", "pref-src", "type", "active", "connect", "static", "dynamic"),
            "ipv6/route" to listOf("dst-address", "gateway", "interface", "protocol", "metric", "routing-table", "pref-src", "type", "active", "connect", "static", "dynamic"),
            "ip/arp" to listOf("address", "mac-address", "interface", "status", "dynamic", "complete"),
            "ipv6/neighbor" to listOf("address", "mac-address", "interface", "status", "dynamic", "complete", "router"),
            "system/resource" to listOf("uptime", "load", "cpu-load", "cpu-count", "total-memory", "free-memory", "version", "architecture-name", "board-name"),
            "system/clock" to listOf("date", "time", "gmt-offset"),
            "system/storage" to listOf("description", "file-system", "size", "used", "free"),
            "system/process" to listOf("pid", "user", "cpu", "memory", "vsz", "stat", "command"),
            "system/service" to listOf("name", "load", "status", "running", "disabled", "description"),
            "log" to listOf("time", "topics", "message"),
            "file" to listOf("name", "type", "size", "last-modified"),
        ).mapKeys { MenuPath.parse(it.key) }
    }
}

suspend fun connectSshHost(host: String, port: Int, user: String, auth: SshAuth, trust: TrustPolicy): SshHostSession {
    val ssh = connectSshTrusted(host, port, user, auth, trust)
    val uname = try {
        ssh.exec("uname -snrm").takeIf { it.exitStatus == 0 }?.output?.trim()?.split(' ').orEmpty()
    } catch (e: Exception) {
        ssh.close()
        throw e
    }
    val named = uname.size >= 4
    val device = DeviceInfo(
        Platform.Ssh,
        if (named) uname[1] else host,
        if (named) uname[0] + " " + uname[2] else "",
        if (named) uname.last() else "",
        "",
        emptySet(),
        setOf("read", "write"),
    )
    return SshHostSession(ssh, device, linux = named && uname[0] == "Linux")
}
