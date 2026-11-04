package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.discovery.formatIpv6
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

class SnmpLogin(val host: String, val community: String, val port: Int = 161, val user: SnmpUser? = null)

suspend fun connectSnmp(login: SnmpLogin): SnmpSession {
    val client = SnmpClient(openUdp(login.host, login.port), login.community, user = login.user)
    try {
        val system = client.get(listOf(SYS_NAME, SYS_DESCR)).associate { it.oid to it.value }
        val board = text(system[SYS_DESCR]).lineSequence().first().take(80)
        return SnmpSession(client, DeviceInfo(Platform.Snmp, text(system[SYS_NAME]).ifEmpty { login.host }, "", "", board, emptySet(), setOf("read")))
    } catch (e: Exception) {
        client.close()
        throw e
    }
}

class SnmpSession(
    private val client: SnmpClient,
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
        encrypted = client.encrypted,
        menuContents = false,
    )
    override val safeMode: SafeMode? = null
    override val ssh: SshSession? = null

    private var names = emptyMap<Long, String>()
    private var named: TimeMark? = null

    private var cpu: LongArray? = null

    override suspend fun describe(path: MenuPath): MenuSchema = calls {
        val children = schemas.keys.filter { it.parent == path }.map { it.segments.last() } +
            schemas.keys.mapNotNull { it.parent }.filter { it.parent == path && it !in schemas }.map { it.segments.last() }
        val arguments = schemas[path]
        when {
            arguments != null -> {
                if ((path == NEIGHBOR || path == STORAGE) && read(path).isEmpty()) throw DeviceException(ErrorCategory.MissingItem, "the SNMP agent has no rows of $path")
                val kind = if (path in settings) NodeKind.Settings else NodeKind.List
                val commands = if (path == INTERFACE) listOf("print", "monitor-traffic") else listOf("print")
                MenuSchema(path, kind, emptyList(), commands, arguments.map { Argument(it, ValueType.Text(), readOnly = true) })
            }
            children.isNotEmpty() -> MenuSchema(path, NodeKind.Directory, children.distinct().sorted(), emptyList(), emptyList())
            else -> throw DeviceException(ErrorCategory.MissingItem, "SNMP has no $path here")
        }
    }

    override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> = flow {
        if (path !in schemas) throw DeviceException(ErrorCategory.MissingItem, "SNMP has no $path here")
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

    // agents hold their counters for some seconds, net-snmp for five, so a rate runs from where they last moved
    override fun run(path: MenuPath, command: String, arguments: Map<String, String>): Flow<Item> = flow {
        if (path != INTERFACE || command != "monitor-traffic") throw DeviceException(ErrorCategory.MissingItem, "SNMP has no $path/$command here")
        val wanted = arguments["interface"].orEmpty().split(',').filter { it.isNotEmpty() }.toSet()
        val watched = calls { names() }.filterValues { it in wanted }
        val moved = mutableMapOf<Long, Pair<Traffic, TimeMark>>()
        val rates = mutableMapOf<Long, Map<String, String>>()
        var round = 0
        while (true) {
            val now = calls { traffic(watched.keys) }
            for ((index, name) in watched) {
                val counters = now[index] ?: continue
                val last = moved[index]
                when {
                    last == null -> moved[index] = counters to time.markNow()
                    counters != last.first -> {
                        rates[index] = counters.rates(last.first, last.second.elapsedNow().inWholeMilliseconds / 1000.0)
                        moved[index] = counters to time.markNow()
                    }
                    last.second.elapsedNow() >= 10.seconds -> rates[index] = counters.rates(counters, 1.0)
                }
                rates[index]?.let { emit(Item("", it + mapOf("name" to name, ".section" to "$round"))) }
            }
            if (rates.isNotEmpty()) round++
            delay(1.seconds)
        }
    }

    override suspend fun export(path: MenuPath): String = throw readOnly()

    override fun close() = client.close()

    private fun readOnly() = DeviceException(ErrorCategory.Unsupported, "SNMP is only read here")

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
        RESOURCE -> listOf(Item("", resource()))
        CLOCK -> listOf(Item("", clock()))
        INTERFACE -> interfaces()
        ADDRESS -> addresses().filter { it["address"]?.contains(':') == false }
        ADDRESS6 -> addresses().filter { it["address"]?.contains(':') == true }
        ROUTE -> routes().filter { it["dst-address"]?.contains(':') == false }
        ROUTE6 -> routes().filter { it["dst-address"]?.contains(':') == true }
        ARP -> neighbours(v6 = false)
        NEIGHBOR6 -> neighbours(v6 = true)
        NEIGHBOR -> lldp()
        STORAGE -> storage()
        else -> throw DeviceException(ErrorCategory.MissingItem, "SNMP has no $path here")
    }

    private suspend fun names(): Map<Long, String> {
        if (named?.let { it.elapsedNow() < 30.seconds } == true) return names
        val columns = client.columns(listOf(IF_DESCR, IF_NAME))
        names = columns.getValue(IF_DESCR).keys.associate { index ->
            index.single() to text(columns.getValue(IF_NAME)[index]).ifEmpty { text(columns.getValue(IF_DESCR)[index]) }
        }
        named = time.markNow()
        return names
    }

    private suspend fun resource(): Map<String, String> {
        val scalars = client.get(listOf(SYS_NAME, SYS_DESCR, SYS_CONTACT, SYS_LOCATION, SYS_OBJECT_ID, SYS_UP_TIME, HR_SYSTEM_UPTIME))
            .associate { it.oid to it.value }
        val uptime = (scalars[HR_SYSTEM_UPTIME] as? SnmpValue.Number ?: scalars[SYS_UP_TIME] as? SnmpValue.Number)?.value
        val storage = client.columns(listOf(HR_STORAGE_TYPE, HR_STORAGE_DESCR, HR_STORAGE_UNITS, HR_STORAGE_SIZE, HR_STORAGE_USED))
        val memory = storage.getValue(HR_STORAGE_TYPE).filterValues { (it as? SnmpValue.Id)?.oid == HR_STORAGE_RAM }.keys.firstOrNull()
        val available = storage.getValue(HR_STORAGE_DESCR).filterValues { text(it) == "Available memory" }.keys.firstOrNull()
        fun bytes(column: Oid, index: List<Long>?): Long? {
            if (index == null) return null
            val count = number(storage.getValue(column)[index]) ?: return null
            return count * (number(storage.getValue(HR_STORAGE_UNITS)[index]) ?: 1)
        }
        val total = bytes(HR_STORAGE_SIZE, memory)
        val free = bytes(HR_STORAGE_SIZE, available) ?: total?.let { it - (bytes(HR_STORAGE_USED, memory) ?: 0) }
        return buildMap {
            put("name", text(scalars[SYS_NAME]))
            put("description", text(scalars[SYS_DESCR]))
            put("contact", text(scalars[SYS_CONTACT]))
            put("location", text(scalars[SYS_LOCATION]))
            put("object-id", text(scalars[SYS_OBJECT_ID]))
            uptime?.let { put("uptime", duration(it / 100)) }
            load()?.let { put("cpu-load", "$it") }
            total?.let { put("total-memory", "$it") }
            free?.let { put("free-memory", "$it") }
        }
    }

    private suspend fun load(): Long? {
        val processors = client.columns(listOf(HR_PROCESSOR_LOAD)).getValue(HR_PROCESSOR_LOAD).values.mapNotNull(::number)
        if (processors.isNotEmpty()) return processors.sum() / processors.size
        val raw = client.get(CPU_RAW).map { number(it.value) ?: return null }.toLongArray()
        val previous = cpu
        cpu = raw
        if (previous == null) return null
        val busy = (0..2).sumOf { raw[it] - previous[it] }
        val all = busy + (3..4).sumOf { raw[it] - previous[it] }
        return if (all > 0) busy * 100 / all else null
    }

    private suspend fun clock(): Map<String, String> {
        val date = (client.get(listOf(HR_SYSTEM_DATE)).single().value as? SnmpValue.Octets)?.bytes ?: return emptyMap()
        if (date.size < 8) return emptyMap()
        val year = ((date[0].toInt() and 0xFF) shl 8) or (date[1].toInt() and 0xFF)
        fun two(at: Int) = "${date[at].toInt() and 0xFF}".padStart(2, '0')
        return mapOf("date" to "$year-${two(2)}-${two(3)}", "time" to "${two(4)}:${two(5)}:${two(6)}")
    }

    private suspend fun interfaces(): List<Item> {
        val columns = client.columns(INTERFACE_COLUMNS)
        fun value(column: Oid, index: List<Long>) = columns.getValue(column)[index]
        return columns.getValue(IF_DESCR).keys.map { index ->
            val admin = number(value(IF_ADMIN, index))
            val oper = number(value(IF_OPER, index))
            val high = number(value(IF_HIGH_SPEED, index))
            val speed = number(value(IF_SPEED, index))
            Item(index.joinToString("."), buildMap {
                put("name", text(value(IF_NAME, index)).ifEmpty { text(value(IF_DESCR, index)) })
                put("type", number(value(IF_TYPE, index))?.let { interfaceTypes[it] ?: "$it" }.orEmpty())
                put("mtu", text(value(IF_MTU, index)))
                put("mac-address", mac(value(IF_PHYS, index)))
                put("speed", rate(high?.takeIf { it > 0 }?.times(1_000_000) ?: speed))
                put("status", oper?.let { statuses.getOrElse(it.toInt() - 1) { "$it" } }.orEmpty())
                put("running", "${oper == 1L}")
                put("disabled", "${admin == 2L}")
                put("comment", text(value(IF_ALIAS, index)))
                put("description", text(value(IF_DESCR, index)))
                put("rx-byte", text(value(IF_HC_IN_OCTETS, index) ?: value(IF_IN_OCTETS, index)))
                put("tx-byte", text(value(IF_HC_OUT_OCTETS, index) ?: value(IF_OUT_OCTETS, index)))
                put("rx-packet", packets(listOf(IF_HC_IN_UCAST, IF_HC_IN_MULTICAST, IF_HC_IN_BROADCAST), IF_IN_UCAST) { value(it, index) })
                put("tx-packet", packets(listOf(IF_HC_OUT_UCAST, IF_HC_OUT_MULTICAST, IF_HC_OUT_BROADCAST), IF_OUT_UCAST) { value(it, index) })
                put("rx-drop", text(value(IF_IN_DISCARDS, index)))
                put("tx-drop", text(value(IF_OUT_DISCARDS, index)))
                put("rx-error", text(value(IF_IN_ERRORS, index)))
                put("tx-error", text(value(IF_OUT_ERRORS, index)))
            })
        }
    }

    private suspend fun traffic(indexes: Collection<Long>): Map<Long, Traffic> {
        val oids = indexes.flatMap { index -> TRAFFIC.map { it + listOf(index) } }
        val values = oids.chunked(40).flatMap { client.get(it) }.associate { it.oid to it.value }
        return indexes.associateWith { index -> Traffic(TRAFFIC.map { values[it + listOf(index)] }) }
    }

    private suspend fun addresses(): List<Item> {
        val names = names()
        val columns = client.columns(listOf(IP_ADDRESS_IF_INDEX, IP_ADDRESS_TYPE, IP_ADDRESS_PREFIX, IP_ADDRESS_ORIGIN, IP_ADDRESS_STATUS))
        val rows = columns.getValue(IP_ADDRESS_IF_INDEX)
        if (rows.isEmpty()) return legacyAddresses(names)
        return rows.mapNotNull { (index, ifIndex) ->
            if (number(columns.getValue(IP_ADDRESS_TYPE)[index]) == 3L) return@mapNotNull null
            val address = inet(index, 0) ?: return@mapNotNull null
            val prefix = (columns.getValue(IP_ADDRESS_PREFIX)[index] as? SnmpValue.Id)?.oid?.arcs?.lastOrNull()?.toInt()
            Item(index.joinToString("."), buildMap {
                put("address", address.text() + (prefix?.let { "/$it" } ?: ""))
                if (prefix != null && !address.v6) put("network", network(address.bytes, prefix))
                put("interface", interfaceName(names, ifIndex))
                put("origin", number(columns.getValue(IP_ADDRESS_ORIGIN)[index])?.let { origins[it] ?: "$it" }.orEmpty())
                put("status", number(columns.getValue(IP_ADDRESS_STATUS)[index])?.let { addressStatuses.getOrElse(it.toInt() - 1) { "$it" } }.orEmpty())
                put("dynamic", "${number(columns.getValue(IP_ADDRESS_ORIGIN)[index])?.let { it in setOf(4L, 5L, 6L) } == true}")
            })
        }
    }

    private suspend fun legacyAddresses(names: Map<Long, String>): List<Item> {
        val columns = client.columns(listOf(IP_AD_ENT_IF_INDEX, IP_AD_ENT_NET_MASK))
        return columns.getValue(IP_AD_ENT_IF_INDEX).map { (index, ifIndex) ->
            val bytes = ByteArray(4) { index.getOrElse(it) { 0 }.toByte() }
            val mask = (columns.getValue(IP_AD_ENT_NET_MASK)[index] as? SnmpValue.Address)?.bytes
            val prefix = mask?.sumOf { (it.toInt() and 0xFF).countOneBits() }
            Item(index.joinToString("."), buildMap {
                put("address", index.joinToString(".") + (prefix?.let { "/$it" } ?: ""))
                if (prefix != null) put("network", network(bytes, prefix))
                put("interface", interfaceName(names, ifIndex))
            })
        }
    }

    private suspend fun routes(): List<Item> {
        val names = names()
        val columns = client.columns(listOf(INET_ROUTE_IF_INDEX, INET_ROUTE_TYPE, INET_ROUTE_PROTO, INET_ROUTE_METRIC))
        val rows = columns.getValue(INET_ROUTE_IF_INDEX)
        if (rows.isEmpty()) return legacyRoutes(names)
        return rows.mapNotNull { (index, ifIndex) ->
            val destination = inet(index, 0) ?: return@mapNotNull null
            val prefix = index.getOrNull(destination.next) ?: return@mapNotNull null
            // the policy is an OBJECT IDENTIFIER of its own length
            val policy = index.getOrNull(destination.next + 1)?.toInt() ?: return@mapNotNull null
            val hop = inet(index, destination.next + 2 + policy) ?: return@mapNotNull null
            route(index, "${destination.text()}/$prefix", hop.takeIf { it.bytes.any { b -> b != 0.toByte() } }?.text().orEmpty(), interfaceName(names, ifIndex),
                number(columns.getValue(INET_ROUTE_TYPE)[index]), number(columns.getValue(INET_ROUTE_PROTO)[index]), number(columns.getValue(INET_ROUTE_METRIC)[index]))
        }
    }

    private suspend fun legacyRoutes(names: Map<Long, String>): List<Item> {
        val cidr = client.columns(listOf(IP_CIDR_ROUTE_IF_INDEX, IP_CIDR_ROUTE_TYPE, IP_CIDR_ROUTE_PROTO, IP_CIDR_ROUTE_METRIC))
        if (cidr.getValue(IP_CIDR_ROUTE_IF_INDEX).isNotEmpty()) {
            return cidr.getValue(IP_CIDR_ROUTE_IF_INDEX).mapNotNull { (index, ifIndex) ->
                if (index.size < 13) return@mapNotNull null
                val prefix = index.subList(4, 8).sumOf { it.toInt().countOneBits() }
                val hop = index.subList(9, 13).joinToString(".")
                route(index, index.subList(0, 4).joinToString(".") + "/$prefix", hop.takeIf { it != "0.0.0.0" }.orEmpty(), interfaceName(names, ifIndex),
                    number(cidr.getValue(IP_CIDR_ROUTE_TYPE)[index]), number(cidr.getValue(IP_CIDR_ROUTE_PROTO)[index]), number(cidr.getValue(IP_CIDR_ROUTE_METRIC)[index]))
            }
        }
        val columns = client.columns(listOf(IP_ROUTE_IF_INDEX, IP_ROUTE_NEXT_HOP, IP_ROUTE_TYPE, IP_ROUTE_PROTO, IP_ROUTE_METRIC, IP_ROUTE_MASK))
        return columns.getValue(IP_ROUTE_IF_INDEX).map { (index, ifIndex) ->
            val prefix = (columns.getValue(IP_ROUTE_MASK)[index] as? SnmpValue.Address)?.bytes?.sumOf { (it.toInt() and 0xFF).countOneBits() } ?: 32
            val hop = text(columns.getValue(IP_ROUTE_NEXT_HOP)[index])
            // ipRouteType counts direct as 3 and indirect as 4, as the newer tables count local and remote
            route(index, index.joinToString(".") + "/$prefix", hop.takeIf { it != "0.0.0.0" }.orEmpty(), interfaceName(names, ifIndex),
                number(columns.getValue(IP_ROUTE_TYPE)[index]), number(columns.getValue(IP_ROUTE_PROTO)[index]), number(columns.getValue(IP_ROUTE_METRIC)[index]))
        }
    }

    private fun route(index: List<Long>, destination: String, gateway: String, interfaceName: String, type: Long?, protocol: Long?, metric: Long?) =
        Item(index.joinToString("."), buildMap {
            put("dst-address", destination)
            put("gateway", gateway)
            put("interface", interfaceName)
            put("type", type?.let { routeTypes.getOrElse(it.toInt() - 1) { "$it" } }.orEmpty())
            put("protocol", protocol?.let { protocols.getOrElse(it.toInt() - 1) { "$it" } }.orEmpty())
            put("metric", metric?.takeIf { it >= 0 }?.toString().orEmpty())
            put("active", "true")
            put("connect", "${protocol == 2L && gateway.isEmpty()}")
            put("static", "${protocol == 3L}")
            put("dynamic", "${protocol != 3L}")
        })

    private suspend fun neighbours(v6: Boolean): List<Item> {
        val names = names()
        val columns = client.columns(listOf(IP_NET_TO_PHYSICAL_PHYS, IP_NET_TO_PHYSICAL_TYPE, IP_NET_TO_PHYSICAL_STATE))
        val rows = columns.getValue(IP_NET_TO_PHYSICAL_PHYS)
        if (rows.isEmpty() && !v6) {
            val legacy = client.columns(listOf(IP_NET_TO_MEDIA_PHYS, IP_NET_TO_MEDIA_TYPE))
            return legacy.getValue(IP_NET_TO_MEDIA_PHYS).mapNotNull { (index, mac) ->
                if (index.size < 5) return@mapNotNull null
                neighbour(index, index.drop(1).joinToString("."), mac(mac), interfaceName(names, index[0]), number(legacy.getValue(IP_NET_TO_MEDIA_TYPE)[index]), null)
            }
        }
        return rows.mapNotNull { (index, mac) ->
            val address = inet(index, 1)?.takeIf { it.v6 == v6 } ?: return@mapNotNull null
            neighbour(index, address.text(), mac(mac), interfaceName(names, index[0]),
                number(columns.getValue(IP_NET_TO_PHYSICAL_TYPE)[index]), number(columns.getValue(IP_NET_TO_PHYSICAL_STATE)[index]))
        }
    }

    private fun neighbour(index: List<Long>, address: String, mac: String, interfaceName: String, type: Long?, state: Long?) =
        Item(index.joinToString("."), buildMap {
            put("address", address)
            put("mac-address", mac)
            put("interface", interfaceName)
            put("type", type?.let { neighbourTypes.getOrElse(it.toInt() - 1) { "$it" } }.orEmpty())
            state?.let { put("status", neighbourStates.getOrElse(it.toInt() - 1) { "$it" }) }
            put("dynamic", "${type == 3L}")
            put("complete", "${mac.isNotEmpty()}")
        })

    private suspend fun lldp(): List<Item> {
        val columns = client.columns(LLDP_COLUMNS)
        val ports = client.columns(listOf(LLDP_LOC_PORT_DESC, LLDP_LOC_PORT_ID))
        val addresses = client.columns(listOf(LLDP_REM_MAN_ADDR_IF_SUBTYPE)).getValue(LLDP_REM_MAN_ADDR_IF_SUBTYPE).keys
            .mapNotNull { index -> index.takeIf { it.size > 5 }?.let { inet(it, 3)?.let { address -> it.subList(0, 3) to address.text() } } }
            .groupBy({ it.first }, { it.second })
        fun value(column: Oid, index: List<Long>) = columns.getValue(column)[index]
        return columns.getValue(LLDP_REM_CHASSIS_ID).keys.filter { it.size == 3 }.map { index ->
            val port = listOf(index[1])
            Item(index.joinToString("."), buildMap {
                put("interface", text(ports.getValue(LLDP_LOC_PORT_DESC)[port]).ifEmpty { text(ports.getValue(LLDP_LOC_PORT_ID)[port]) })
                put("identity", text(value(LLDP_REM_SYS_NAME, index)))
                put("address", addresses[index].orEmpty().joinToString(","))
                put("mac-address", if (number(value(LLDP_REM_CHASSIS_SUBTYPE, index)) == 4L) mac(value(LLDP_REM_CHASSIS_ID, index)) else "")
                put("chassis-id", if (number(value(LLDP_REM_CHASSIS_SUBTYPE, index)) == 4L) mac(value(LLDP_REM_CHASSIS_ID, index)) else text(value(LLDP_REM_CHASSIS_ID, index)))
                put("interface-name", if (number(value(LLDP_REM_PORT_SUBTYPE, index)) == 3L) mac(value(LLDP_REM_PORT_ID, index)) else text(value(LLDP_REM_PORT_ID, index)))
                put("port-description", text(value(LLDP_REM_PORT_DESC, index)))
                put("system-description", text(value(LLDP_REM_SYS_DESC, index)))
            })
        }
    }

    private suspend fun storage(): List<Item> {
        val columns = client.columns(listOf(HR_STORAGE_TYPE, HR_STORAGE_DESCR, HR_STORAGE_UNITS, HR_STORAGE_SIZE, HR_STORAGE_USED))
        return columns.getValue(HR_STORAGE_DESCR).map { (index, description) ->
            val units = number(columns.getValue(HR_STORAGE_UNITS)[index]) ?: 1
            val size = number(columns.getValue(HR_STORAGE_SIZE)[index])?.times(units)
            val used = number(columns.getValue(HR_STORAGE_USED)[index])?.times(units)
            val type = (columns.getValue(HR_STORAGE_TYPE)[index] as? SnmpValue.Id)?.oid
            Item(index.joinToString("."), buildMap {
                put("description", text(description))
                put("type", type?.takeIf { it.under(HR_STORAGE_TYPES) }?.arcs?.last()?.let { storageTypes.getOrElse(it.toInt() - 1) { "$it" } } ?: type?.toString().orEmpty())
                put("size", size?.toString().orEmpty())
                put("used", used?.toString().orEmpty())
                put("free", if (size != null && used != null) "${size - used}" else "")
            })
        }
    }

    private companion object {
        val RESOURCE = MenuPath.parse("system/resource")
        val CLOCK = MenuPath.parse("system/clock")
        val INTERFACE = MenuPath.parse("interface")
        val ADDRESS = MenuPath.parse("ip/address")
        val ADDRESS6 = MenuPath.parse("ipv6/address")
        val ROUTE = MenuPath.parse("ip/route")
        val ROUTE6 = MenuPath.parse("ipv6/route")
        val ARP = MenuPath.parse("ip/arp")
        val NEIGHBOR6 = MenuPath.parse("ipv6/neighbor")
        val NEIGHBOR = MenuPath.parse("ip/neighbor")
        val STORAGE = MenuPath.parse("system/storage")

        val settings = setOf(RESOURCE, CLOCK)

        val schemas = mapOf(
            RESOURCE to listOf("name", "description", "contact", "location", "object-id", "uptime", "cpu-load", "total-memory", "free-memory"),
            CLOCK to listOf("date", "time"),
            INTERFACE to listOf(
                "name", "type", "mtu", "mac-address", "speed", "status", "running", "disabled", "comment", "description",
                "rx-byte", "tx-byte", "rx-packet", "tx-packet", "rx-drop", "tx-drop", "rx-error", "tx-error",
            ),
            ADDRESS to listOf("address", "network", "interface", "origin", "status", "dynamic"),
            ADDRESS6 to listOf("address", "interface", "origin", "status", "dynamic"),
            ROUTE to listOf("dst-address", "gateway", "interface", "type", "protocol", "metric", "active", "connect", "static", "dynamic"),
            ROUTE6 to listOf("dst-address", "gateway", "interface", "type", "protocol", "metric", "active", "connect", "static", "dynamic"),
            ARP to listOf("address", "mac-address", "interface", "type", "status", "dynamic", "complete"),
            NEIGHBOR6 to listOf("address", "mac-address", "interface", "type", "status", "dynamic", "complete"),
            NEIGHBOR to listOf("interface", "identity", "address", "mac-address", "chassis-id", "interface-name", "port-description", "system-description"),
            STORAGE to listOf("description", "type", "size", "used", "free"),
        )
    }
}

