package com.vasmarfas.networkhelm.pfsense

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustKind
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PfsenseLabTest {

    private fun pfsense(block: suspend (PfsenseSession) -> Unit) = runBlocking {
        val lab = Lab.require("pfsense.host", "pfsense.user", "pfsense.secret", "pfsense.certificate")
        val store = MemoryTrustStore()
        store.put(lab.getValue("pfsense.host"), 443, TrustKind.Tls, lab.getValue("pfsense.certificate"))
        val session = connectPfsense(PfsenseLogin(lab.getValue("pfsense.host"), lab.getValue("pfsense.user"), lab.getValue("pfsense.secret")), TrustPolicy(store) { false })
        withTimeout(120.seconds) { block(session) }
    }

    @Test
    fun theFirewallTellsWhatItIsAndEveryListOpens() = pfsense { session ->
        assertEquals("pfSense", session.device.identity)
        assertEquals(Lab.optional("pfsense.version") ?: "2.7.2", session.device.version)
        val failed = endpoints.map { it.path }.mapNotNull { path ->
            try {
                session.describe(path)
                session.watch(path).first()
                null
            } catch (e: DeviceException) {
                "$path: ${e.message}"
            }
        }
        assertEquals(emptyList(), failed, failed.joinToString("\n"))
        val rule = session.describe(MenuPath.parse("firewall/rule"))
        assertTrue("block" in rule.argument("type")?.choices.orEmpty(), rule.argument("type").toString())
        assertEquals(ValueType.YesNo, rule.argument("disabled")?.type)
        val resource = assertNotNull(session.get(MenuPath.parse("system/resource"), null))
        assertTrue(resource["memory-usage"]?.toIntOrNull() in 1..100, resource.toString())
        assertTrue(resource["uptime"].orEmpty().matches(Regex("""(\d+[dhms])+""")), resource.toString())
    }

    @Test
    fun anAliasIsMadeChangedAndTakenAway() = pfsense { session ->
        val aliases = MenuPath.parse("firewall/alias")
        val mine = Filter.Equals("name", "rd_test")
        try {
            session.add(aliases, mapOf("name" to "rd_test", "type" to "host", "address" to "192.0.2.1", "descr" to "rd-test"))
            val made = (session.watch(aliases, mine).first() as ListEvent.Snapshot).items.single()
            assertEquals("192.0.2.1", made["address"])
            session.set(aliases, made.id, mapOf("address" to "192.0.2.1,192.0.2.2", "descr" to "rd-test тест"))
            val changed = assertNotNull(session.get(aliases, made.id))
            assertEquals("192.0.2.1,192.0.2.2", changed["address"])
            assertEquals("rd-test тест", changed["descr"])
        } finally {
            session.remove(aliases, (session.watch(aliases, mine).first() as ListEvent.Snapshot).items.map { it.id })
            assertNotNull(session.safeMode).enter()
        }
        assertEquals(0, session.count(aliases, mine))
    }

    @Test
    fun aRuleIsAppliedMovedAndTakenAway() = pfsense { session ->
        val rules = MenuPath.parse("firewall/rule")
        val safe = assertNotNull(session.safeMode)
        val mine = Filter.Equals("descr", "rd-test")
        try {
            session.add(rules, mapOf(
                "type" to "block", "interface" to "lan", "ipprotocol" to "inet", "protocol" to "tcp", "source" to "any",
                "destination" to "any", "destination_port" to "8291", "descr" to "rd-test", "disabled" to "true",
            ))
            assertEquals(1, safe.actions.first())
            assertTrue(safe.enter())
            assertEquals(0, safe.actions.first())
            val made = (session.watch(rules, mine).first() as ListEvent.Snapshot).items.single()
            assertEquals(listOf("block", "lan", "tcp", "8291", "true"), listOf("type", "interface", "protocol", "destination_port", "disabled").map { made[it] })
            val first = (session.watch(rules).first() as ListEvent.Snapshot).items.first()
            session.move(rules, listOf(made.id), first.id)
            assertEquals("rd-test", (session.watch(rules).first() as ListEvent.Snapshot).items.first()["descr"])
        } finally {
            session.remove(rules, (session.watch(rules, mine).first() as ListEvent.Snapshot).items.map { it.id })
            safe.enter()
        }
        assertEquals(0, session.count(rules, mine))
    }
}
