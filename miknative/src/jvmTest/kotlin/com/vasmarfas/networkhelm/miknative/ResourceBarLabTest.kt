package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.session.watchSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ResourceBarLabTest {

    @Test
    fun theResourcesTheClockAndTheTrafficOverPort8291() = runBlocking<Unit> {
        val lab = Lab.require("chr.host", "chr.user", "chr.password")
        withTimeout(120.seconds) {
            val session = connectNativeHost(lab.getValue("chr.host"), 8291, lab.getValue("chr.user"), lab.getValue("chr.password"), this)
            try {
                val resource = session.watchSettings(MenuPath.parse("system/resource"), listOf("uptime", "cpu-load", "free-memory", "total-memory", "free-hdd-space", "total-hdd-space")).first()
                assertTrue(resource["cpu-load"].orEmpty().isNotEmpty() && resource["free-hdd-space"].orEmpty().isNotEmpty() && resource["version"] == null, resource.toString())
                val clock = session.watchSettings(MenuPath.parse("system/clock"), listOf("date", "time")).first()
                assertTrue(clock["date"].orEmpty().isNotEmpty() && clock["time"].orEmpty().isNotEmpty(), clock.toString())
                val routes = assertIs<ListEvent.Snapshot>(session.watch(MenuPath.parse("ip/route"), Filter.Equals("dst-address", "0.0.0.0/0")).first()).items
                println("BAR routes ${routes.map { it.values.filterKeys { key -> key in setOf("gateway", "immediate-gw", "active", "gateway-status") } }}")
                val rates = session.run(MenuPath.parse("interface"), "monitor-traffic", mapOf("interface" to "ether1")).first()
                println("BAR rates $rates")
                assertTrue(rates["rx-bits-per-second"] != null && rates["tx-bits-per-second"] != null, rates.toString())
            } finally {
                session.close()
            }
        }
    }
}
