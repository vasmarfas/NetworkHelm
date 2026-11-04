package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.journal.EditKind
import com.vasmarfas.networkhelm.model.MenuPath
import kotlin.test.Test
import kotlin.test.assertEquals

class UciCliTest {

    private val interfaces = MenuPath.parse("network/interface")
    private val rules = MenuPath.parse("firewall/rule")

    @Test
    fun editsReadAsTheUciCommandsThatMakeThem() {
        assertEquals(
            "uci set network.guest=interface\nuci set network.guest.proto='static'\nuci -q delete network.guest.dns\n" +
                "uci add_list network.guest.dns='192.0.2.1'\nuci add_list network.guest.dns='192.0.2.2'",
            uciCli(interfaces, EditKind.Add, emptyList(), linkedMapOf(".name" to "guest", "proto" to "static", "dns" to "192.0.2.1\n192.0.2.2")),
        )
        assertEquals("uci add firewall rule\nuci set firewall.@rule[-1].name='it'\\''s'", uciCli(rules, EditKind.Add, emptyList(), mapOf("name" to "it's")))
        assertEquals("uci set system.@system[0].hostname='rd-test'", uciCli(MenuPath.parse("system/system"), EditKind.Set, emptyList(), mapOf("hostname" to "rd-test")))
        assertEquals("uci delete network.lan.gateway", uciCli(interfaces, EditKind.Set, listOf("lan"), mapOf("gateway" to null)))
        assertEquals("uci delete firewall.cfg0a\nuci delete firewall.cfg0b", uciCli(rules, EditKind.Remove, listOf("cfg0a", "cfg0b"), emptyMap()))
        assertEquals("# firewall.cfg0b moved before firewall.cfg0a", uciCli(rules, EditKind.Move, listOf("cfg0b"), emptyMap(), "cfg0a"))
        assertEquals("# status/packages add name=tcpdump", uciCli(MenuPath.parse("status/packages"), EditKind.Add, emptyList(), mapOf("name" to "tcpdump")))
    }
}
