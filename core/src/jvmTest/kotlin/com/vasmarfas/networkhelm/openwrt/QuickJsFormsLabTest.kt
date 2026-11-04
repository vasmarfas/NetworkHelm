package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.session.TextStore
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.ssh.connectSsh
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class QuickJsFormsLabTest {

    private fun ssh(lab: Map<String, String>, block: suspend (SshSession) -> Unit) = runBlocking {
        withTimeout(60.seconds) {
            val ssh = connectSsh(
                lab.getValue("host"), 22, lab.getValue("user"), SshAuth.Password(lab.getValue("password")),
                acceptHostKey = { it.fingerprint == lab.getValue("ssh.hostKey") },
            )
            try {
                block(ssh)
            } finally {
                ssh.close()
            }
        }
    }

    private fun openwrt(prefix: String = "openwrt", block: suspend (SshSession) -> Unit) =
        ssh(Lab.require("$prefix.host", "$prefix.user", "$prefix.password", "$prefix.ssh.hostKey").mapKeys { it.key.removePrefix("$prefix.") }, block)

    private fun eachOpenWrt(block: suspend (SshSession) -> Unit) = Lab.forEachOpenWrt("host", "user", "password", "ssh.hostKey") { ssh(it, block) }

    @Test
    fun theViewsOfAConfigComeFromTheMenusAndTellItsFields() = eachOpenWrt { ssh ->
        val forms = QuickJsForms(SshUbus(ssh), ssh)
        val views = forms.views()
        assertTrue("network/dhcp" in views["dhcp"].orEmpty() && "system/system" in views["system"].orEmpty(), views.toString())
        val dnsmasq = forms.maps("network/dhcp").single { it.config == "dhcp" }.sections.first { it.type == "dnsmasq" }
        assertTrue(!dnsmasq.single && forms.maps("system/system").flatMap { it.sections }.single { it.type == "system" }.single)
        assertTrue(dnsmasq.tabs.size > 3, dnsmasq.tabs.map { it.name }.toString())
        val authoritative = assertNotNull(dnsmasq.options.firstOrNull { it.name == "authoritative" }).argument()
        assertEquals(ValueType.Flag, authoritative?.type)
        assertTrue(authoritative?.label.orEmpty().isNotEmpty())
    }

    @Test
    fun theSessionTakesItsFieldsFromTheForms() = eachOpenWrt { ssh ->
        val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
        assertEquals(NodeKind.Settings, session.describe(MenuPath.parse("system/system")).kind)
        val dnsmasq = session.describe(MenuPath.parse("dhcp/dnsmasq"))
        assertEquals(NodeKind.List, dnsmasq.kind)
        assertEquals(ValueType.Flag, dnsmasq.argument("domainneeded")?.type)
        val hosts = session.describe(MenuPath.parse("dhcp/host"))
        assertEquals(NodeKind.List, hosts.kind)
        assertTrue(hosts.argument("mac")?.label.orEmpty().isNotEmpty(), hosts.arguments.map { "${it.name}=${it.label}" }.toString())
        val interfaces = session.describe(MenuPath.parse("network/interface"))
        assertEquals("IPv4 address", interfaces.argument("ipaddr")?.label, interfaces.arguments.map { "${it.name}=${it.label}" }.toString())
        assertTrue(interfaces.argument("hostname")?.label.orEmpty().isNotEmpty())
        assertEquals("General Settings", interfaces.arguments.firstNotNullOfOrNull { it.tab })
        assertTrue(mapOf("proto" to "static") in interfaces.argument("ipaddr")?.depends.orEmpty())
        assertTrue("dhcp" in interfaces.argument("proto")?.choices.orEmpty())
        assertEquals(listOf("General Settings", "General Settings", "Advanced Settings"), listOf("proto", "device", "metric").map { interfaces.argument(it)?.tab })
        assertTrue(mapOf("proto" to "pppoe") in interfaces.argument("username")?.depends.orEmpty())
        val zones = session.describe(MenuPath.parse("firewall/zone"))
        assertEquals(null, zones.argument(".name"))
        assertEquals(ValueType.Many(ValueType.Text(), LIST), zones.argument("network")?.type)
        assertTrue("lan" in zones.argument("network")?.choices.orEmpty())
        assertEquals(null, zones.argument("out"))
        assertTrue("wan" in session.describe(MenuPath.parse("firewall/rule")).argument("src")?.choices.orEmpty())
        assertTrue(interfaces.argument("device")?.choices.orEmpty().isNotEmpty())
        val devices = session.describe(MenuPath.parse("network/device"))
        assertTrue("bridge" in devices.argument("type")?.choices.orEmpty())
        assertTrue(mapOf("type" to "bridge") in devices.argument("ports")?.depends.orEmpty())
        val servers = session.describe(MenuPath.parse("dhcp/dhcp"))
        assertEquals(NodeKind.List, servers.kind)
        assertEquals("Start", servers.argument("start")?.label)
        assertTrue(servers.argument("start")?.tab in setOf("General Setup", "IPv4 Settings"))
        assertEquals("1", session.describe(MenuPath.parse("firewall/rule")).argument("enabled")?.default)
        assertEquals(listOf("on", "off"), session.describe(MenuPath.parse("dropbear/dropbear")).argument("PasswordAuth")?.choices)
    }

    @Test
    fun theNativeMenuFollowsLuci() = openwrt { ssh ->
        val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
        val native = assertNotNull(session.nativeMenu())
        val groups = native.menu.items.filter { it.items.isNotEmpty() }.associate { group -> group.title to group.items.associate { it.title to (it.path ?: it.page) } }
        assertEquals(listOf("Status", "System", "Services", "Network", "Statistics"), groups.keys.toList())
        assertEquals(listOf("Overview", "Routing", "Firewall", "System Log", "Processes", "Realtime Graphs", "WireGuard"), groups["Status"]?.keys?.toList())
        assertEquals(
            listOf("System", "Administration", "Software", "Startup", "Scheduled Tasks", "Attended Sysupgrade", "Backup / Flash Firmware", "Reboot"),
            groups["System"]?.keys?.toList(),
        )
        val mixomo = "Mixomo".takeIf { Lab.optional("openwrt.mixomo") != null }
        assertEquals(listOfNotNull("Dynamic DNS", mixomo, "Policy Routing", "Adblock", "banIP", "Terminal", "UPnP IGD & PCP"), groups["Services"]?.keys?.toList())
        assertEquals(listOf("Interfaces", "Routing", "DHCP", "DNS", "Diagnostics", "SQM QoS", "Firewall"), groups["Network"]?.keys?.toList())
        assertEquals(listOf("Graphs", "Setup"), groups["Statistics"]?.keys?.toList())
        assertEquals(
            listOf("status/resource", "status/routes", "status/firewall", "status/log", "status/processes", "tool/traffic", "status/wireguard"),
            groups["Status"]?.values?.toList(),
        )
        assertEquals("ddns/service", groups["Services"]?.get("Dynamic DNS"))
        assertEquals(mixomo?.let { "mixomo/overview" }, groups["Services"]?.get("Mixomo"))
        assertEquals("luci_statistics/statistics", groups["Statistics"]?.get("Setup"))
        assertEquals("admin/statistics/graphs", groups["Statistics"]?.get("Graphs"))
        assertEquals(listOf("Attended Sysupgrade", "DNS"), native.packs.map { it.window.title }.filter { it in setOf("Mount Points", "Attended Sysupgrade", "DNS") })
        assertEquals(listOf("Files", "Log", "New Terminal"), native.menu.items.takeLast(3).map { it.title })
    }

    @Test
    fun theNativeMenuOf2410FollowsLuci() = openwrt("openwrt24") { ssh ->
        val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
        val native = assertNotNull(session.nativeMenu())
        val groups = native.menu.items.filter { it.items.isNotEmpty() }.associate { group -> group.title to group.items.map { it.title } }
        assertEquals(listOf("Status", "System", "Network"), groups.keys.toList())
        assertEquals(listOf("Overview", "Routing", "Firewall", "System Log", "Processes", "Realtime Graphs"), groups["Status"])
        assertEquals(listOf("System", "Administration", "Software", "Startup", "Scheduled Tasks", "Backup / Flash Firmware", "Reboot"), groups["System"])
        assertEquals(listOf("Interfaces", "Routing", "DHCP and DNS", "Diagnostics", "Firewall"), groups["Network"])
    }

    @Test
    fun anApplicationOfLuciGetsAWindowFromItsViews() = openwrt { ssh ->
        val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
        val store = object : TextStore {
            val texts = mutableMapOf<String, String>()

            override suspend fun read(name: String) = texts[name]

            override suspend fun write(name: String, text: String) {
                texts[name] = text
            }
        }
        val packs = session.packs(store)
        assertEquals(
            listOf(listOf("System", "Attended Sysupgrade"), listOf("Services", "Adblock"), listOf("Services", "banIP"), listOf("Services", "Terminal")),
            packs.map { it.menu },
        )
        assertEquals(listOf("adblock/adblock"), packs[1].window.tabs.map { it.path })
        assertTrue("_unix_sock_path" !in packs[3].paths["ttyd/ttyd"]?.columns.orEmpty(), "an option of LuCI's own is no field")
        val pack = packs.first()
        assertEquals(listOf("Server" to "attendedsysupgrade/server", "Client" to "attendedsysupgrade/client"), pack.window.tabs.map { it.title to it.path })
        assertEquals(listOf("url", "rebuilder"), pack.paths["attendedsysupgrade/server"]?.columns)
        assertEquals(NodeKind.Settings, session.describe(MenuPath.parse("attendedsysupgrade/client")).kind)
        val (name, text) = store.texts.entries.single { it.key.startsWith("luci-2-luci-app-attendedsysupgrade-") }
        assertEquals("[]", store.texts.entries.single { it.key.startsWith("luci-2-luci-app-package-manager-") }.value)
        store.texts[name] = text.replace("\"Client\"", "\"Kept\"")
        val again = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
        assertEquals(listOf("Server", "Kept"), again.packs(store).first().window.tabs.map { it.title })
    }

    @Test
    fun aConfigIsFoundByTheMapItsViewMakes() = openwrt { ssh ->
        val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
        val pbr = session.describe(MenuPath.parse("pbr/pbr"))
        assertEquals(NodeKind.Settings, pbr.kind)
        assertEquals(ValueType.Enumeration(), pbr.argument("resolver_set")?.type)
        val statistics = session.describe(MenuPath.parse("luci_statistics/statistics"))
        assertEquals(NodeKind.List, statistics.kind)
        assertEquals(listOf(mapOf(".name" to "collectd")), statistics.argument("Hostname")?.depends)
        assertEquals(listOf(mapOf("Hostname" to "", ".name" to "collectd")), statistics.argument("FQDNLookup")?.depends)
        assertEquals(true, statistics.argument("ReportByCpu")?.optional)
        assertEquals(ValueType.Integer(min = 0), session.describe(MenuPath.parse("sqm/queue")).argument("download")?.type)
    }

    @Test
    fun theDialogOfAWirelessNetworkGivesItsFields() = openwrt("openwrtwifi") { ssh ->
        val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
        val networks = session.describe(MenuPath.parse("wireless/wifi-iface"))
        assertEquals(NodeKind.List, networks.kind)
        assertEquals(listOf("ESSID", "General Setup"), networks.argument("ssid")?.let { listOf(it.label, it.tab) })
        assertEquals(listOf(ValueType.Secret, "Wireless Security"), networks.argument("key")?.let { listOf(it.type, it.tab) })
        assertTrue("lan" in networks.argument("network")?.choices.orEmpty())
        assertEquals(ValueType.Flag, networks.argument("disabled")?.type)
        val device = networks.argument("device")
        assertEquals(listOf(ValueType.Enumeration(), "General Setup", false, true), listOf(device?.type, device?.tab, device?.readOnly, device?.fixed))
        assertTrue("radio0" in device?.choices.orEmpty())
        val radios = session.describe(MenuPath.parse("wireless/wifi-device"))
        assertEquals("Advanced Settings", radios.argument("distance")?.tab)
        assertEquals(listOf("General Setup", "General Setup", "General Setup"), listOf("band", "channel", "htmode").map { radios.argument(it)?.tab })
        assertTrue(radios.argument("channel")?.choices.orEmpty().size > 1 && radios.argument("htmode")?.choices.orEmpty().isNotEmpty())
    }

    @Test
    fun aViewThatWantsARadioGetsOneFromTheWirelessConfig() = openwrt("openwrtwifi") { ssh ->
        val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
        val packs = session.packs(null).associateBy { it.window.title }
        assertEquals(listOf("travelmate/travelmate"), packs["Travelmate"]?.window?.tabs?.map { it.path })
        assertTrue("Mihomo" !in packs && "MagiTrickle" !in packs, packs.keys.toString())
        val services = assertNotNull(session.nativeMenu()).menu.items.single { it.title == "Services" }.items.associateBy { it.title }
        assertEquals("admin/services/mihomo", services["Mihomo"]?.page)
        assertEquals("admin/services/magitrickle", services["MagiTrickle"]?.page)
    }
}
