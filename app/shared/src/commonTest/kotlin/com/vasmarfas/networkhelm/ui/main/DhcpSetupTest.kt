package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.model.MenuPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DhcpSetupTest {

    @Test
    fun theAddressOfTheInterfaceGivesTheSetup() {
        val lan = assertNotNull(dhcpPlan("192.168.88.1/24"))
        assertEquals(listOf("192.168.88.0/24", "192.168.88.1", "192.168.88.2-192.168.88.254", "192.168.88.1"), listOf(lan.network, lan.gateway, lan.pool, lan.dns))
        assertEquals("10.0.0.129,10.0.0.131-10.0.0.254", dhcpPlan("10.0.0.130/25")?.pool)
        assertEquals("10.0.0.2", dhcpPlan("10.0.0.1/30")?.pool)
        assertNull(dhcpPlan("10.0.0.1/31"))
        assertNull(dhcpPlan("10.0.0.1/32"))
        assertNull(dhcpPlan("fe80::1/64"))
    }

    @Test
    fun thePoolComesFirstAndTheServerLast() {
        val plan = assertNotNull(dhcpPlan("192.168.88.1/24"))
        val writes = dhcpWrites("bridge1", plan, "30m", "dhcp_pool0", "dhcp1")
        assertEquals(listOf("ip/pool", "ip/dhcp-server/network", "ip/dhcp-server").map(MenuPath::parse), writes.map { it.first })
        assertEquals(mapOf("name" to "dhcp1", "interface" to "bridge1", "address-pool" to "dhcp_pool0", "lease-time" to "30m"), writes.last().second)
        val bare = dhcpWrites("bridge1", DhcpPlan("192.168.88.0/24", "", "192.168.88.2-192.168.88.254", ""), "", "dhcp_pool0", "dhcp1")
        assertEquals(mapOf("address" to "192.168.88.0/24"), bare[1].second)
        assertEquals(setOf("name", "interface", "address-pool"), bare[2].second.keys)
    }
}
