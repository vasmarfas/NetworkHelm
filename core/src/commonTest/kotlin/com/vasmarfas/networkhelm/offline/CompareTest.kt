package com.vasmarfas.networkhelm.offline

import com.vasmarfas.networkhelm.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompareTest {

    private val before = parseExport(
        """
        /interface ethernet
        set [ find default-name=ether1 ] comment=WAN
        /ip address
        add address=192.168.88.1/24 interface=bridge
        add address=10.0.0.1/24 interface=ether2
        /ip dns
        set servers=1.1.1.1
        /ip firewall filter
        add action=accept chain=input connection-state=established
        add action=drop chain=input in-interface=ether1
        add action=accept chain=forward
        /system script
        add name=backup source=":log info a"
        add name=old source=":log info b"
        """.trimIndent(),
    )

    private val after = parseExport(
        """
        /interface ethernet
        set [ find default-name=ether1 ] comment=Uplink
        /ip address
        add address=192.168.88.1/24 interface=bridge
        add address=10.0.0.1/24 interface=ether3
        /ip dns
        set servers=1.1.1.1,8.8.8.8
        /ip firewall filter
        add action=accept chain=input connection-state=established
        add action=accept chain=forward
        add action=drop chain=input in-interface=ether1
        /system script
        add name=backup source=":log info a"
        add name=new source=":log info c"
        /ip pool
        add name=dhcp ranges=192.168.88.10-192.168.88.254
        """.trimIndent(),
    )

    @Test
    fun twoExportsDifferMenuByMenu() {
        val differences = compare(before, after)
        assertEquals(listOf("interface/ethernet", "ip/address", "ip/dns", "ip/firewall/filter", "system/script", "ip/pool"), differences.map { it.path.toString() })
        val menus = differences.associateBy { it.path.toString() }
        assertEquals(listOf("WAN" to "Uplink"), menus.getValue("interface/ethernet").changed.map { (a, b) -> a["comment"] to b["comment"] })
        val address = menus.getValue("ip/address")
        assertEquals(listOf("ether2" to "ether3"), address.changed.map { (a, b) -> a["interface"] to b["interface"] })
        assertTrue(address.removed.isEmpty() && address.added.isEmpty())
        assertEquals(listOf("1.1.1.1" to "1.1.1.1,8.8.8.8"), menus.getValue("ip/dns").changed.map { (a, b) -> a["servers"] to b["servers"] })
        val filter = menus.getValue("ip/firewall/filter")
        assertEquals(listOf("drop") to listOf("drop"), filter.removed.map { it["action"] } to filter.added.map { it["action"] })
        val scripts = menus.getValue("system/script")
        assertEquals(listOf("old") to listOf("new"), scripts.removed.map { it["name"] } to scripts.added.map { it["name"] })
        assertEquals(listOf("dhcp"), menus.getValue("ip/pool").added.map { it["name"] })
        assertTrue(compare(after, after).isEmpty())
    }

    @Test
    fun sectionsOfUciPairByTheirNames() {
        val old = configOf(Platform.OpenWrt, "package network\n\nconfig interface 'lan'\n\toption ipaddr '192.168.99.1'\n\npackage firewall\n\nconfig rule\n\toption name 'a'\n")
        val new = configOf(Platform.OpenWrt, "package network\n\nconfig interface 'lan'\n\toption ipaddr '192.168.98.1'\n\npackage firewall\n\nconfig rule\n\toption name 'a'\n\nconfig rule\n\toption name 'b'\n")
        val menus = compare(old, new).associateBy { it.path.toString() }
        assertEquals(listOf("192.168.99.1" to "192.168.98.1"), menus.getValue("network/interface").changed.map { (a, b) -> a["ipaddr"] to b["ipaddr"] })
        assertEquals(listOf("b"), menus.getValue("firewall/rule").added.map { it["name"] })
    }
}
