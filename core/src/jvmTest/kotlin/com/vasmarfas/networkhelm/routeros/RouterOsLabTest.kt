package com.vasmarfas.networkhelm.routeros

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.LinkState
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.SafeModeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.model.lacks
import com.vasmarfas.networkhelm.offline.openConfig
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.PackTab
import com.vasmarfas.networkhelm.packs.bundledMenu
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.routeros.api.ApiTrapException
import com.vasmarfas.networkhelm.search.Finding
import com.vasmarfas.networkhelm.search.find
import com.vasmarfas.networkhelm.search.lookup
import com.vasmarfas.networkhelm.session.connectWithSsh
import com.vasmarfas.networkhelm.session.rows
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.connectSsh
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustKind
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RouterOsLabTest {

    private val bridges = MenuPath.parse("interface/bridge")
    private val addresses = MenuPath.parse("ip/address")
    private val rules = MenuPath.parse("ip/firewall/filter")

    private fun chr(protocol: RouterOsProtocol = RouterOsProtocol.ApiSsl, block: suspend CoroutineScope.(RouterOsSession) -> Unit) = runBlocking {
        val lab = Lab.require("chr.host", "chr.user", "chr.password")
        val scope = CoroutineScope(Dispatchers.Default)
        val trust = TrustPolicy(MemoryTrustStore()) { true }
        val login = RouterOsLogin(lab.getValue("chr.host"), lab.getValue("chr.user"), lab.getValue("chr.password"), protocol)
        val session = connectRouterOs(login, trust, scope)
        try {
            withTimeout(60.seconds) { block(session) }
        } finally {
            session.close()
            scope.cancel()
        }
    }

    private fun chr6(block: suspend CoroutineScope.(RouterOsSession, String) -> Unit) = runBlocking {
        val lab = Lab.require("chr6.host", "chr6.user", "chr6.password")
        val scope = CoroutineScope(Dispatchers.Default)
        val login = RouterOsLogin(lab.getValue("chr6.host"), lab.getValue("chr6.user"), lab.getValue("chr6.password"), RouterOsProtocol.Api)
        val session = connectRouterOs(login, TrustPolicy(MemoryTrustStore()) { true }, scope)
        try {
            withTimeout(60.seconds) { block(session, lab.getValue("chr6.host")) }
        } finally {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun routerOs6DescribesItsMenusByWhatPrintAnswers() = chr6 { session, host ->
        assertTrue(session.device.version.startsWith("6.49"), session.device.version)
        assertEquals("enterprise", session.device.deviceMode["mode"])
        assertTrue(session.device.id.orEmpty().isNotEmpty(), "CHR6 tells its system-id")
        assertTrue("write" in session.device.policies, session.device.policies.toString())
        assertEquals(SafeModeKind.None, session.capabilities.safeMode, "without SSH a v6 router has no Safe Mode")
        assertFalse(session.capabilities.export)
        val list = session.describe(addresses)
        assertEquals(NodeKind.List, list.kind)
        assertNotNull(list.argument("address"))
        assertEquals(NodeKind.Settings, session.describe(MenuPath.parse("ip/dns")).kind)
        assertEquals(NodeKind.Command, session.describe(MenuPath.parse("tool/torch")).kind)
        for (missing in listOf("interface/wifi", "container", "system/keymat-provider")) {
            assertEquals(ErrorCategory.MissingItem, assertFailsWith<DeviceException>(missing) { session.describe(MenuPath.parse(missing)) }.category, missing)
        }
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { session.describe(MenuPath.parse("ip")) }.category)
        val rows = session.watch(addresses).take(1).toList().filterIsInstance<ListEvent.Snapshot>().single().items
        assertTrue(rows.any { it["address"] == "$host/24" }, rows.toString())
        assertNotNull(session.get(MenuPath.parse("system/resource"), null, listOf("uptime"))?.get("uptime"))
        assertNotNull(session.get(MenuPath.parse("system/clock"), null, listOf("time"))?.get("time"))
    }

    @Test
    fun routerOs6HasThePathsOfItsWindowsAndMenu() = chr6 { session, _ ->
        fun older(until: String?) = until?.let(RouterOsVersion::parse)?.let { it <= RouterOsVersion(7, 0) } == true
        fun entries(items: List<MenuEntry>): List<MenuEntry> = items.flatMap { listOf(it) + entries(it.items) }
        val running = RouterOsVersion.parse(session.device.version)
        fun active(tab: PackTab) = running?.within(tab.since, tab.until) != false
        val packs = bundledPacks().values.filter { older(it.until) }
        val left = packs.flatMap { pack -> pack.window.tabs.filterNot(::active).map { it.path } }.toSet()
        val paths = packs.flatMap { pack -> pack.window.tabs.filter(::active).map { it.path } + pack.paths.keys.filterNot { it in left } } +
            entries(bundledMenu().items).filter { older(it.until) }.mapNotNull { it.path }
        val missing = paths.distinct().filter { path ->
            try {
                session.describe(MenuPath.parse(path))
                false
            } catch (e: DeviceException) {
                e.category == ErrorCategory.MissingItem
            }
        }
        assertTrue(paths.isNotEmpty() && missing.isEmpty(), "RouterOS 6.49 lacks $missing")
    }

    @Test
    fun routerOs6HasNoRest() {
        val lab = Lab.require("chr6.host")
        val login = RouterOsLogin(lab.getValue("chr6.host"), "rd-test", "", RouterOsProtocol.Rest)
        runBlocking { assertFailsWith<ApiTrapException> { connectRouterOs(login, TrustPolicy(MemoryTrustStore()) { false }, this) } }
    }

    private suspend fun RouterOsSession.removeAll(path: MenuPath, filter: Filter) {
        val ids = watch(path, filter, listOf()).take(1).toList().filterIsInstance<ListEvent.Snapshot>().single().items.map { it.id }
        if (ids.isNotEmpty()) remove(path, ids)
    }

    @Test
    fun theConsoleTellsHowItReadsAScript() = chr { session ->
        val script = ":local rdTest \"a\"\n# проверка rd-test\n:put \$rdTest\n:foo"
        val classes = assertNotNull(highlight(session, script))
        assertEquals(script.length, classes.size)
        assertEquals(listOf("dir") + List(5) { "cmd" }, classes.take(6))
        assertEquals(List(6) { "variable-local" }, classes.subList(7, 13))
        assertEquals(setOf("comment"), classes.subList(script.indexOf('#'), script.indexOf(":put")).toSet())
        assertEquals(List(3) { "obj-inactive" }, classes.takeLast(3))
    }

    @Test
    fun theSearchFindsTheAddressOfTheRouter() = chr { session ->
        val own = assertNotNull(session.get(addresses, null)?.get("address")).substringBefore('/')
        val hits = find(session, assertNotNull(lookup(own))).toList().filterIsInstance<Finding.Hit>()
        val places = hits.map { it.place.path.toString() }.toSet()
        assertTrue("ip/address" in places, places.toString())
        val routes = MenuPath.parse("ip/route")
        assertTrue(session.count(routes, Filter.Not(Filter.Equals("dynamic", "true"))) < session.count(routes))
    }

    @Test
    fun theExportOfTheRouterReadsBackAsItsConfiguration() = chr { session ->
        val opened = assertNotNull(openConfig("chr.rsc", session.export().encodeToByteArray()))
        assertEquals(session.device.identity, opened.device.identity)
        assertEquals(session.device.version, opened.device.version)
        assertEquals(NodeKind.Settings, opened.describe(MenuPath.parse("system/identity")).kind)
        assertTrue(opened.describe(MenuPath.Root).children.size > 3, opened.describe(MenuPath.Root).children.toString())
    }

    @Test
    fun deviceAndCapabilitiesComeFromTheRouter() = chr { session ->
        assertTrue(session.device.version.startsWith("7."), session.device.version)
        assertEquals("x86_64", session.device.architecture)
        assertTrue("routeros" in session.device.packages, session.device.packages.toString())
        assertTrue("write" in session.device.policies, session.device.policies.toString())
        assertEquals("advanced", session.device.deviceMode["mode"])
        assertTrue(session.device.id.orEmpty().isNotEmpty(), "CHR tells its system-id")
        assertEquals(SafeModeKind.ApiSession, session.capabilities.safeMode)
    }

    @Test
    fun addressIsAddedChangedAndRemovedWhileWatched() = chr { session -> addressWhileWatched(session) }

    @Test
    fun addressIsAddedChangedAndRemovedWhileWatchedOnRouterOs6() = chr6 { session, _ -> addressWhileWatched(session) }

    private suspend fun CoroutineScope.addressWhileWatched(session: RouterOsSession) {
        val onBridge = Filter.Equals("interface", "rd-test-br")
        session.removeAll(addresses, onBridge)
        session.removeAll(bridges, Filter.Equals("name", "rd-test-br"))
        val bridge = session.add(bridges, mapOf("name" to "rd-test-br", "comment" to "rd-test"))
        try {
            val events = Channel<ListEvent>(Channel.UNLIMITED)
            val watch = launch { session.watch(addresses, onBridge).collect { events.send(it) } }
            assertEquals(ListEvent.Snapshot(emptyList()), events.receive())
            val id = session.add(addresses, mapOf("address" to "192.0.2.1/32", "interface" to "rd-test-br", "comment" to "rd-test"))
            assertEquals("rd-test", assertIs<ListEvent.Upsert>(events.receive()).item["comment"])
            session.set(addresses, id, mapOf("comment" to "rd-test-changed"))
            assertEquals("rd-test-changed", assertIs<ListEvent.Upsert>(events.receive()).item["comment"])
            assertEquals(1, session.count(addresses, onBridge))
            if (session.capabilities.export) assertTrue(session.export(addresses).contains("rd-test-changed"))
            session.remove(addresses, listOf(id))
            assertEquals(ListEvent.Removed(id), events.receive())
            watch.cancel()
        } finally {
            session.removeAll(addresses, onBridge)
            session.remove(bridges, listOf(bridge))
        }
    }

    @Test
    fun moveArrivesWithTheElementItNowPrecedes() = chr { session -> moveWhileWatched(session) }

    @Test
    fun moveArrivesWithTheElementItNowPrecedesOnRouterOs6() = chr6 { session, _ -> moveWhileWatched(session) }

    private suspend fun CoroutineScope.moveWhileWatched(session: RouterOsSession) {
        val chain = Filter.Equals("chain", "rd-test")
        session.removeAll(rules, chain)
        val rule = mapOf("chain" to "rd-test", "action" to "passthrough", "disabled" to "yes")
        val first = session.add(rules, rule + ("comment" to "rd-test-0"))
        val second = session.add(rules, rule + ("comment" to "rd-test-1"))
        try {
            val events = Channel<ListEvent>(Channel.UNLIMITED)
            val watch = launch { session.watch(rules, chain, listOf("comment")).collect { events.send(it) } }
            assertEquals(listOf(first, second), assertIs<ListEvent.Snapshot>(events.receive()).items.map { it.id })
            session.move(rules, listOf(second), before = first)
            var moved = assertIs<ListEvent.Upsert>(events.receive())
            while (moved.item.id != second) moved = assertIs<ListEvent.Upsert>(events.receive())
            assertEquals(first, moved.next)
            watch.cancel()
        } finally {
            session.removeAll(rules, chain)
        }
    }

    @Test
    fun settingsMenuWithoutListenIsPolled() = chr { session -> settingsPolled(session) }

    @Test
    fun settingsMenuWithoutListenIsPolledOnRouterOs6() = chr6 { session, _ -> settingsPolled(session) }

    private suspend fun settingsPolled(session: RouterOsSession) {
        val events = session.watch(MenuPath.parse("system/resource"), properties = listOf("uptime")).take(2).toList()
        assertIs<ListEvent.Snapshot>(events[0])
        assertTrue(assertIs<ListEvent.Snapshot>(events[1]).items.single()["uptime"].orEmpty().isNotEmpty())
    }

    @Test
    fun restReadsWritesAndPolls() = chr(RouterOsProtocol.Rest) { session -> restRoundTrip(session) }

    @Test
    fun restReadsWritesAndPollsOverHttps() {
        Lab.require("chr.restSsl")
        chr(RouterOsProtocol.RestSsl) { session -> restRoundTrip(session) }
    }

    private suspend fun CoroutineScope.restRoundTrip(session: RouterOsSession) {
        val lists = MenuPath.parse("ip/firewall/address-list")
        val mine = Filter.Equals("list", "rd-test-rest")
        assertEquals(SafeModeKind.None, session.capabilities.safeMode)
        assertTrue("write" in session.device.policies, session.device.policies.toString())
        session.removeAll(lists, mine)
        val events = Channel<ListEvent>(Channel.UNLIMITED)
        val watch = launch { session.watch(lists, mine).collect { events.send(it) } }
        try {
            assertEquals(ListEvent.Snapshot(emptyList()), events.receive())
            val id = session.add(lists, mapOf("list" to "rd-test-rest", "address" to "192.0.2.71", "comment" to "rd-test"))
            assertEquals(listOf(id), assertIs<ListEvent.Snapshot>(events.receive()).items.map { it.id })
            assertEquals(1, session.count(lists, mine))
            session.remove(lists, listOf(id))
            assertEquals(ListEvent.Snapshot(emptyList()), events.receive())
        } finally {
            watch.cancel()
            session.removeAll(lists, mine)
        }
    }

    @Test
    fun windowSchemasComeFromInspect() = chr { session ->
        val interfaces = listOf("interface", "interface/list", "interface/ethernet", "interface/vlan", "interface/bonding", "interface/bridge")
            .map { path -> async { session.describe(MenuPath.parse(path)) } }
            .awaitAll()
        assertTrue(interfaces.all { it.kind == NodeKind.List }, interfaces.map { "${it.path} ${it.kind}" }.toString())
        val ethernet = interfaces[2]
        assertEquals(ValueType.Mac, ethernet.argument("mac-address")?.type)
        assertEquals(ValueType.Time, ethernet.argument("arp-timeout")?.type)
        assertEquals(ValueType.Text(), ethernet.argument("name")?.type)
        assertEquals(ValueType.YesNo, ethernet.argument("disabled")?.type)
        assertEquals(ValueType.Integer(1, 4094, scaled = true), interfaces[3].argument("vlan-id")?.type)
        val mode = interfaces[5].argument("protocol-mode")
        assertEquals(ValueType.Enumeration(closed = true), mode?.type)
        assertEquals(setOf("none", "stp", "rstp", "mstp"), mode?.choices?.toSet())
        assertEquals(ValueType.Enumeration(), session.describe(MenuPath.parse("ip/address")).argument("interface")?.type)
        val filter = session.describe(MenuPath.parse("ip/firewall/filter"))
        assertEquals(ValueType.Ip4Range, filter.argument("src-address")?.type)
        assertEquals(true, filter.argument("src-address")?.negatable)
        assertEquals(ValueType.PortList, filter.argument("dst-port")?.type)
        assertTrue("tcp" in filter.argument("protocol")?.choices.orEmpty())
        val dns = session.describe(MenuPath.parse("ip/dns"))
        assertEquals(NodeKind.Settings, dns.kind)
        assertEquals(ValueType.Integer(64, 4294967295, "KiB", scaled = true), dns.argument("cache-size")?.type)
        val connections = MenuPath.parse("ip/firewall/connection")
        assertEquals(listOf(false, true, true), listOf(connections, MenuPath.parse("ipv6/firewall/connection"), MenuPath.parse("ip/firewall/filter")).map { session.describe(it).listens })
        assertEquals(emptyList(), session.counters(connections, listOf("orig-bytes")).toList())
        assertEquals(ValueType.Ip, session.describe(MenuPath.parse("ip/dns/static")).argument("address")?.type)
        assertEquals(ValueType.Ip6Prefix, session.describe(MenuPath.parse("ipv6/address")).argument("address")?.type)
        assertEquals(ValueType.Ip6Prefix, session.describe(MenuPath.parse("ipv6/pool")).argument("prefix")?.type)
        assertEquals(NodeKind.Directory, session.describe(MenuPath.parse("ip/firewall")).kind)
        val ping = session.describe(MenuPath.parse("tool/ping"))
        assertEquals(NodeKind.Command, ping.kind)
        assertEquals(listOf(ValueType.Switch, ValueType.YesNo), listOf(ping.argument("do-not-fragment")?.type, ping.argument("arp-ping")?.type))
        assertEquals(ErrorCategory.MissingItem, assertFailsWith<DeviceException> { session.describe(MenuPath.parse("rd-test-none")) }.category)
    }

    @Test
    fun mostFieldsOfMainWindowsGetAType() = chr { session ->
        val fields = listOf(
            "interface", "interface/list", "interface/ethernet", "interface/vlan", "interface/bonding", "interface/bridge", "interface/bridge/port",
            "ip/address", "ip/route", "ip/firewall/filter", "ip/firewall/nat", "ip/firewall/address-list", "ip/dhcp-server", "ip/dhcp-server/network",
            "ip/dns", "ip/dns/static", "ip/pool", "system/identity", "system/scheduler",
        ).map { path -> async { session.describe(MenuPath.parse(path)) } }.awaitAll().flatMap { it.arguments }
        val unparsed = fields.filter { it.type is ValueType.Unparsed }
        assertTrue(unparsed.size * 10 < fields.size, "${unparsed.size} of ${fields.size}: ${unparsed.map { "${it.name}=${it.type}" }}")
    }

    @Test
    fun safeModeRollsBackOrKeepsTheChanges() = chr { session ->
        val list = MenuPath.parse("ip/firewall/address-list")
        val entries = Filter.Equals("list", "rd-test-safe")
        session.removeAll(list, entries)
        val safeMode = assertNotNull(session.safeMode)
        try {
            assertTrue(safeMode.enter())
            session.add(list, mapOf("list" to "rd-test-safe", "address" to "192.0.2.80", "comment" to "rd-test"))
            assertEquals(1, safeMode.actions.first { it > 0 })
            safeMode.rollback()
            assertFalse(safeMode.active.first())
            assertEquals(0, session.count(list, entries))
            assertTrue(safeMode.enter())
            session.add(list, mapOf("list" to "rd-test-safe", "address" to "192.0.2.81", "comment" to "rd-test"))
            safeMode.commit()
            assertEquals(1, session.count(list, entries))
        } finally {
            session.removeAll(list, entries)
        }
    }

    @Test
    fun theConsoleHoldsSafeModeOverSsh() = chr { session -> consoleSafeMode(session, "chr") }

    @Test
    fun theConsoleHoldsSafeModeOverSshOnRouterOs6() = chr6 { session, _ -> consoleSafeMode(session, "chr6") }

    @Test
    fun aRouterBefore718HoldsSafeModeInTheConsoleTheSessionOpens() = runBlocking {
        val lab = Lab.require("chr717.host", "chr717.user", "chr717.password", "chr717.ssh.hostKey")
        val host = lab.getValue("chr717.host")
        val store = MemoryTrustStore().also { it.put(host, 22, TrustKind.Ssh, lab.getValue("chr717.ssh.hostKey")) }
        val scope = CoroutineScope(Dispatchers.Default)
        val login = RouterOsLogin(host, lab.getValue("chr717.user"), lab.getValue("chr717.password"), RouterOsProtocol.Api)
        val session = connectWithSsh(login, TrustPolicy(store) { false }, scope)
        val list = MenuPath.parse("ip/firewall/address-list")
        val entries = Filter.Equals("list", "rd-test-console")
        suspend fun clear() = session.watch(list, entries).rows().drop(1).first().map { it.id }.takeIf { it.isNotEmpty() }?.let { session.remove(list, it) }
        try {
            withTimeout(60.seconds) {
                assertTrue(session.device.version.startsWith("7.17"), session.device.version)
                assertEquals(SafeModeKind.SshConsole, session.capabilities.safeMode)
                clear()
                val safeMode = assertNotNull(session.safeMode)
                assertTrue(safeMode.enter())
                session.add(list, mapOf("list" to "rd-test-console", "address" to "192.0.2.86", "comment" to "rd-test"))
                assertEquals(1, safeMode.actions.first { it > 0 })
                safeMode.rollback()
                withTimeout(10.seconds) { while (session.count(list, entries) > 0) delay(200) }
                assertTrue(safeMode.enter())
                session.add(list, mapOf("list" to "rd-test-console", "address" to "192.0.2.87", "comment" to "rd-test"))
                safeMode.commit()
                delay(2.seconds)
                assertEquals(1, session.count(list, entries))
            }
        } finally {
            clear()
            session.close()
            scope.cancel()
        }
    }

    private suspend fun CoroutineScope.consoleSafeMode(session: RouterOsSession, prefix: String) {
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password", "$prefix.ssh.hostKey")
        val ssh = connectSsh(
            lab.getValue("$prefix.host"), 22, lab.getValue("$prefix.user"), SshAuth.Password(lab.getValue("$prefix.password")),
            acceptHostKey = { it.fingerprint == lab.getValue("$prefix.ssh.hostKey") },
        )
        val list = MenuPath.parse("ip/firewall/address-list")
        val entries = Filter.Equals("list", "rd-test-console")
        session.removeAll(list, entries)
        val safeMode = ConsoleSafeMode(ssh, this, { session.count(MenuPath.parse("system/history"), Filter.Equals("floating-undo", "true")) }, 1.seconds)
        try {
            assertTrue(safeMode.enter())
            session.add(list, mapOf("list" to "rd-test-console", "address" to "192.0.2.84", "comment" to "rd-test"))
            assertEquals(1, safeMode.actions.first { it > 0 })
            safeMode.rollback()
            withTimeout(10.seconds) { while (session.count(list, entries) > 0) delay(200) }
            assertTrue(safeMode.enter())
            session.add(list, mapOf("list" to "rd-test-console", "address" to "192.0.2.85", "comment" to "rd-test"))
            safeMode.commit()
            delay(2.seconds)
            assertEquals(1, session.count(list, entries))
        } finally {
            safeMode.rollback()
            session.removeAll(list, entries)
            ssh.close()
        }
    }

    @Test
    fun undoAndRedoTakeTheLastActionBackAndForth() = chr { session -> undoAndRedo(session) }

    @Test
    fun undoAndRedoTakeTheLastActionBackAndForthOnRouterOs6() = chr6 { session, _ -> undoAndRedo(session) }

    private suspend fun undoAndRedo(session: RouterOsSession) {
        val list = MenuPath.parse("ip/firewall/address-list")
        val entries = Filter.Equals("list", "rd-test-undo")
        session.removeAll(list, entries)
        try {
            session.add(list, mapOf("list" to "rd-test-undo", "address" to "192.0.2.82", "comment" to "rd-test"))
            session.run(MenuPath.Root, "undo", emptyMap()).toList()
            assertEquals(0, session.count(list, entries))
            session.run(MenuPath.Root, "redo", emptyMap()).toList()
            assertEquals(1, session.count(list, entries))
        } finally {
            session.removeAll(list, entries)
        }
    }

    @Test
    fun pingStreamsItsRows() = chr { session -> ping(session) }

    @Test
    fun pingStreamsItsRowsOnRouterOs6() = chr6 { session, _ -> ping(session) }

    @Test
    fun theFlagOfPingKeepsABigPacketWhole() = chr { session ->
        Lab.require("chr.disposable")
        val big = mapOf("address" to "192.0.2.1", "interface" to "ether1", "count" to "1", "size" to "1600")
        val refused = "packet too large and cannot be fragmented"
        assertTrue(session.run(MenuPath.Root, "ping", big).toList().none { it["status"] == refused })
        assertEquals(listOf(refused), session.run(MenuPath.Root, "ping", big + ("do-not-fragment" to "yes")).toList().map { it["status"] })
    }

    @Test
    fun resetReturnsTheFieldsWithADefault() = chr { session ->
        Lab.require("chr.disposable")
        val client = MenuPath.parse("system/ntp/client")
        val before = session.get(client, null)?.values.orEmpty()
        try {
            session.set(client, null, mapOf("mode" to "broadcast", "servers" to "192.0.2.123"))
            session.run(client, "reset", emptyMap()).toList()
            val after = session.get(client, null)
            assertEquals(listOf("unicast", "192.0.2.123"), listOf(after?.get("mode"), after?.get("servers")))
        } finally {
            session.set(client, null, mapOf("mode" to before["mode"], "servers" to before["servers"].orEmpty()))
        }
    }

    private suspend fun ping(session: RouterOsSession) {
        val rows = session.run(MenuPath.Root, "ping", mapOf("address" to "127.0.0.1", "count" to "3", "interval" to "200ms")).toList()
        assertEquals(listOf("0", "1", "2"), rows.mapNotNull { it["seq"] })
    }

    @Test
    fun aGroupWithoutTestIsRefusedWhatLacksNames() = runBlocking<Unit> {
        val lab = Lab.require("chr.host", "chr.user", "chr.password", "chr.disposable")
        val scope = CoroutineScope(Dispatchers.Default)
        val trust = TrustPolicy(MemoryTrustStore()) { true }
        val admin = connectRouterOs(RouterOsLogin(lab.getValue("chr.host"), lab.getValue("chr.user"), lab.getValue("chr.password"), RouterOsProtocol.Api), trust, scope)
        val groups = MenuPath.parse("user/group")
        val users = MenuPath.parse("user")
        val password = UUID.randomUUID().toString()
        var group: String? = null
        var user: String? = null
        try {
            group = admin.add(groups, mapOf("name" to "rd-test-limited", "policy" to "api,read,write"))
            user = admin.add(users, mapOf("name" to "rd-test-limited", "group" to "rd-test-limited", "password" to password))
            val limited = connectRouterOs(RouterOsLogin(lab.getValue("chr.host"), "rd-test-limited", password, RouterOsProtocol.Api), trust, scope)
            try {
                assertEquals(setOf("api", "read", "write"), limited.device.policies)
                assertEquals(listOf("test"), limited.device.lacks(MenuPath.parse("tool/ping")))
                assertEquals(listOf("policy"), limited.device.lacks(users))
                assertFalse(limited.device.canTransfer)
                val refused = assertFailsWith<DeviceException> { limited.run(MenuPath.Root, "ping", mapOf("address" to "127.0.0.1", "count" to "1")).toList() }
                assertTrue("not enough permissions" in refused.message.orEmpty(), refused.message)
            } finally {
                limited.close()
            }
        } finally {
            user?.let { admin.remove(users, listOf(it)) }
            group?.let { admin.remove(groups, listOf(it)) }
            admin.close()
            scope.cancel()
        }
    }

    @Test
    fun sshMovedOffItsPortIsFoundThroughTheApi() = runBlocking<Unit> {
        val lab = Lab.require("chr.host", "chr.user", "chr.password", "chr.disposable", "chr.ssh.otherPort")
        val scope = CoroutineScope(Dispatchers.Default)
        val trust = TrustPolicy(MemoryTrustStore()) { true }
        val login = RouterOsLogin(lab.getValue("chr.host"), lab.getValue("chr.user"), lab.getValue("chr.password"), RouterOsProtocol.Api)
        val admin = connectRouterOs(login, trust, scope)
        val services = MenuPath.parse("ip/service")
        val ssh = admin.watch(services, Filter.Equals("name", "ssh")).rows().first { it.isNotEmpty() }.single { it["connection"] != "true" }.id
        try {
            admin.set(services, ssh, mapOf("port" to lab.getValue("chr.ssh.otherPort")))
            val session = connectWithSsh(login, trust, scope)
            try {
                assertEquals(admin.device.identity, assertNotNull(session.ssh).exec(":put [/system identity get name]").output.trim())
            } finally {
                session.close()
            }
        } finally {
            admin.set(services, ssh, mapOf("port" to "22"))
            admin.close()
            scope.cancel()
        }
    }

    @Test
    fun aRouterThatStopsAnsweringIsFoundAndComesBack() = runBlocking<Unit> {
        val lab = Lab.require("chr.host", "chr.user", "chr.password", "chr.disposable")
        val scope = CoroutineScope(Dispatchers.Default)
        val trust = TrustPolicy(MemoryTrustStore()) { true }
        fun login(protocol: RouterOsProtocol) = RouterOsLogin(lab.getValue("chr.host"), lab.getValue("chr.user"), lab.getValue("chr.password"), protocol)
        val session = connectRouterOs(login(RouterOsProtocol.ApiSsl), trust, scope)
        val admin = connectRouterOs(login(RouterOsProtocol.Api), trust, scope)
        suspend fun connections() = admin.count(MenuPath.parse("user/active"), Filter.And(listOf(Filter.Equals("via", "api"), Filter.Has("address"))))
        var rule: String? = null
        try {
            val before = connections()
            val first = admin.watch(rules).rows().drop(1).first().firstOrNull()?.id
            val drop = mapOf("chain" to "input", "protocol" to "tcp", "dst-port" to "8729", "action" to "drop", "comment" to "rd-test-silent")
            rule = admin.add(rules, drop + listOfNotNull(first?.let { "place-before" to it }))
            withTimeout(45.seconds) { session.link.first { it == LinkState.Reconnecting } }
            admin.remove(rules, listOfNotNull(rule))
            rule = null
            withTimeout(90.seconds) { session.link.first { it == LinkState.Up } }
            assertEquals(admin.device.identity, session.device.identity)
            assertNotNull(session.get(MenuPath.parse("system/identity"), null)?.get("name"))
            withTimeout(120.seconds) { while (connections() > before) delay(1_000) }
        } finally {
            rule?.let { admin.remove(rules, listOf(it)) }
            session.close()
            admin.close()
            scope.cancel()
        }
    }

    @Test
    fun countersComeInRoundsOfEveryElement() = chr { session ->
        val rule = session.add(rules, mapOf("chain" to "forward", "action" to "passthrough", "disabled" to "yes", "comment" to "rd-test-counters"))
        try {
            counters(session, rules, listOf("bytes", "packets"))
        } finally {
            session.remove(rules, listOf(rule))
        }
    }

    @Test
    fun countersComeInRoundsOfEveryElementOnRouterOs6() = chr6 { session, _ -> counters(session, MenuPath.parse("interface"), listOf("rx-byte", "tx-byte")) }

    private suspend fun counters(session: RouterOsSession, path: MenuPath, properties: List<String>) {
        val ids = session.watch(path).rows().first { it.isNotEmpty() }.map { it.id }.toSet()
        val rounds = session.counters(path, properties).take(2).toList()
        for (round in rounds) {
            assertEquals(ids, round.keys)
            assertTrue(round.values.all { it.keys == properties.toSet() }, "only the asked properties: $round")
        }
    }

    @Test
    fun countersOfOneElementComeAlone() = chr { session -> countersOfOne(session) }

    @Test
    fun countersOfOneElementComeAloneOnRouterOs6() = chr6 { session, _ -> countersOfOne(session) }

    private suspend fun countersOfOne(session: RouterOsSession) {
        val path = MenuPath.parse("interface")
        val id = session.watch(path).rows().first { it.isNotEmpty() }.first().id
        val rounds = session.counters(path, listOf("rx-byte", "tx-byte"), Filter.Equals(".id", id)).take(2).toList()
        assertTrue(rounds.all { it.keys == setOf(id) }, rounds.toString())
    }
}
