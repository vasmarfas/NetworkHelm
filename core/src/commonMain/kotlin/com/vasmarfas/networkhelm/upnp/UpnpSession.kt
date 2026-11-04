package com.vasmarfas.networkhelm.upnp

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
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.transport.openUdp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class UpnpLogin(val host: String, val port: Int = 1900, val user: String = "", val password: String = "")

suspend fun connectUpnp(login: UpnpLogin, open: suspend (String, Int) -> ByteStream = { host, port -> openTcp(host, port) }): UpnpSession {
    val (port, paths) = if (login.port == SSDP) located(login.host) else login.port to DESCRIPTIONS
    val client = UpnpClient(login.host, port, open, login.user, login.password)
    var missing: DeviceException? = null
    for (path in paths) {
        val description = try {
            client.description(path)
        } catch (e: DeviceException) {
            if (e.category != ErrorCategory.MissingItem) throw e
            missing = e
            continue
        }
        val session = UpnpSession(client, description, DeviceInfo(Platform.Upnp, description.name.ifEmpty { login.host }, "", "", description.model, emptySet(), setOf("read", "write")))
        return session.withVersion()
    }
    throw missing ?: DeviceException(ErrorCategory.MissingItem, "${login.host}:$port has no UPnP description")
}

private suspend fun located(host: String): Pair<Int, List<String>> {
    val link = openUdp(host, SSDP)
    val location = try {
        search(link, host)
    } finally {
        link.close()
    } ?: throw DeviceException(ErrorCategory.ConnectionLost, "no SSDP answer from $host:$SSDP")
    val authority = location.substringAfter("://").substringBefore('/')
    return (authority.substringAfterLast(':', "80").toIntOrNull() ?: 80) to listOf(path(location))
}