private data class Traffic(private val values: List<SnmpValue?>) {
    fun rates(before: Traffic, seconds: Double): Map<String, String> {
        fun delta(at: Int): Long? {
            val now = values[at] as? SnmpValue.Number ?: return null
            val then = before.values[at] as? SnmpValue.Number ?: return null
            return if (now.tag == COUNTER64) now.value - then.value else (now.value - then.value) and 0xFFFF_FFFFL
        }
        fun perSecond(value: Long?) = value?.let { (it / seconds).toLong() }?.toString().orEmpty()
        val rx = delta(0) ?: delta(8)
        val tx = delta(1) ?: delta(9)
        val rxPackets = (2..4).mapNotNull(::delta).takeIf { it.isNotEmpty() }?.sum()
        val txPackets = (5..7).mapNotNull(::delta).takeIf { it.isNotEmpty() }?.sum()
        return mapOf(
            "rx-bits-per-second" to perSecond(rx?.times(8)),
            "tx-bits-per-second" to perSecond(tx?.times(8)),
            "rx-packets-per-second" to perSecond(rxPackets),
            "tx-packets-per-second" to perSecond(txPackets),
        )
    }
}

private class Inet(val type: Int, val bytes: ByteArray, val next: Int) {
    // ipv4z and ipv6z carry four bytes of zone after the address
    val v6 get() = type == 2 || type == 4

