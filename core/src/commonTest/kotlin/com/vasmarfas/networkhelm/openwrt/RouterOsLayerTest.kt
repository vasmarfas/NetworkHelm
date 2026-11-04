package com.vasmarfas.networkhelm.openwrt

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
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.session.SafeMode
import com.vasmarfas.networkhelm.ssh.SshSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RouterOsLayerTest {

    @Test
    fun aPoolOfDnsmasqIsARangeOfAddresses() {
        assertEquals("192.168.1.100-192.168.1.249", dhcpRange("192.168.1.1/24", 100, 150))
        assertEquals("192.168.1.200-192.168.1.254", dhcpRange("192.168.1.1/24", 200, 150))
        assertNull(dhcpRange("192.168.1.1/24", 300, 10))
    }

    @Test
    fun thePortsOfFirewall4ReadAsRouterOsWritesThem() {
        assertEquals("22,80,443", ports("22 80 443"))
        assertEquals("1000-2000", ports("1000-2000"))
        assertEquals("22 80 443", uciPorts("22,80,443"))
    }

    @Test
    fun aStationOfHostapdIsARowOfTheRegistrationTable() = runTest {
        val station = Item(
            "phy0-ap0 aa:bb:cc:00:00:09",
            mapOf("interface" to "phy0-ap0", "mac-address" to "aa:bb:cc:00:00:09", "signal" to "-48", "connected-time" to "1h2m5s", "rx-bytes" to "1000", "tx-bytes" to "2000"),
        )
        val row = assertNotNull(RouterOsLayer(Stations(station)).get(MenuPath.parse("interface/wifi/registration-table"), null, null))
        assertEquals(listOf("-48", "1h2m5s", "2000,1000"), listOf(row["signal"], row["uptime"], row["bytes"]))
    }

    private class Stations(private val station: Item) : DeviceSession {
        override val device = DeviceInfo(Platform.OpenWrt, "lab", "25.12.4", "aarch64", "Cudy TR3000", emptySet(), emptySet())
        override val capabilities = Capabilities(true, true, true, SafeModeKind.None, terminal = false, files = false, export = false, menuContents = false)
        override suspend fun describe(path: MenuPath) = MenuSchema(path, NodeKind.List, emptyList(), listOf("print"), emptyList())
        override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> =
            flowOf(ListEvent.Snapshot(if (path == MenuPath.parse("status/wireless-clients")) listOf(station) else emptyList()))
        override suspend fun get(path: MenuPath, id: String?, properties: List<String>?): Item? = null
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
}
