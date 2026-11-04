package com.vasmarfas.networkhelm.offline

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouterOsExportTest {

    private val export = """
        # 2026-10-23 17:40:12 by RouterOS 7.25.1
        # software id =
        #
        # model = CHR
        /interface bridge
        add comment="\D0\9B\D0\B0\D0\B1" name=bridge1
        /interface ethernet
        set [ find default-name=ether1 ] comment=WAN
        /ip pool
        add name=dhcp ranges=192.168.88.10-192.168.88.254
        /ip address
        add address=192.168.88.1/24 comment=defconf interface=bridge1 network=\
            192.168.88.0
        /ip dns
        set servers=1.1.1.1,8.8.8.8
        /ip firewall filter
        add action=accept chain=input comment=\
            "defconf: accept established,related,untracked" connection-state=\
            established,related,untracked
        add action=drop chain=input disabled=yes in-interface=ether1
        /ip service
        set telnet disabled=yes
        /routing bgp template
        set default as=65000
        /system identity
        set name=rd-test
        /system script
        add name=rd-test source=":local a \"b\"\r\
            \n:put \${'$'}a"
        /ip route add dst-address=10.0.0.0/8 gateway=192.168.88.2
        /ip firewall address-list
        add address=10.0.0.1 list=rd-test
        add address=10.0.0.2 list=rd-test
        remove [ find address=10.0.0.2 ]
    """.trimIndent()

    @Test
    fun theMenusOfAnExportComeInTheOrderOfTheFile() {
        val config = parseExport(export)
        assertEquals("7.25.1", config.facts["version"])
        assertEquals("CHR", config.facts["model"])
        assertEquals(
            listOf(
                "interface/bridge", "interface/ethernet", "ip/pool", "ip/address", "ip/dns", "ip/firewall/filter", "ip/service", "routing/bgp/template",
                "system/identity", "system/script", "ip/route", "ip/firewall/address-list",
            ),
            config.menus.keys.map { it.toString() },
        )
        assertEquals(mapOf("comment" to "Лаб", "name" to "bridge1"), config.menus.getValue(MenuPath.parse("interface/bridge")).items.single().values)
        assertEquals("192.168.88.0", config.menus.getValue(MenuPath.parse("ip/address")).items.single()["network"])
        val rules = config.menus.getValue(MenuPath.parse("ip/firewall/filter")).items
        assertEquals(listOf("*1", "*2"), rules.map { it.id })
        assertEquals("defconf: accept established,related,untracked", rules[0]["comment"])
        assertEquals("established,related,untracked", rules[0]["connection-state"])
        assertEquals("true", rules[1]["disabled"])
        assertEquals(":local a \"b\"\r\n:put \$a", config.menus.getValue(MenuPath.parse("system/script")).items.single()["source"])
    }

    @Test
    fun setChangesAnElementOfTheRouterOrWritesSettings() {
        val config = parseExport(export)
        val ether1 = config.menus.getValue(MenuPath.parse("interface/ethernet"))
        assertEquals(NodeKind.List, ether1.kind)
        assertEquals("WAN", ether1.items.single()["comment"])
        assertEquals("ether1", ether1.items.single()["default-name"])
        assertEquals(mapOf("name" to "telnet", "disabled" to "true"), config.menus.getValue(MenuPath.parse("ip/service")).items.single().values)
        assertEquals(mapOf("name" to "default", "as" to "65000"), config.menus.getValue(MenuPath.parse("routing/bgp/template")).items.single().values)
        val dns = config.menus.getValue(MenuPath.parse("ip/dns"))
        assertEquals(NodeKind.Settings, dns.kind)
        assertEquals("1.1.1.1,8.8.8.8", dns.items.single()["servers"])
        assertEquals("192.168.88.2", config.menus.getValue(MenuPath.parse("ip/route")).items.single()["gateway"])
        assertEquals(listOf("10.0.0.1"), config.menus.getValue(MenuPath.parse("ip/firewall/address-list")).items.map { it["address"] })
    }

    @Test
    fun theFileOpensInTheWindowsOfARouterToReadOnly() = runTest {
        val session = assertNotNull(openConfig("office.rsc", export.encodeToByteArray()))
        assertEquals("rd-test", session.device.identity)
        assertEquals("7.25.1", session.device.version)
        assertEquals("CHR", session.device.board)
        assertFalse(session.device.canWrite)
        assertTrue(session.capabilities.offline)
        assertEquals(NodeKind.List, session.describe(MenuPath.parse("ip/address")).kind)
        assertEquals(listOf("print"), session.describe(MenuPath.parse("ip/address")).commands)
        assertEquals(NodeKind.Settings, session.describe(MenuPath.parse("ip/dns")).kind)
        assertTrue(session.describe(MenuPath.parse("ip")).children.containsAll(listOf("pool", "address", "dns", "firewall", "service", "route")))
        val snapshot = session.watch(MenuPath.parse("ip/address")).first() as ListEvent.Snapshot
        assertEquals("192.168.88.1/24", snapshot.items.single()["address"])
        assertEquals("1.1.1.1,8.8.8.8", session.get(MenuPath.parse("ip/dns"), null)?.get("servers"))
        assertEquals(0, session.count(MenuPath.parse("ip/hotspot")))
        assertEquals(export, session.export())
        assertFailsWith<DeviceException> { session.set(MenuPath.parse("ip/dns"), null, mapOf("servers" to "9.9.9.9")) }
        assertNull(openConfig("picture.png", byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
    }

    @Test
    fun theAddsOfATextComeWithTheirMenus() {
        val adds = addsOf(export)
        assertEquals(
            listOf("interface/bridge", "ip/pool", "ip/address", "ip/firewall/filter", "ip/firewall/filter", "system/script", "ip/route", "ip/firewall/address-list", "ip/firewall/address-list"),
            adds.map { it.first.toString() },
        )
        val (accept, drop) = adds.filter { it.first == MenuPath.parse("ip/firewall/filter") }.map { it.second }
        assertEquals("defconf: accept established,related,untracked", accept["comment"])
        assertEquals("established,related,untracked", accept["connection-state"])
        assertEquals(mapOf("action" to "drop", "chain" to "input", "disabled" to "yes", "in-interface" to "ether1"), drop)
        assertEquals(mapOf("dst-address" to "10.0.0.0/8", "gateway" to "192.168.88.2"), adds.single { it.first == MenuPath.parse("ip/route") }.second)
        val copied = "/ip firewall filter add action=accept chain=forward comment=\"web, 443\" dst-port=443 protocol=tcp\n/ip/firewall/filter add chain=input"
        assertEquals(
            listOf(mapOf("action" to "accept", "chain" to "forward", "comment" to "web, 443", "dst-port" to "443", "protocol" to "tcp"), mapOf("chain" to "input")),
            addsOf(copied).map { it.second },
        )
    }
}