class UpnpSession(
    private val client: UpnpClient,
    private val description: Description,
    override val device: DeviceInfo,
    private val pollInterval: Duration = 3.seconds,
    private val time: TimeSource = TimeSource.Monotonic,
) : DeviceSession {
    override val capabilities = Capabilities(
        subscriptions = false,
        streamingCommands = true,
        cancel = false,
        safeMode = SafeModeKind.None,
        terminal = false,
        files = false,
        export = false,
        encrypted = false,
        menuContents = false,
    )
    override val safeMode: SafeMode? = null
    override val ssh: SshSession? = null

    // the control of a service by the end of its type, as TR-064 names the same services under urn:dslforum-org
    private fun service(vararg types: String): Pair<String, String>? =
        types.firstNotNullOfOrNull { type -> description.controls.entries.firstOrNull { it.key.endsWith(":$type") }?.toPair() }

    private val common get() = service("WANCommonInterfaceConfig:1")
    private val connection get() = service("WANIPConnection:2", "WANIPConnection:1", "WANPPPConnection:1")
    private val information get() = service("DeviceInfo:1")
    private val hosts get() = service("Hosts:1")

    internal suspend fun withVersion(): UpnpSession {
        val version = calls { optional(information, "GetInfo") }["NewSoftwareVersion"] ?: return this
        return UpnpSession(client, description, DeviceInfo(Platform.Upnp, device.identity, version, "", device.board, emptySet(), device.policies), pollInterval, time)
    }

    override suspend fun describe(path: MenuPath): MenuSchema = calls {
        val children = schemas.keys.filter { available(it) }.flatMap { key -> (1..key.segments.size).map { MenuPath(key.segments.take(it)) } }
            .filter { it.parent == path }.map { it.segments.last() }.distinct().sorted()
        val arguments = schemas[path]
        when {
            arguments != null && available(path) -> MenuSchema(
                path, if (path == RESOURCE) NodeKind.Settings else NodeKind.List, emptyList(),
                when (path) {
                    INTERFACE -> listOf("print", "monitor-traffic")
                    NAT -> listOf("print", "remove")
                    else -> listOf("print")
                },
                arguments.map { Argument(it, ValueType.Text(), readOnly = true) },
            )
            children.isNotEmpty() -> MenuSchema(path, NodeKind.Directory, children, emptyList(), emptyList())
            else -> throw DeviceException(ErrorCategory.MissingItem, "the device has no service for $path")
        }
    }

    private fun available(path: MenuPath) = when (path) {
        INTERFACE -> common != null
        NAT -> connection != null
        LEASE -> hosts != null
        else -> path in schemas
    }

    override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> = flow {
        if (!available(path)) throw DeviceException(ErrorCategory.MissingItem, "the device has no service for $path")
        emitAll(polled(pollInterval) { calls { read(path) }.filter { filter?.matches(it) != false } })
    }

    override suspend fun get(path: MenuPath, id: String?, properties: List<String>?): Item? = calls { read(path).firstOrNull { id == null || it.id == id } }

    override suspend fun count(path: MenuPath, filter: Filter?): Int = calls { read(path).count { filter?.matches(it) != false } }

    override suspend fun add(path: MenuPath, values: Map<String, String>): String = throw readOnly()

    override suspend fun set(path: MenuPath, id: String?, changes: Map<String, String?>) {
        throw readOnly()
    }

    override suspend fun remove(path: MenuPath, ids: List<String>) {
        if (path != NAT) throw readOnly()
        val (service, control) = connection ?: throw DeviceException(ErrorCategory.MissingItem, "the device has no service for $path")
        calls {
            for (id in ids) {
                val parts = id.split('/')
                if (parts.size !in 2..3) throw DeviceException(ErrorCategory.MissingItem, "no port mapping $id")
                client.call(service, control, "DeletePortMapping", mapOf("NewRemoteHost" to parts.getOrElse(2) { "" }, "NewExternalPort" to parts[1], "NewProtocol" to parts[0]))
            }
        }
    }

    override suspend fun move(path: MenuPath, ids: List<String>, before: String?) {
        throw readOnly()
    }

    override fun run(path: MenuPath, command: String, arguments: Map<String, String>): Flow<Item> = flow {
        if (path != INTERFACE || command != "monitor-traffic") throw DeviceException(ErrorCategory.MissingItem, "the device has no $path/$command")
        if ("wan" !in arguments["interface"].orEmpty().split(',')) return@flow
        var before: Pair<List<Long>, TimeMark>? = null
        var round = 0
        while (true) {
            val now = calls { counters() }
            before?.let { (then, at) ->
                val seconds = at.elapsedNow().inWholeMilliseconds / 1000.0
                fun rate(i: Int, scale: Int) = if (now[i] < 0 || then[i] < 0) "" else "${(((now[i] - then[i]) and 0xFFFF_FFFFL) * scale / seconds).toLong()}"
                emit(Item("", mapOf("name" to "wan", "rx-bits-per-second" to rate(0, 8), "tx-bits-per-second" to rate(1, 8), "rx-packets-per-second" to rate(2, 1),
                    "tx-packets-per-second" to rate(3, 1), ".section" to "$round")))
                round++
            }
            before = now to time.markNow()
            delay(1.seconds)
        }
    }

    override suspend fun export(path: MenuPath): String = throw readOnly()

    override fun close() {}

    private fun readOnly() = DeviceException(ErrorCategory.Unsupported, "over UPnP only port mappings can be removed here")

    private suspend fun <T> calls(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DeviceException) {
        throw e
    } catch (e: UpnpFault) {
        throw DeviceException(ErrorCategory.Api, e.message.orEmpty(), e)
    } catch (e: Exception) {
        throw DeviceException(ErrorCategory.ConnectionLost, e.message ?: e::class.simpleName.orEmpty(), e)
    }

    private suspend fun read(path: MenuPath): List<Item> = when (path) {
        RESOURCE -> listOf(Item("", resource()))
        INTERFACE -> listOf(Item("wan", wan()))
        NAT -> mappings()
        LEASE -> leases()
        else -> throw DeviceException(ErrorCategory.MissingItem, "the device has no service for $path")
    }

    private suspend fun optional(service: Pair<String, String>?, action: String): Map<String, String> {
        service ?: return emptyMap()
        return try {
            client.call(service.first, service.second, action)
        } catch (e: UpnpFault) {
            emptyMap()
        } catch (e: DeviceException) {
            if (e.category == ErrorCategory.PermissionDenied) emptyMap() else throw e
        }
    }

    private suspend fun resource(): Map<String, String> {
        val info = optional(information, "GetInfo")
        val status = optional(connection, "GetStatusInfo")
        val external = optional(connection, "GetExternalIPAddress")
        val nat = optional(connection, "GetNATRSIPStatus")
        return buildMap {
            put("name", description.name)
            put("manufacturer", description.manufacturer)
            put("model", description.model)
            put("description", description.description)
            info["NewSoftwareVersion"]?.let { put("version", it) }
            info["NewSerialNumber"]?.let { put("serial-number", it) }
            info["NewUpTime"]?.toLongOrNull()?.let { put("uptime", duration(it)) }
            external["NewExternalIPAddress"]?.let { put("external-address", it) }
            status["NewConnectionStatus"]?.let { put("connection-status", it) }
            status["NewUptime"]?.toLongOrNull()?.let { put("connection-uptime", duration(it)) }
            nat["NewNATEnabled"]?.let { put("nat", "${it == "1"}") }
        }
    }

    private suspend fun wan(): Map<String, String> {
        val link = optional(common, "GetCommonLinkProperties")
        val counters = counters()
        return buildMap {
            put("name", "wan")
            put("type", link["NewWANAccessType"].orEmpty().lowercase())
            put("status", link["NewPhysicalLinkStatus"].orEmpty().lowercase())
            put("running", "${link["NewPhysicalLinkStatus"] == "Up"}")
            put("max-upload", rate(link["NewLayer1UpstreamMaxBitRate"]?.toLongOrNull()))
            put("max-download", rate(link["NewLayer1DownstreamMaxBitRate"]?.toLongOrNull()))
            listOf("rx-byte", "tx-byte", "rx-packet", "tx-packet").forEachIndexed { i, name -> put(name, counters[i].takeIf { it >= 0 }?.toString().orEmpty()) }
        }
    }

    private suspend fun counters(): List<Long> = listOf("GetTotalBytesReceived", "GetTotalBytesSent", "GetTotalPacketsReceived", "GetTotalPacketsSent").map { action ->
        optional(common, action).values.firstOrNull()?.toLongOrNull() ?: -1
    }

    // the index shifts when a mapping goes, so the id is what DeletePortMapping takes: protocol, port, remote host
    private suspend fun mappings(): List<Item> {
        val (service, control) = connection ?: return emptyList()
        return buildList {
            for (index in 0 until MAX_ENTRIES) {
                val entry = try {
                    client.call(service, control, "GetGenericPortMappingEntry", mapOf("NewPortMappingIndex" to "$index"))
                } catch (e: UpnpFault) {
                    if (e.code == 713 || e.code == 714 || e.code == 402) break else throw e
                }
                val id = listOf(entry["NewProtocol"].orEmpty(), entry["NewExternalPort"].orEmpty()) + listOfNotNull(entry["NewRemoteHost"]?.takeIf { it.isNotEmpty() })
                add(Item(id.joinToString("/"), buildMap {
                    put("chain", "dstnat")
                    put("action", "dst-nat")
                    put("protocol", entry["NewProtocol"].orEmpty().lowercase())
                    entry["NewRemoteHost"]?.takeIf { it.isNotEmpty() }?.let { put("src-address", it) }
                    put("dst-port", entry["NewExternalPort"].orEmpty())
                    put("to-addresses", entry["NewInternalClient"].orEmpty())
                    put("to-ports", entry["NewInternalPort"].orEmpty())
                    put("comment", entry["NewPortMappingDescription"].orEmpty())
                    put("disabled", "${entry["NewEnabled"] == "0"}")
                    put("dynamic", "true")
                    entry["NewLeaseDuration"]?.toLongOrNull()?.takeIf { it > 0 }?.let { put("lease", duration(it)) }
                }))
            }
        }
    }

    private suspend fun leases(): List<Item> {
        val (service, control) = hosts ?: return emptyList()
        val count = client.call(service, control, "GetHostNumberOfEntries")["NewHostNumberOfEntries"]?.toIntOrNull() ?: 0
        return (0 until minOf(count, MAX_ENTRIES)).map { index ->
            val host = client.call(service, control, "GetGenericHostEntry", mapOf("NewIndex" to "$index"))
            Item("$index", buildMap {
                put("address", host["NewIPAddress"].orEmpty())
                put("mac-address", host["NewMACAddress"].orEmpty().uppercase())
                put("host-name", host["NewHostName"].orEmpty())
                put("active", "${host["NewActive"] == "1"}")
                put("source", host["NewAddressSource"].orEmpty().lowercase())
                put("interface", host["NewInterfaceType"].orEmpty())
                host["NewLeaseTimeRemaining"]?.toLongOrNull()?.takeIf { it > 0 }?.let { put("expires-after", duration(it)) }
                put("dynamic", "${host["NewAddressSource"] == "DHCP"}")
            })
        }
    }

    private companion object {
        val RESOURCE = MenuPath.parse("system/resource")
        val INTERFACE = MenuPath.parse("interface")
        val NAT = MenuPath.parse("ip/firewall/nat")
        val LEASE = MenuPath.parse("ip/dhcp-server/lease")
        const val MAX_ENTRIES = 1_000

        val schemas = mapOf(
            RESOURCE to listOf("name", "manufacturer", "model", "description", "version", "serial-number", "uptime", "external-address", "connection-status", "connection-uptime", "nat"),
            INTERFACE to listOf("name", "type", "status", "running", "max-upload", "max-download", "rx-byte", "tx-byte", "rx-packet", "tx-packet"),
            NAT to listOf("chain", "action", "protocol", "src-address", "dst-port", "to-addresses", "to-ports", "comment", "disabled", "dynamic", "lease"),
            LEASE to listOf("address", "mac-address", "host-name", "active", "source", "interface", "expires-after", "dynamic"),
        )
    }
}

private const val SSDP = 1900

private val DESCRIPTIONS = listOf("/rootDesc.xml", "/tr64desc.xml", "/igddesc.xml", "/description.xml", "/gatedesc.xml", "/igd.xml", "/DeviceDescription.xml")

private fun rate(bits: Long?): String = when {
    bits == null || bits <= 0 -> ""
    bits % 1_000_000_000 == 0L -> "${bits / 1_000_000_000}Gbps"
    bits % 1_000_000 == 0L -> "${bits / 1_000_000}Mbps"
    bits % 1_000 == 0L -> "${bits / 1_000}kbps"
    else -> "${bits}bps"
}