    fun text() = if (v6) formatIpv6(bytes.copyOf(16)) else bytes.take(4).joinToString(".") { "${it.toInt() and 0xFF}" }
}

private fun inet(index: List<Long>, at: Int): Inet? {
    val type = index.getOrNull(at)?.toInt() ?: return null
    val length = index.getOrNull(at + 1)?.toInt() ?: return null
    val fits = when (type) {
        1 -> length == 4
        2 -> length == 16
        3 -> length == 8
        4 -> length == 20
        else -> false
    }
    if (!fits || at + 2 + length > index.size) return null
    return Inet(type, ByteArray(length) { index[at + 2 + it].toByte() }, at + 2 + length)
}

private fun network(address: ByteArray, prefix: Int) = ByteArray(4) { i ->
    val bits = (prefix - i * 8).coerceIn(0, 8)
    (address[i].toInt() and (0xFF shl (8 - bits)) and 0xFF).toByte()
}.joinToString(".") { "${it.toInt() and 0xFF}" }

private fun interfaceName(names: Map<Long, String>, value: SnmpValue?) = number(value)?.let { interfaceName(names, it) }.orEmpty()

private fun interfaceName(names: Map<Long, String>, index: Long) = if (index == 0L) "" else names[index] ?: "$index"

private fun number(value: SnmpValue?) = (value as? SnmpValue.Number)?.value

