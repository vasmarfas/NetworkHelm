package com.vasmarfas.networkhelm.offline

import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.openwrt.parseUci
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenWrtConfigTest {

    private val export = """
        package network

        config interface 'loopback'
        	option device 'lo'
        	option proto 'static'
        	list ipaddr '127.0.0.1/8'

        config interface 'lan'
        	option proto 'static'
        	list ipaddr '192.168.99.1/24'
        	list ipaddr '10.0.0.1/24'

        package firewall

        config rule
        	option name 'Allow-DHCP-Renew'

        # a comment
        config rule
        	option name "it's \"quoted\""

        config zone 'lan_zone'
        	option name 'lan'

        package system

        config system
        	option hostname 'rd-test-wrt'
    """.trimIndent()

    @Test
    fun sectionsAreReadWithTheirNamesOrAsUciAddressesThem() {
        val configs = parseUci(export)
        assertEquals(listOf("network", "firewall", "system"), configs.keys.toList())
        assertEquals(listOf("loopback", "lan"), configs.getValue("network").map { it.name })
        assertEquals(listOf("192.168.99.1/24", "10.0.0.1/24"), configs.getValue("network")[1].lists["ipaddr"])
        val firewall = configs.getValue("firewall")
        assertEquals(listOf("@rule[0]", "@rule[1]", "lan_zone"), firewall.map { it.name })
        assertEquals(listOf(true, true, false), firewall.map { it.anonymous })
        assertEquals("it's \"quoted\"", firewall[1].options["name"])
        assertEquals(listOf("wan"), parseUci("config interface 'wan'\n\toption proto 'dhcp'", "network").getValue("network").map { it.name })
    }

    @Test
    fun uciOpensAsTheMenusTheSessionOfOpenWrtShows() = runTest {
        val session = assertNotNull(openConfig("uci-export.txt", export.encodeToByteArray()))
        assertEquals(Platform.OpenWrt, session.device.platform)
        assertEquals("rd-test-wrt", session.device.identity)
        assertFalse(session.device.canWrite)
        val lan = assertNotNull(session.get(MenuPath.parse("network/interface"), "lan"))
        assertEquals(mapOf("proto" to "static", "ipaddr" to "192.168.99.1/24\n10.0.0.1/24", ".name" to "lan"), lan.values)
        val rule = assertNotNull(session.get(MenuPath.parse("firewall/rule"), "@rule[0]"))
        assertNull(rule[".name"])
        assertTrue(session.describe(MenuPath.parse("firewall")).children.containsAll(listOf("rule", "zone")))
        assertNotNull(openConfig("network", "config interface 'wan'\n\toption proto 'dhcp'".encodeToByteArray())?.get(MenuPath.parse("network/interface"), "wan"))
    }
}
