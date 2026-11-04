package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.connectSsh
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RouterOsLayerMoreLabTest {

    private fun layer(block: suspend (RouterOsLayer, DeviceSession) -> Unit) =
        Lab.forEachOpenWrt("host", "user", "password", "ssh.hostKey") { lab ->
            runBlocking {
                withTimeout(60.seconds) {
                    val ssh = connectSsh(
                        lab.getValue("host"), 22, lab.getValue("user"), SshAuth.Password(lab.getValue("password")),
                        acceptHostKey = { it.fingerprint == lab.getValue("ssh.hostKey") },
                    )
                    try {
                        val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
                        block(RouterOsLayer(session), session)
                    } finally {
                        ssh.close()
                    }
                }
            }
        }

    private suspend fun DeviceSession.items(path: String) = watch(MenuPath.parse(path)).filterIsInstance<ListEvent.Snapshot>().first().items

    @Test
    fun aTunnelClientGoesIntoTheDraft() = layer { layer, session ->
        val pptps = MenuPath.parse("interface/pptp-client")
        val l2tps = MenuPath.parse("interface/l2tp-client")
        val pptp = layer.add(pptps, mapOf("name" to "pptp-rd_test_vpn", "connect-to" to "192.0.2.1", "user" to "rd-test", "max-mtu" to "1400"))
        val section = session.get(MenuPath.parse("network/interface"), pptp)
        assertEquals(
            listOf("rd_test_vpn", "pptp", "192.0.2.1", "rd-test", "1400", "0", "0"),
            listOf(pptp, section?.get("proto"), section?.get("server"), section?.get("username"), section?.get("mtu"), section?.get("defaultroute"), section?.get("peerdns")),
        )
        val shown = layer.items("interface/pptp-client").single { it.id == pptp }
        assertEquals(listOf("pptp-rd_test_vpn", "192.0.2.1", "false", "false"), listOf(shown["name"], shown["connect-to"], shown["add-default-route"], shown["use-peer-dns"]))
        assertEquals(listOf("pptp-out", "false"), layer.items("interface").single { it["name"] == "pptp-rd_test_vpn" }.let { listOf(it["type"], it["running"]) })
        val l2tp = layer.add(l2tps, mapOf("name" to "l2tp-rd_test_vpn2", "connect-to" to "192.0.2.2"))
        assertEquals("l2tp-out", layer.items("interface").single { it["name"] == "l2tp-rd_test_vpn2" }["type"])
        assertTrue(layer.items("interface/pptp-client").none { it.id == l2tp } && layer.items("interface/l2tp-client").none { it.id == pptp })
        layer.set(pptps, pptp, mapOf("add-default-route" to "true", "connect-to" to "192.0.2.9"))
        val changed = session.get(MenuPath.parse("network/interface"), pptp)
        assertEquals(listOf(null, "192.0.2.9"), listOf(changed?.get("defaultroute"), changed?.get("server")))
        layer.remove(pptps, listOf(pptp))
        layer.remove(l2tps, listOf(l2tp))
        assertTrue(layer.items("interface").none { it["name"] in listOf("pptp-rd_test_vpn", "l2tp-rd_test_vpn2") })
    }

    @Test
    fun natMangleAndRawGoIntoTheDraft() = layer { layer, session ->
        val rules = MenuPath.parse("firewall/rule")
        val forward = layer.add(
            MenuPath.parse("ipv6/firewall/nat"),
            mapOf("chain" to "dstnat", "action" to "dst-nat", "in-interface-list" to "wan", "protocol" to "tcp", "dst-port" to "18443", "to-addresses" to "fd00::5", "to-ports" to "443", "comment" to "rd-test-nat6"),
        )
        val redirect = session.get(MenuPath.parse("firewall/redirect"), forward)
        assertEquals(
            listOf("DNAT", "ipv6", "wan", "18443", "fd00::5", "443"),
            listOf(redirect?.get("target"), redirect?.get("family"), redirect?.get("src"), redirect?.get("src_dport"), redirect?.get("dest_ip"), redirect?.get("dest_port")),
        )
        assertTrue(layer.items("ipv6/firewall/nat").any { it.id == forward } && layer.items("ip/firewall/nat").none { it.id == forward })
        assertTrue(layer.items("ip/firewall/nat").any { it["action"] == "masquerade" && it["dynamic"] == "true" })
        val mark = layer.add(
            MenuPath.parse("ip/firewall/mangle"),
            mapOf("chain" to "prerouting", "action" to "mark-packet", "new-packet-mark" to "0x44", "in-interface-list" to "lan", "protocol" to "udp", "dst-port" to "5060", "comment" to "rd-test-mark"),
        )
        val written = session.get(rules, mark)
        assertEquals(listOf("lan", "*", "MARK", "0x44", "ipv4"), listOf(written?.get("src"), written?.get("dest"), written?.get("target"), written?.get("set_mark"), written?.get("family")))
        assertEquals("prerouting", layer.items("ip/firewall/mangle").single { it.id == mark }["chain"])
        assertTrue(layer.items("ip/firewall/filter").none { it.id == mark } && layer.items("ipv6/firewall/mangle").none { it.id == mark })
        val track = layer.add(
            MenuPath.parse("ipv6/firewall/raw"),
            mapOf("chain" to "prerouting", "action" to "notrack", "in-interface-list" to "lan", "protocol" to "udp", "comment" to "rd-test-raw"),
        )
        assertEquals(listOf("NOTRACK", "lan", "ipv6"), session.get(rules, track).let { listOf(it?.get("target"), it?.get("src"), it?.get("family")) })
        assertTrue(layer.items("ipv6/firewall/raw").any { it.id == track } && layer.items("ipv6/firewall/filter").none { it.id == track })
        layer.set(MenuPath.parse("ip/firewall/mangle"), mark, mapOf("chain" to "forward", "in-interface-list" to "lan", "out-interface-list" to "wan"))
        assertEquals(listOf("lan", "wan"), session.get(rules, mark).let { listOf(it?.get("src"), it?.get("dest")) })
        assertEquals("forward", layer.items("ip/firewall/mangle").single { it.id == mark }["chain"])
        layer.remove(MenuPath.parse("ip/firewall/mangle"), listOf(mark))
        layer.remove(MenuPath.parse("ipv6/firewall/raw"), listOf(track))
        layer.remove(MenuPath.parse("ipv6/firewall/nat"), listOf(forward))
        assertTrue(layer.items("ip/firewall/mangle").none { it.id == mark } && layer.items("ipv6/firewall/nat").none { it.id == forward })
    }

    @Test
    fun theConnectionsAreListedByFamily() = layer { layer, _ ->
        val v4 = layer.items("ip/firewall/connection")
        assertTrue(v4.any { it["protocol"] == "tcp" && it["timeout"] != null }, v4.toString())
        assertTrue(v4.all { '.' in it["src-address"].orEmpty() }, v4.toString())
        assertTrue(layer.items("ipv6/firewall/connection").all { ':' in it["src-address"].orEmpty() })
    }

    @Test
    fun theBindingsPrefixesAndGroupsAreRead() = layer { layer, session ->
        val bindings = MenuPath.parse("ipv6/dhcp-server/binding")
        assertTrue(layer.items("ipv6/nd/prefix").all { it["dynamic"] == "true" && it["prefix"].orEmpty().substringAfter('/').toInt() <= 64 })
        val id = layer.add(bindings, mapOf("duid" to "0004aabbccddeeff", "address" to "1234", "comment" to "rd-test"))
        val section = session.get(MenuPath.parse("dhcp/host"), id)
        assertEquals(listOf("0004aabbccddeeff", "1234", "rd-test"), listOf(section?.get("duid"), section?.get("hostid"), section?.get("name")))
        assertEquals(id, layer.items("ipv6/dhcp-server/binding").single { it["comment"] == "rd-test" }.id)
        assertTrue(layer.items("ip/dhcp-server/lease").none { it.id == id })
        layer.set(bindings, id, mapOf("comment" to "rd-test-2"))
        assertEquals("rd-test-2", session.get(MenuPath.parse("dhcp/host"), id)?.get("name"))
        layer.remove(bindings, listOf(id))
        assertTrue(layer.items("ipv6/dhcp-server/binding").none { it.id == id })
        assertEquals(listOf("full", "read"), layer.items("user/group").map { it.id }.take(2))
    }

    @Test
    fun aNetworkOfTheWiFiRouterHasAConfiguration() = runBlocking {
        val lab = Lab.require("openwrtwifi.host", "openwrtwifi.user", "openwrtwifi.password", "openwrtwifi.ssh.hostKey")
        withTimeout(60.seconds) {
            val ssh = connectSsh(
                lab.getValue("openwrtwifi.host"), 22, lab.getValue("openwrtwifi.user"), SshAuth.Password(lab.getValue("openwrtwifi.password")),
                acceptHostKey = { it.fingerprint == lab.getValue("openwrtwifi.ssh.hostKey") },
            )
            try {
                val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
                val layer = RouterOsLayer(session)
                val configurations = MenuPath.parse("interface/wifi/configuration")
                val networks = layer.items("interface/wifi")
                val profiles = layer.items("interface/wifi/configuration")
                assertEquals(networks.map { it["name"] }.toSet(), profiles.map { it["name"] }.toSet())
                val access = profiles.first { it["mode"] == "ap" }
                assertTrue(access["country"].orEmpty().isNotEmpty() && access["tx-power"].orEmpty().isNotEmpty(), access.toString())
                layer.set(configurations, access.id, mapOf("ssid" to "rd-test-ssid", "hide-ssid" to "true", "max-clients" to "12"))
                val section = session.get(MenuPath.parse("wireless/wifi-iface"), access.id)
                assertEquals(listOf("rd-test-ssid", "1", "12"), listOf(section?.get("ssid"), section?.get("hidden"), section?.get("maxassoc")))
                val drafted = layer.items("interface/wifi/configuration").single { it.id == access.id }
                assertEquals(listOf("rd-test-ssid", "true", "12"), listOf(drafted["ssid"], drafted["hide-ssid"], drafted["max-clients"]))
                val datapaths = layer.items("interface/wifi/datapath")
                assertEquals(networks.map { it["name"] }.toSet(), datapaths.map { it["name"] }.toSet())
                assertTrue(datapaths.single { it.id == access.id }["bridge"].orEmpty().isNotEmpty(), datapaths.toString())
                layer.set(MenuPath.parse("interface/wifi/datapath"), access.id, mapOf("client-isolation" to "true"))
                assertEquals("1", session.get(MenuPath.parse("wireless/wifi-iface"), access.id)?.get("isolate"))
            } finally {
                ssh.close()
            }
        }
    }
}