private fun packets(columns: List<Oid>, legacy: Oid, value: (Oid) -> SnmpValue?): String {
    val counted = columns.mapNotNull { (value(it) as? SnmpValue.Number)?.value }
    return if (counted.isNotEmpty()) counted.reduce { a, b -> a + b }.toULong().toString() else text(value(legacy))
}

private fun rate(bits: Long?): String = when {
    bits == null || bits <= 0 -> ""
    bits % 1_000_000_000 == 0L -> "${bits / 1_000_000_000}Gbps"
    bits % 1_000_000 == 0L -> "${bits / 1_000_000}Mbps"
    bits % 1_000 == 0L -> "${bits / 1_000}kbps"
    else -> "${bits}bps"
}

private fun mac(value: SnmpValue?): String = (value as? SnmpValue.Octets)?.bytes?.let(::hex).orEmpty()

internal fun text(value: SnmpValue?): String = when (value) {
    null, SnmpValue.Null, is SnmpValue.Missing -> ""
    is SnmpValue.Number -> if (value.tag == COUNTER64) value.value.toULong().toString() else "${value.value}"
    is SnmpValue.Octets -> value.bytes.decodeToString().takeIf { readable(it) } ?: hex(value.bytes)
    is SnmpValue.Address -> value.bytes.joinToString(".") { "${it.toInt() and 0xFF}" }
    is SnmpValue.Id -> value.oid.toString()
    is SnmpValue.Other -> hex(value.bytes)
}

