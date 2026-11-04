package com.vasmarfas.networkhelm.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceModeTest {

    private val device = DeviceInfo(
        Platform.RouterOs, "lab", "7.26", "x86_64", "CHR", setOf("routeros"), setOf("read"),
        deviceMode = mapOf("mode" to "advanced", "traffic-gen" to "false", "sniffer" to "true", "fetch" to "no"),
    )

    @Test
    fun aMenuIsClosedByTheFalsePropertyThatCoversIt() {
        assertEquals("traffic-gen", device.closedBy(MenuPath.parse("tool/traffic-generator/stream")))
        assertEquals("traffic-gen", device.closedBy(MenuPath.parse("tool/flood-ping")))
        assertEquals("fetch", device.closedBy(MenuPath.parse("tool/fetch")))
        assertNull(device.closedBy(MenuPath.parse("tool/sniffer")), "sniffer is allowed")
        assertNull(device.closedBy(MenuPath.parse("tool/ping")), "ping has no property")
        assertNull(device.closedBy(MenuPath.parse("tool/fetcher")), "a menu is matched by whole segments")
    }
}
