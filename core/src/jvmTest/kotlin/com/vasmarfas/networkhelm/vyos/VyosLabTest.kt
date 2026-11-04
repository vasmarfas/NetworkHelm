package com.vasmarfas.networkhelm.vyos

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.session.rows
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class VyosLabTest {

    private val routes = MenuPath.parse("protocols/static/route")
    private val dummies = MenuPath.parse("interfaces/dummy")

    private fun vyos(block: suspend (VyosSession) -> Unit) = runBlocking {
        val lab = Lab.require("vyos.host", "vyos.key")
        val login = VyosLogin(lab.getValue("vyos.host"), lab.getValue("vyos.key"), Lab.optional("vyos.port")?.toInt() ?: 443)
        val session = connectVyos(login, TrustPolicy(MemoryTrustStore()) { true })
        withTimeout(120.seconds) { block(session) }
    }

    @Test
    fun theRouterTellsWhatItIsAndShowsItsInterfaces() = vyos { session ->
        assertEquals("vyos", session.device.identity)
        assertTrue(session.device.version.isNotEmpty())
        val ethernet = MenuPath.parse("interfaces/ethernet")
        assertEquals(NodeKind.List, session.describe(ethernet).kind)
        assertEquals(NodeKind.Settings, session.describe(MenuPath.parse("system")).kind)
        val rows = session.watch(ethernet).rows().first { it.isNotEmpty() }
        assertEquals("dhcp", rows.single { it.id == "eth0" }["address"])
    }

    @Test
    fun opModeShowsTheStateOfTheRouter() = vyos { session ->
        val status = MenuPath.parse("status")
        assertEquals(NodeKind.Directory, session.describe(status).kind)
        assertEquals(listOf("print"), session.describe(MenuPath.parse("status/interfaces")).commands)
        val interfaces = session.watch(MenuPath.parse("status/interfaces")).rows().first { it.isNotEmpty() }
        val eth0 = interfaces.single { it.id == "eth0" }
        assertTrue(eth0["ip-address"].orEmpty().contains('/'), eth0.toString())
        assertTrue(eth0["rx-bytes"]?.toLongOrNull() != null, eth0.toString())
        assertTrue(interfaces.single { it.id == "lo" }["ip-address"].orEmpty().contains("::1/128"))
        val routes = session.watch(MenuPath.parse("status/routes")).rows().first { it.isNotEmpty() }
        assertTrue(routes.any { it["protocol"] == "connected" && it["interface"] == "eth0" }, routes.toString())
        session.watch(MenuPath.parse("status/arp")).rows().first()
        val log = session.watch(MenuPath.parse("status/log")).rows().first { it.isNotEmpty() }
        assertTrue(log.all { it["time"] != null && it["program"] != null }, log.filter { it["time"] == null }.take(3).toString())
        assertEquals(log.size, log.distinctBy { it.id }.size)
        val resource = assertNotNull(session.get(MenuPath.parse("system/resource"), null))
        val total = assertNotNull(resource["total-memory"]?.toLongOrNull(), resource.toString())
        assertTrue(resource["free-memory"]?.toLongOrNull()?.let { it in 1..total } == true, resource.toString())
        assertTrue(resource["uptime"].orEmpty().matches(Regex("""(\d+[wdhms])+""")), resource.toString())
        assertTrue(resource["load"].orEmpty().endsWith("%"), resource.toString())
    }

    @Test
    fun aRouteIsAddedChangedAndRemoved() = vyos { session ->
        val route = "192.0.2.0/24"
        try {
            assertEquals(route, session.add(routes, mapOf(NAME to route, "blackhole" to "true", "description" to "rd-test")))
            assertEquals("rd-test", session.get(routes, route)?.get("description"))
            session.set(routes, route, mapOf("description" to "rd-test changed"))
            assertEquals("rd-test changed", session.get(routes, route)?.get("description"))
            assertTrue("set protocols static route 192.0.2.0/24 blackhole" in session.export(routes), session.export(routes))
        } finally {
            if (session.get(routes, route) != null) session.remove(routes, listOf(route))
        }
        assertNull(session.get(routes, route))
    }

    @Test
    fun aDummyInterfaceTurnsOffAndGoes() = vyos { session ->
        try {
            session.add(dummies, mapOf(NAME to "dum9", "address" to "192.0.2.9/32", "description" to "rd-test"))
            assertEquals("false", session.get(dummies, "dum9")?.get("disabled"))
            session.set(dummies, "dum9", mapOf("disabled" to "true"))
            assertEquals("true", session.get(dummies, "dum9")?.get("disabled"))
            session.set(dummies, "dum9", mapOf("address" to "192.0.2.9/32,198.51.100.9/32"))
            assertEquals(setOf("192.0.2.9/32", "198.51.100.9/32"), session.get(dummies, "dum9")?.get("address")?.split(',')?.toSet())
        } finally {
            if (session.get(dummies, "dum9") != null) session.remove(dummies, listOf("dum9"))
        }
        assertNull(session.get(dummies, "dum9"))
    }

    @Test
    fun safeModeRollsBackOrKeepsTheChanges() = vyos { session ->
        val safe = assertNotNull(session.safeMode)
        val route = "198.51.100.0/24"
        try {
            assertTrue(safe.enter())
            session.add(routes, mapOf(NAME to route, "blackhole" to "true"))
            assertEquals(1, safe.actions.first())
            safe.rollback()
            assertNull(session.get(routes, route))
            assertTrue(safe.enter())
            session.add(routes, mapOf(NAME to route, "blackhole" to "true"))
            safe.commit()
            assertNotNull(session.get(routes, route))
        } finally {
            if (session.get(routes, route) != null) session.remove(routes, listOf(route))
        }
    }
}