private fun readable(text: String) = '�' !in text && text.none { it.isISOControl() && it !in "\t\r\n" }

private fun hex(bytes: ByteArray) = bytes.joinToString(":") { (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') }

private val interfaceTypes = mapOf(
    1L to "other", 6L to "ether", 23L to "ppp", 24L to "loopback", 53L to "virtual", 62L to "ether", 71L to "wlan", 117L to "ether",
    131L to "tunnel", 135L to "vlan", 136L to "vlan", 161L to "bond", 209L to "bridge",
)
private val statuses = listOf("up", "down", "testing", "unknown", "dormant", "not-present", "lower-layer-down")
private val origins = mapOf(1L to "other", 2L to "manual", 4L to "dhcp", 5L to "link-layer", 6L to "random")
private val addressStatuses = listOf("preferred", "deprecated", "invalid", "inaccessible", "unknown", "tentative", "duplicate", "optimistic")
private val routeTypes = listOf("other", "reject", "local", "remote", "blackhole")
private val protocols = listOf(
    "other", "local", "static", "icmp", "egp", "ggp", "hello", "rip", "is-is", "es-is", "igrp", "bbn-spf-igp", "ospf", "bgp", "idpr",
    "eigrp", "dvmrp", "rpl", "dhcp", "ttdp",
)
private val neighbourTypes = listOf("other", "invalid", "dynamic", "static", "local")
private val neighbourStates = listOf("reachable", "stale", "delay", "probe", "invalid", "unknown", "incomplete")
private val storageTypes = listOf("other", "ram", "virtual-memory", "fixed-disk", "removable-disk", "floppy-disk", "compact-disc", "ram-disk", "flash-memory", "network-disk")

private val SYS_DESCR = oid("1.3.6.1.2.1.1.1.0")
private val SYS_OBJECT_ID = oid("1.3.6.1.2.1.1.2.0")
private val SYS_UP_TIME = oid("1.3.6.1.2.1.1.3.0")
private val SYS_CONTACT = oid("1.3.6.1.2.1.1.4.0")
private val SYS_NAME = oid("1.3.6.1.2.1.1.5.0")
private val SYS_LOCATION = oid("1.3.6.1.2.1.1.6.0")

private val IF_DESCR = oid("1.3.6.1.2.1.2.2.1.2")
private val IF_TYPE = oid("1.3.6.1.2.1.2.2.1.3")
private val IF_MTU = oid("1.3.6.1.2.1.2.2.1.4")
private val IF_SPEED = oid("1.3.6.1.2.1.2.2.1.5")
private val IF_PHYS = oid("1.3.6.1.2.1.2.2.1.6")
private val IF_ADMIN = oid("1.3.6.1.2.1.2.2.1.7")
private val IF_OPER = oid("1.3.6.1.2.1.2.2.1.8")
private val IF_IN_OCTETS = oid("1.3.6.1.2.1.2.2.1.10")
private val IF_IN_UCAST = oid("1.3.6.1.2.1.2.2.1.11")
private val IF_IN_DISCARDS = oid("1.3.6.1.2.1.2.2.1.13")
private val IF_IN_ERRORS = oid("1.3.6.1.2.1.2.2.1.14")
private val IF_OUT_OCTETS = oid("1.3.6.1.2.1.2.2.1.16")
private val IF_OUT_UCAST = oid("1.3.6.1.2.1.2.2.1.17")
private val IF_OUT_DISCARDS = oid("1.3.6.1.2.1.2.2.1.19")
private val IF_OUT_ERRORS = oid("1.3.6.1.2.1.2.2.1.20")
private val IF_NAME = oid("1.3.6.1.2.1.31.1.1.1.1")
private val IF_HC_IN_OCTETS = oid("1.3.6.1.2.1.31.1.1.1.6")
private val IF_HC_IN_UCAST = oid("1.3.6.1.2.1.31.1.1.1.7")
private val IF_HC_IN_MULTICAST = oid("1.3.6.1.2.1.31.1.1.1.8")
private val IF_HC_IN_BROADCAST = oid("1.3.6.1.2.1.31.1.1.1.9")
private val IF_HC_OUT_OCTETS = oid("1.3.6.1.2.1.31.1.1.1.10")
private val IF_HC_OUT_UCAST = oid("1.3.6.1.2.1.31.1.1.1.11")
private val IF_HC_OUT_MULTICAST = oid("1.3.6.1.2.1.31.1.1.1.12")
private val IF_HC_OUT_BROADCAST = oid("1.3.6.1.2.1.31.1.1.1.13")
private val IF_HIGH_SPEED = oid("1.3.6.1.2.1.31.1.1.1.15")
private val IF_ALIAS = oid("1.3.6.1.2.1.31.1.1.1.18")
private val INTERFACE_COLUMNS = listOf(
    IF_DESCR, IF_TYPE, IF_MTU, IF_SPEED, IF_PHYS, IF_ADMIN, IF_OPER, IF_IN_OCTETS, IF_IN_UCAST, IF_IN_DISCARDS, IF_IN_ERRORS,
    IF_OUT_OCTETS, IF_OUT_UCAST, IF_OUT_DISCARDS, IF_OUT_ERRORS, IF_NAME, IF_HC_IN_OCTETS, IF_HC_IN_UCAST, IF_HC_IN_MULTICAST,
    IF_HC_IN_BROADCAST, IF_HC_OUT_OCTETS, IF_HC_OUT_UCAST, IF_HC_OUT_MULTICAST, IF_HC_OUT_BROADCAST, IF_HIGH_SPEED, IF_ALIAS,
)
private val TRAFFIC = listOf(
    IF_HC_IN_OCTETS, IF_HC_OUT_OCTETS, IF_HC_IN_UCAST, IF_HC_IN_MULTICAST, IF_HC_IN_BROADCAST, IF_HC_OUT_UCAST, IF_HC_OUT_MULTICAST,
    IF_HC_OUT_BROADCAST, IF_IN_OCTETS, IF_OUT_OCTETS,
)

private val IP_ADDRESS_IF_INDEX = oid("1.3.6.1.2.1.4.34.1.3")
private val IP_ADDRESS_TYPE = oid("1.3.6.1.2.1.4.34.1.4")
private val IP_ADDRESS_PREFIX = oid("1.3.6.1.2.1.4.34.1.5")
private val IP_ADDRESS_ORIGIN = oid("1.3.6.1.2.1.4.34.1.6")
private val IP_ADDRESS_STATUS = oid("1.3.6.1.2.1.4.34.1.7")
private val IP_AD_ENT_IF_INDEX = oid("1.3.6.1.2.1.4.20.1.2")
private val IP_AD_ENT_NET_MASK = oid("1.3.6.1.2.1.4.20.1.3")

private val INET_ROUTE_IF_INDEX = oid("1.3.6.1.2.1.4.24.7.1.7")
private val INET_ROUTE_TYPE = oid("1.3.6.1.2.1.4.24.7.1.8")
private val INET_ROUTE_PROTO = oid("1.3.6.1.2.1.4.24.7.1.9")
private val INET_ROUTE_METRIC = oid("1.3.6.1.2.1.4.24.7.1.12")
private val IP_CIDR_ROUTE_IF_INDEX = oid("1.3.6.1.2.1.4.24.4.1.5")
private val IP_CIDR_ROUTE_TYPE = oid("1.3.6.1.2.1.4.24.4.1.6")
private val IP_CIDR_ROUTE_PROTO = oid("1.3.6.1.2.1.4.24.4.1.7")
private val IP_CIDR_ROUTE_METRIC = oid("1.3.6.1.2.1.4.24.4.1.11")
private val IP_ROUTE_IF_INDEX = oid("1.3.6.1.2.1.4.21.1.2")
private val IP_ROUTE_METRIC = oid("1.3.6.1.2.1.4.21.1.3")
private val IP_ROUTE_NEXT_HOP = oid("1.3.6.1.2.1.4.21.1.7")
private val IP_ROUTE_TYPE = oid("1.3.6.1.2.1.4.21.1.8")
private val IP_ROUTE_PROTO = oid("1.3.6.1.2.1.4.21.1.9")
private val IP_ROUTE_MASK = oid("1.3.6.1.2.1.4.21.1.11")

private val IP_NET_TO_PHYSICAL_PHYS = oid("1.3.6.1.2.1.4.35.1.4")
private val IP_NET_TO_PHYSICAL_TYPE = oid("1.3.6.1.2.1.4.35.1.6")
private val IP_NET_TO_PHYSICAL_STATE = oid("1.3.6.1.2.1.4.35.1.7")
private val IP_NET_TO_MEDIA_PHYS = oid("1.3.6.1.2.1.4.22.1.2")
private val IP_NET_TO_MEDIA_TYPE = oid("1.3.6.1.2.1.4.22.1.4")

private val HR_SYSTEM_UPTIME = oid("1.3.6.1.2.1.25.1.1.0")
private val HR_SYSTEM_DATE = oid("1.3.6.1.2.1.25.1.2.0")
private val HR_STORAGE_TYPES = oid("1.3.6.1.2.1.25.2.1")
private val HR_STORAGE_RAM = oid("1.3.6.1.2.1.25.2.1.2")
private val HR_STORAGE_TYPE = oid("1.3.6.1.2.1.25.2.3.1.2")
private val HR_STORAGE_DESCR = oid("1.3.6.1.2.1.25.2.3.1.3")
private val HR_STORAGE_UNITS = oid("1.3.6.1.2.1.25.2.3.1.4")
private val HR_STORAGE_SIZE = oid("1.3.6.1.2.1.25.2.3.1.5")
private val HR_STORAGE_USED = oid("1.3.6.1.2.1.25.2.3.1.6")
private val HR_PROCESSOR_LOAD = oid("1.3.6.1.2.1.25.3.3.1.2")

private val CPU_RAW = listOf("50", "51", "52", "53", "54").map { oid("1.3.6.1.4.1.2021.11.$it.0") }

private val LLDP_REM_CHASSIS_SUBTYPE = oid("1.0.8802.1.1.2.1.4.1.1.4")
private val LLDP_REM_CHASSIS_ID = oid("1.0.8802.1.1.2.1.4.1.1.5")
private val LLDP_REM_PORT_SUBTYPE = oid("1.0.8802.1.1.2.1.4.1.1.6")
private val LLDP_REM_PORT_ID = oid("1.0.8802.1.1.2.1.4.1.1.7")
private val LLDP_REM_PORT_DESC = oid("1.0.8802.1.1.2.1.4.1.1.8")
private val LLDP_REM_SYS_NAME = oid("1.0.8802.1.1.2.1.4.1.1.9")
private val LLDP_REM_SYS_DESC = oid("1.0.8802.1.1.2.1.4.1.1.10")
private val LLDP_COLUMNS = listOf(
    LLDP_REM_CHASSIS_SUBTYPE, LLDP_REM_CHASSIS_ID, LLDP_REM_PORT_SUBTYPE, LLDP_REM_PORT_ID, LLDP_REM_PORT_DESC, LLDP_REM_SYS_NAME,
    LLDP_REM_SYS_DESC,
)
private val LLDP_LOC_PORT_ID = oid("1.0.8802.1.1.2.1.3.7.1.3")
private val LLDP_LOC_PORT_DESC = oid("1.0.8802.1.1.2.1.3.7.1.4")
private val LLDP_REM_MAN_ADDR_IF_SUBTYPE = oid("1.0.8802.1.1.2.1.4.2.1.3")
