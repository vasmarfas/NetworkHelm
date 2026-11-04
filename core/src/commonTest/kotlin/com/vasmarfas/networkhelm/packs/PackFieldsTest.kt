package com.vasmarfas.networkhelm.packs

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Capabilities
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.SafeModeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.session.SafeMode
import com.vasmarfas.networkhelm.ssh.SshSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackFieldsTest {

    private class PrintedSession(version: String = "6.49.23", private val cpus: Int? = null) : DeviceSession {
        override val device = DeviceInfo(Platform.RouterOs, "lab", version, "x86_64", "CHR", emptySet(), setOf("read", "write"))
        override val capabilities = Capabilities(true, true, true, SafeModeKind.None, terminal = false, files = false, export = false, menuContents = false)
        override suspend fun describe(path: MenuPath) = MenuSchema(
            path, NodeKind.List, emptyList(), listOf("print", "add", "set", "remove"),
            listOf(Argument("dst-address", ValueType.Text()), Argument("active", ValueType.YesNo), Argument("disabled", ValueType.YesNo)),
        )
        override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> {
            val names = mapOf(
                "interface" to listOf("ether1", "bridge1"),
                "interface/bridge" to listOf("bridge1"),
                "interface/list" to listOf("all", "LAN"),
                "ppp/profile" to listOf("default", "rd-test-pppoe"),
                "interface/wireless" to listOf("wlan1"),
            )
            return flowOf(ListEvent.Snapshot(names[path.toString()].orEmpty().mapIndexed { at, name -> Item("*$at", mapOf("name" to name)) }))
        }
        override suspend fun get(path: MenuPath, id: String?, properties: List<String>?): Item? =
            if (path.toString() == "system/resource" && properties == listOf("cpu-count")) cpus?.let { Item("", mapOf("cpu-count" to "$it")) } else null
        override suspend fun count(path: MenuPath, filter: Filter?) = 0
        override suspend fun add(path: MenuPath, values: Map<String, String>): String = error("not used")
        override suspend fun set(path: MenuPath, id: String?, changes: Map<String, String?>) = Unit
        override suspend fun remove(path: MenuPath, ids: List<String>) = Unit
        override suspend fun move(path: MenuPath, ids: List<String>, before: String?) = Unit
        override fun run(path: MenuPath, command: String, arguments: Map<String, String>): Flow<Item> = emptyFlow()
        override suspend fun export(path: MenuPath): String = error("not used")
        override val safeMode: SafeMode? = null
        override val ssh: SshSession? = null
        override fun close() = Unit
    }

    private val pack = Pack(
        menu = listOf("IP", "Routes"),
        window = PackWindow("Routes", listOf(PackTab("Routes", "ip/route"))),
        paths = mapOf(
            "ip/route" to PackPath(readonly = listOf("active"), form = "route"),
            "ip/route/nexthop" to PackPath(columns = listOf("address", "gw-state"), readonly = listOf("address", "gw-state")),
        ),
        forms = mapOf(
            "route" to PackForm(
                tabs = listOf(
                    PackFormTab("General", listOf("dst-address", "gateway", "routing-mark"), until = "7.0"),
                    PackFormTab("General", listOf("dst-address", "gateway", "routing-table"), since = "7.0"),
                    PackFormTab("Status", listOf("active", "gateway-status"), readonly = true),
                    PackFormTab("Rule", listOf("table"), path = "ip/route/rule"),
                ),
            ),
        ),
    )

    @Test
    fun formFieldsOfTheRunningVersionComeAsTextAndReadOnlyPropertiesGo() = runTest {
        val session = PackFieldsSession(PrintedSession(), listOf(pack))
        val route = session.describe(MenuPath.parse("ip/route"))
        assertEquals(listOf("dst-address", "disabled", "gateway", "routing-mark"), route.arguments.map { it.name }.take(4))
        assertEquals(ValueType.Text(), route.argument("gateway")?.type)
        assertEquals(ValueType.YesNo, route.argument("disabled")?.type)
        assertEquals(ValueType.Integer(scaled = true), route.argument("distance")?.type)
        assertEquals(true, route.argument("dynamic")?.readOnly)
        assertEquals(listOf("dst-address", "active", "disabled", "table"), session.describe(MenuPath.parse("ip/route/rule")).arguments.map { it.name }.take(4))
    }

    @Test
    fun anEnumerationOfTik4netComesWithItsValues() = runTest {
        val filter = PackFieldsSession(PrintedSession(), emptyList()).describe(MenuPath.parse("ip/firewall/filter"))
        val state = assertNotNull(filter.argument("connection-state"))
        assertEquals(ValueType.Enumeration(multiple = true), state.type)
        assertEquals(listOf("invalid", "established", "related", "new", "untracked"), state.choices)
        assertTrue(state.negatable)
        assertEquals(ValueType.PortList, filter.argument("dst-port")?.type)
    }

    @Test
    fun aListOfTik4netIsLines() = runTest {
        val session = PackFieldsSession(PrintedSession(), emptyList())
        assertEquals(ValueType.Many(ValueType.Text()), session.describe(MenuPath.parse("interface/bridge/vlan")).argument("tagged")?.type)
        val topics = assertNotNull(session.describe(MenuPath.parse("system/logging")).argument("topics"))
        assertEquals(ValueType.Many(ValueType.Text()), topics.type)
        assertFalse(topics.negatable)
    }

    @Test
    fun aFieldOfAnotherListOffersTheNamesTheRouterHas() = runTest {
        val session = PackFieldsSession(PrintedSession(), emptyList())
        val port = session.describe(MenuPath.parse("interface/bridge/port"))
        assertEquals(ValueType.Text(), port.argument("interface")?.type)
        assertEquals(listOf("ether1", "bridge1"), port.argument("interface")?.choices)
        assertEquals(listOf("bridge1"), port.argument("bridge")?.choices)
        val filter = session.describe(MenuPath.parse("ip/firewall/filter"))
        assertEquals(listOf("all", "LAN"), filter.argument("in-interface-list")?.choices)
        assertEquals(true, filter.argument("in-interface")?.negatable)
        assertEquals(listOf("default", "rd-test-pppoe"), session.describe(MenuPath.parse("ppp/secret")).argument("profile")?.choices)
    }

    @Test
    fun theScannerOfAWirelessInterfaceOffersTheWirelessInterfaces() = runTest {
        val scan = Pack(
            menu = listOf("Wireless"),
            window = PackWindow("Wireless", listOf(PackTab("WiFi Interfaces", "interface/wireless"))),
            paths = mapOf("interface/wireless/scan" to PackPath(form = "scan")),
            forms = mapOf("scan" to PackForm(tabs = listOf(PackFormTab("General", listOf("number", "duration"))))),
        )
        val session = PackFieldsSession(PrintedSession(), listOf(scan))
        assertEquals(listOf("wlan1"), session.describe(MenuPath.parse("interface/wireless/scan")).argument("number")?.choices)
    }

    @Test
    fun aListOfAPackTakesACommentWhereTheRouterHasOne() = runTest {
        fun shown(path: String) = Pack(menu = listOf(path), window = PackWindow(path, listOf(PackTab(path, path))), paths = mapOf(path to PackPath()))
        val session = PackFieldsSession(PrintedSession(), listOf(shown("ipv6/firewall/filter"), shown("system/logging")))
        assertEquals(ValueType.Text(), session.describe(MenuPath.parse("ipv6/firewall/filter")).argument("comment")?.type)
        assertNull(session.describe(MenuPath.parse("system/logging")).argument("comment"))
        assertNull(session.describe(MenuPath.parse("ipv6/firewall/mangle")).argument("comment"))
    }

    @Test
    fun theSchemaNamesWhatTheReleaseLacks() = runTest {
        val policy = MenuPath.parse("ip/ipsec/policy")
        assertTrue("peer" in PackFieldsSession(PrintedSession("6.42.12"), emptyList()).describe(policy).lacks)
        assertFalse("peer" in PackFieldsSession(PrintedSession("6.49.23"), emptyList()).describe(policy).lacks)
    }

    @Test
    fun aFieldTheLabRouterOfTheReleaseLacksIsNotOffered() = runTest {
        val vlan = MenuPath.parse("interface/bridge/vlan")
        assertNull(PackFieldsSession(PrintedSession("6.42.1"), emptyList()).describe(vlan).argument("mvrp-forbidden"))
        assertNotNull(PackFieldsSession(PrintedSession("7.16"), emptyList()).describe(vlan).argument("mvrp-forbidden"))
    }

    @Test
    fun theNextMeasuredReleaseTellsWhatARouterLacks() = runTest {
        val ethernet = MenuPath.parse("interface/ethernet")
        assertNotNull(PackFieldsSession(PrintedSession("6.42.12"), emptyList()).describe(ethernet).argument("poe-out"))
        assertNull(PackFieldsSession(PrintedSession("6.45"), emptyList()).describe(ethernet).argument("poe-out"))
    }

    @Test
    fun theButtonsOfAWindowAreCommandsOfItsMenuTheReleaseHas() = runTest {
        val peers = PackPath(actions = listOf(PackAction("Refresh", "refresh", selection = true), PackAction("Resend All", "resend-all")))
        val window = PackWindow("BGP", listOf(PackTab("Peers", "routing/bgp/peer")))
        val path = MenuPath.parse("routing/bgp/peer")
        val older = Pack(menu = listOf("Routing", "BGP"), window = window, paths = mapOf(path.toString() to peers), until = "7.0")
        assertEquals(listOf("print", "add", "set", "remove", "refresh", "resend-all"), PackFieldsSession(PrintedSession(), listOf(older)).describe(path).commands)
        val lease = MenuPath.parse("ip/dhcp-server/lease")
        val leases = PackPath(actions = listOf(PackAction("Make Static", "make-static", selection = true), PackAction("Send Reconfigure", "send-reconfigure", selection = true)))
        val dhcp = Pack(menu = listOf("IP", "DHCP Server"), window = PackWindow("DHCP Server", listOf(PackTab("Leases", lease.toString()))), paths = mapOf(lease.toString() to leases))
        assertEquals(listOf("print", "add", "set", "remove", "make-static"), PackFieldsSession(PrintedSession(), listOf(dhcp)).describe(lease).commands)
        val keys = MenuPath.parse("ip/ipsec/key")
        val ipsec = Pack(menu = listOf("IP", "IPsec"), window = PackWindow("IPsec", listOf(PackTab("Keys", keys.toString()))), paths = mapOf(keys.toString() to PackPath()))
        assertEquals(listOf("print", "set", "remove"), PackFieldsSession(PrintedSession(), listOf(ipsec)).describe(keys).commands)
    }

    @Test
    fun aFieldOfAWindowOfRouterOs6IsEditableThoughTik4netCallsItReadOnly() = runTest {
        val route = MenuPath.parse("ip/route")
        fun of(until: String?) = Pack(
            menu = listOf("IP", "Routes"),
            window = PackWindow("Route List", listOf(PackTab("Routes", route.toString()))),
            paths = mapOf(route.toString() to PackPath(form = "route")),
            forms = mapOf("route" to PackForm(tabs = listOf(PackFormTab("General", listOf("dst-address", "gateway", "-", "pref-src"))))),
            until = until,
        )
        assertEquals(false, PackFieldsSession(PrintedSession(), listOf(of("7.0"))).describe(route).argument("pref-src")?.readOnly)
        assertEquals(true, PackFieldsSession(PrintedSession(), listOf(of(null))).describe(route).argument("pref-src")?.readOnly)
    }

    @Test
    fun aListWithReadOnlyColumnsAndNoFormKeepsPrintAndRemove() = runTest {
        val connection = MenuPath.parse("ip/firewall/connection")
        val firewall = Pack(
            menu = listOf("IP", "Firewall"),
            window = PackWindow("Firewall", listOf(PackTab("Connections", connection.toString()))),
            paths = mapOf(connection.toString() to PackPath(columns = listOf("src-address"), readonly = listOf("src-address"))),
        )
        val session = PackFieldsSession(PrintedSession(), listOf(pack, firewall))
        assertEquals(listOf("print"), session.describe(MenuPath.parse("ip/route/nexthop")).commands)
        assertEquals(listOf("print", "remove"), session.describe(connection).commands)
        assertEquals(listOf("print", "add", "set", "remove"), session.describe(MenuPath.parse("ip/route")).commands)
    }

    @Test
    fun aComboIsTheNamesAndANumberForEachThingTheRouterCounts() = runTest {
        val profile = MenuPath.parse("tool/profile")
        val combos = mapOf("cpu" to PackCombo(listOf("all", "total"), "system/resource:cpu-count", "cpu#"))
        val schema = MenuSchema(
            profile, NodeKind.Command, emptyList(), emptyList(),
            listOf(Argument("cpu", ValueType.Enumeration(closed = true), listOf("all", "total")), Argument("duration", ValueType.Text())),
        )
        val session = PrintedSession(cpus = 4)
        assertEquals(mapOf("cpu" to 4), session.comboCounts(combos))
        val shaped = schema.withCombos(combos, session.comboCounts(combos))
        val cpu = assertNotNull(shaped.argument("cpu"))
        assertEquals(ValueType.Enumeration(), cpu.type)
        assertEquals(listOf("all", "total", "0", "1", "2", "3"), cpu.choices)
        assertEquals("cpu2", cpu.labels["2"])
        assertEquals(setOf("0"), cpu.apart)
        assertEquals(ValueType.Text(), shaped.argument("duration")?.type)
    }

    @Test
    fun aComboKeepsItsNamesWhenTheRouterDoesNotTellTheCount() = runTest {
        val combo = PackCombo(listOf("all", "total"), "system/resource:cpu-count", "cpu#")
        assertEquals(emptyMap(), PrintedSession().comboCounts(mapOf("cpu" to combo)))
        val cpu = Argument("cpu", ValueType.Text()).combo(combo, null)
        assertEquals(listOf("all", "total"), cpu.choices)
        assertEquals(emptySet(), cpu.apart)
    }

    @Test
    fun theProfilePacksOfBothReleasesOfferTheProcessorsOfTheRouter() {
        for (name in listOf("tools-profile.json", "tools-profile-v6.yaml")) {
            val path = assertNotNull(bundledPacks()[name]?.paths?.get("tool/profile"), name)
            assertEquals(listOf("all", "total"), path.combos["cpu"]?.names, name)
            assertEquals("system/resource:cpu-count", path.combos["cpu"]?.count, name)
            assertEquals("all", path.defaults["cpu"], name)
        }
    }

    @Test
    fun aPackGivesTheTypesOfFieldsTheRouterDoesNotTell() = runTest {
        val ping = MenuPath.parse("ping")
        val tool = Pack(
            menu = listOf("Tools", "Ping"),
            window = PackWindow("Ping", listOf(PackTab("Ping", ping.toString()))),
            paths = mapOf(ping.toString() to PackPath(form = "ping", types = mapOf("arp-ping" to ValueType.YesNo, "do-not-fragment" to ValueType.Switch))),
            forms = mapOf("ping" to PackForm(tabs = listOf(PackFormTab("General", listOf("address", "arp-ping", "do-not-fragment"))))),
            until = "7.0",
        )
        val schema = PackFieldsSession(PrintedSession(), listOf(tool)).describe(ping)
        assertEquals(ValueType.YesNo, schema.argument("arp-ping")?.type)
        assertEquals(ValueType.Switch, schema.argument("do-not-fragment")?.type)
        assertEquals(ValueType.Text(), schema.argument("address")?.type)
    }
}
