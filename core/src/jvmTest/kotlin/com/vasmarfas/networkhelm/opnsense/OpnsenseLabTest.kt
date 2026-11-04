package com.vasmarfas.networkhelm.opnsense

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.search.Finding
import com.vasmarfas.networkhelm.search.find
import com.vasmarfas.networkhelm.search.lookup
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustKind
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OpnsenseLabTest {

    private fun opnsense(block: suspend (OpnsenseSession) -> Unit) = runBlocking {
        val lab = Lab.require("opnsense.host", "opnsense.key", "opnsense.secret", "opnsense.certificate")
        val store = MemoryTrustStore()
        store.put(lab.getValue("opnsense.host"), 443, TrustKind.Tls, lab.getValue("opnsense.certificate"))
        val session = connectOpnsense(OpnsenseLogin(lab.getValue("opnsense.host"), lab.getValue("opnsense.key"), lab.getValue("opnsense.secret")), TrustPolicy(store) { false })
        withTimeout(60.seconds) { block(session) }
    }

    private val version get() = Lab.optional("opnsense.version") ?: "24.7"

    @Test
    fun theFirewallTellsWhatItIsAndShowsItsLists() = opnsense { session ->
        assertTrue(session.device.identity.startsWith("OPNsense."), session.device.identity)
        assertEquals(version to "amd64", session.device.version to session.device.architecture)
        val interfaces = session.watch(MenuPath.parse("interfaces/overview")).first() as ListEvent.Snapshot
        assertTrue(interfaces.items.any { it["device"] == "vtnet0" }, interfaces.items.map { it["device"] }.toString())
        assertTrue(session.count(MenuPath.parse("services/services")) > 0)
        val alias = session.describe(MenuPath.parse("firewall/alias"))
        assertTrue("host" in alias.argument("type")?.choices.orEmpty())
        assertEquals(ValueType.Many(ValueType.Text(), "\n"), alias.argument("content")?.type)
        assertEquals(ValueType.Enumeration(multiple = true), session.describe(MenuPath.parse("firewall/filter")).argument("interface")?.type)
        if (version == "24.7") assertFailsWith<DeviceException> { session.describe(MenuPath.parse("interfaces/bridge")) }
    }

    @Test
    fun theSearchFindsTheAddressOfTheWan() = opnsense { session ->
        val address = Lab.optional("opnsense.wanAddress") ?: Lab.require("opnsense.host").getValue("opnsense.host")
        val hits = find(session, assertNotNull(lookup(address))).toList().filterIsInstance<Finding.Hit>()
        val wan = hits.filter { it.place.path == MenuPath.parse("interfaces/overview") }.map { it.item["device"] }
        assertEquals(listOf(Lab.optional("opnsense.wan") ?: "em0"), wan, hits.toString())
    }

    @Test
    fun everyListOfTheMenuOpens() = opnsense { session ->
        val failed = (grids.map { it.path } + listOf("system/resources", "system/firmware").map(MenuPath::parse)).mapNotNull { path ->
            try {
                session.describe(path)
                session.watch(path).first()
                null
            } catch (e: DeviceException) {
                "$path: ${e.message}"
            }
        }
        val absent = listOfNotNull("leases", "interfaces/bridge".takeIf { version == "24.7" }) + if (version == "24.7") listOf("/api/dnsmasq/", "/api/core/tunables/", "/api/auth/") else emptyList()
        assertTrue(failed.all { line -> absent.any { it in line } }, failed.joinToString("\n"))
    }

    private suspend fun present(session: OpnsenseSession, path: String) = try {
        session.describe(MenuPath.parse(path))
        true
    } catch (e: DeviceException) {
        e.category != ErrorCategory.MissingItem
    }

    private suspend fun ids(session: OpnsenseSession, path: MenuPath) = (session.watch(path).first() as ListEvent.Snapshot).items.map { it.id }

    private suspend fun reconfigure(session: OpnsenseSession) {
        if (Lab.optional("opnsense.apply") != null) assertTrue(assertNotNull(session.safeMode).enter())
    }

    @Test
    fun dnsmasqHostsRangesAndOptionsAreMadeChangedAndTakenAway() = opnsense { session ->
        assumeTrue("$version has no Dnsmasq", present(session, "dnsmasq/host"))
        val lists = listOf(
            Triple(
                MenuPath.parse("dnsmasq/host"),
                mapOf("host" to "rd-test-host", "domain" to "rd-test.example", "ip" to "192.168.1.50", "hwaddr" to "02:00:00:aa:bb:cc", "descr" to "rd-test"),
                mapOf("descr" to "rd-test changed", "local" to "1"),
            ),
            Triple(
                MenuPath.parse("dnsmasq/range"),
                mapOf("interface" to "lan", "start_addr" to "192.168.1.210", "end_addr" to "192.168.1.220", "lease_time" to "3600", "description" to "rd-test"),
                mapOf("lease_time" to "7200"),
            ),
            Triple(
                MenuPath.parse("dnsmasq/option"),
                mapOf("type" to "set", "option" to "6", "interface" to "lan", "value" to "192.168.1.1", "description" to "rd-test"),
                mapOf("value" to "192.168.1.2"),
            ),
        )
        val made = mutableListOf<Pair<MenuPath, String>>()
        try {
            for ((list, values, changes) in lists) {
                val id = session.add(list, values)
                made += list to id
                assertEquals(values.toList(), values.keys.map { it to session.get(list, id)?.get(it) })
                session.set(list, id, changes)
                assertEquals(changes.toList(), changes.keys.map { it to session.get(list, id)?.get(it) })
                assertTrue(id in ids(session, list))
            }
            assertTrue(session.describe(MenuPath.parse("dnsmasq/option")).argument("option")?.labels?.get("6").orEmpty().startsWith("dns-server"))
            assertEquals("LAN", session.describe(MenuPath.parse("dnsmasq/range")).argument("interface")?.labels?.get("lan"))
            val failure = assertFailsWith<DeviceException> { session.set(made.first().first, made.first().second, mapOf("ip" to "not-an-ip")) }
            assertEquals(ErrorCategory.ArgumentValue, failure.category)
            assertTrue(failure.message.orEmpty().startsWith("ip: "), failure.message)
            reconfigure(session)
        } finally {
            for ((list, id) in made) session.remove(list, listOf(id))
            if (made.isNotEmpty()) reconfigure(session)
        }
        for ((list, id) in made) assertFalse(id in ids(session, list))
    }

    @Test
    fun keaSubnetsAndReservationsAreMadeChangedAndTakenAway() = opnsense { session ->
        assumeTrue("$version has no Kea", present(session, "kea/subnet"))
        val subnets = MenuPath.parse("kea/subnet")
        val reservations = MenuPath.parse("kea/reservation")
        val made = mutableListOf<Pair<MenuPath, String>>()
        try {
            val subnet = session.add(subnets, mapOf("subnet" to "10.99.0.0/24", "pools" to "10.99.0.100-10.99.0.150", "option_data.routers" to "10.99.0.1", "description" to "rd-test"))
            made += subnets to subnet
            assertEquals(listOf("10.99.0.0/24", "10.99.0.1"), listOf("subnet", "option_data.routers").map { session.get(subnets, subnet)?.get(it) })
            session.set(subnets, subnet, mapOf("option_data.routers" to "10.99.0.2", "description" to "rd-test changed"))
            assertEquals("10.99.0.2", session.get(subnets, subnet)?.get("option_data.routers"))

            val schema = session.describe(reservations)
            assertEquals("10.99.0.0/24", schema.argument("subnet")?.labels?.get(subnet))
            val options = if (schema.argument("option_data.routers") != null) mapOf("option_data.routers" to "10.99.0.2") else emptyMap()
            val reservation = session.add(
                reservations,
                mapOf("subnet" to subnet, "ip_address" to "10.99.0.101", "hw_address" to "02:00:00:aa:bb:cd", "hostname" to "rd-test-res", "description" to "rd-test") + options,
            )
            made += reservations to reservation
            session.set(reservations, reservation, mapOf("description" to "rd-test changed"))
            val row = (session.watch(reservations).first() as ListEvent.Snapshot).items.single { it.id == reservation }
            assertEquals(listOf("10.99.0.101", "rd-test changed"), listOf("ip_address", "description").map { row[it] })
            assertTrue(row["subnet"] in listOf(subnet, "10.99.0.0/24"), row["subnet"])
            assertEquals(options.toList(), options.keys.map { it to session.get(reservations, reservation)?.get(it) })
            val failure = assertFailsWith<DeviceException> { session.set(reservations, reservation, mapOf("ip_address" to "10.98.0.5")) }
            assertEquals(ErrorCategory.ArgumentValue, failure.category)
            assertTrue(failure.message.orEmpty().startsWith("ip_address: "), failure.message)
            reconfigure(session)
        } finally {
            for ((list, id) in made.reversed()) session.remove(list, listOf(id))
            if (made.isNotEmpty()) reconfigure(session)
        }
        for ((list, id) in made) assertFalse(id in ids(session, list))
    }

    @Test
    fun cronTunablesUsersAndGroupsAreMadeChangedAndTakenAway() = opnsense { session ->
        val made = mutableListOf<Pair<MenuPath, String>>()
        try {
            val cron = MenuPath.parse("system/cron")
            val job = session.add(
                cron,
                mapOf("disabled" to "yes", "minutes" to "0", "hours" to "3", "days" to "*", "months" to "*", "weekdays" to "*", "who" to "root", "command" to "firmware poll", "description" to "rd-test"),
            )
            made += cron to job
            session.set(cron, job, mapOf("hours" to "4"))
            assertEquals(listOf("true", "firmware poll", "4"), listOf("disabled", "command", "hours").map { session.get(cron, job)?.get(it) })
            assertEquals("Firmware update check", session.describe(cron).argument("command")?.labels?.get("firmware poll"))
            assertTrue(session.describe(cron).argument("origin")?.readOnly == true)

            if (present(session, "system/tunables")) {
                val tunables = MenuPath.parse("system/tunables")
                val tunable = session.add(tunables, mapOf("tunable" to "rd.test.value", "value" to "1", "descr" to "rd-test"))
                made += tunables to tunable
                session.set(tunables, tunable, mapOf("value" to "2"))
                assertEquals(listOf("rd.test.value", "2"), listOf("tunable", "value").map { session.get(tunables, tunable)?.get(it) })
            }
            if (present(session, "system/users")) {
                val users = MenuPath.parse("system/users")
                val groups = MenuPath.parse("system/groups")
                val user = session.add(users, mapOf("name" to "rd-test-user", "password" to "Rd-${UUID.randomUUID()}", "disabled" to "yes", "descr" to "rd-test"))
                made += users to user
                val group = session.add(groups, mapOf("name" to "rd-test-group", "description" to "rd-test", "member" to "0"))
                made += groups to group
                session.set(users, user, mapOf("descr" to "rd-test changed", "email" to "rd-test@example.invalid"))
                val row = (session.watch(users).first() as ListEvent.Snapshot).items.single { it.id == user }
                assertEquals(listOf("rd-test-user", "true", "rd-test changed"), listOf("name", "disabled", "descr").map { row[it] })
                assertFalse(listOf("password", "apikeys", "otp_seed").any { it in row.values })
                assertEquals("0", session.get(groups, group)?.get("member"))
                assertEquals("root", session.describe(groups).argument("member")?.labels?.get("0"))
                val failure = assertFailsWith<DeviceException> { session.set(users, user, mapOf("name" to "")) }
                assertEquals(ErrorCategory.ArgumentValue, failure.category)
            }
            reconfigure(session)
        } finally {
            for ((list, id) in made.reversed()) session.remove(list, listOf(id))
            if (made.isNotEmpty()) reconfigure(session)
        }
        for ((list, id) in made) assertFalse(id in ids(session, list))
    }

    @Test
    fun theSystemWindowsShowTheStateOfTheFirewall() = opnsense { session ->
        val resources = assertNotNull(session.get(MenuPath.parse("system/resources"), null))
        assertEquals(session.device.identity, resources["hostname"])
        assertTrue(resources["memory_total"]?.toLongOrNull()?.let { it > 0 } == true, resources.toString())
        assertTrue("/: " in resources["disks"].orEmpty(), resources["disks"])
        assertTrue(resources["uptime"].orEmpty().isNotEmpty())
        assertEquals(version, assertNotNull(session.get(MenuPath.parse("system/firmware"), null))["product_version"])
        val gateways = (session.watch(MenuPath.parse("system/gateways")).first() as ListEvent.Snapshot).items
        assertTrue(gateways.all { it["name"].orEmpty().isNotEmpty() })
        val certificates = (session.watch(MenuPath.parse("system/certificates")).first() as ListEvent.Snapshot).items
        assertTrue(certificates.isNotEmpty())
        assertTrue(certificates.all { it["valid_to"].orEmpty().matches(Regex("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d""")) }, certificates.map { it["valid_to"] }.toString())
        assertFalse(certificates.any { "prv" in it.values || "prv_payload" in it.values })
    }

    @Test
    fun applyPutsARuleIntoEffect() = opnsense { session ->
        Lab.require("opnsense.apply")
        val rules = MenuPath.parse("firewall/filter")
        val safe = assertNotNull(session.safeMode)
        val mine = Filter.Equals("description", "rd-test-apply")
        try {
            session.add(rules, mapOf("disabled" to "yes", "action" to "block", "interface" to "lan", "protocol" to "TCP", "destination_port" to "8291", "description" to "rd-test-apply"))
            assertEquals(1, safe.actions.first())
            assertTrue(safe.enter())
            assertEquals(0, safe.actions.first())
            if (safe.active.first()) safe.commit()
            assertFalse(safe.active.first())
            assertEquals(1, session.count(rules, mine))
        } finally {
            session.remove(rules, (session.watch(rules, mine).first() as ListEvent.Snapshot).items.map { it.id })
            safe.enter()
            if (safe.active.first()) safe.commit()
        }
        assertEquals(0, session.count(rules, mine))
    }

    @Test
    fun aRuleAppliedUnderTheSavepointGoesOnRollback() = opnsense { session ->
        Lab.require("opnsense.apply")
        assumeTrue("$version has no savepoint", version != "26.7")
        val rules = MenuPath.parse("firewall/filter")
        val safe = assertNotNull(session.safeMode)
        val mine = Filter.Equals("description", "rd-test-rollback")
        suspend fun clear() {
            val left = (session.watch(rules, mine).first() as ListEvent.Snapshot).items.map { it.id }
            if (left.isEmpty()) return
            session.remove(rules, left)
            safe.enter()
            if (safe.active.first()) safe.commit()
        }
        clear()
        try {
            session.add(rules, mapOf("disabled" to "yes", "action" to "block", "interface" to "lan", "protocol" to "TCP", "destination_port" to "8292", "description" to "rd-test-rollback"))
            assertTrue(safe.enter())
            assertTrue(safe.active.first())
            safe.rollback()
            assertFalse(safe.active.first())
            assertEquals(0, session.count(rules, mine))
        } finally {
            clear()
        }
    }

    @Test
    fun anAliasIsMadeChangedAndTakenAway() = opnsense { session ->
        val aliases = MenuPath.parse("firewall/alias")
        val id = session.add(aliases, mapOf("name" to "rd_test", "type" to "host", "content" to "192.0.2.1", "description" to "rd-test"))
        try {
            assertEquals("192.0.2.1", session.get(aliases, id)?.get("content"))
            session.set(aliases, id, mapOf("content" to "192.0.2.1\n192.0.2.2", "description" to "rd-test тест"))
            val alias = session.get(aliases, id)
            assertEquals("192.0.2.1\n192.0.2.2", alias?.get("content"))
            assertEquals("rd-test тест", alias?.get("description"))
            assertEquals("false", alias?.get("disabled"))
            session.set(aliases, id, mapOf("disabled" to "yes"))
            assertEquals("true", session.get(aliases, id)?.get("disabled"))
            assertEquals(1, session.count(aliases, Filter.Equals("name", "rd_test")))
            val failure = assertFailsWith<DeviceException> { session.add(aliases, mapOf("name" to "rd_test", "type" to "host", "content" to "192.0.2.3")) }
            assertTrue(failure.message.orEmpty().startsWith("name: "), failure.message)
        } finally {
            val made = session.watch(aliases, Filter.Equals("name", "rd_test")).first() as ListEvent.Snapshot
            session.remove(aliases, made.items.map { it.id })
        }
        assertEquals(0, session.count(aliases, Filter.Equals("name", "rd_test")))
    }

    @Test
    fun aRuleShowsAllItsFieldsInTheList() = opnsense { session ->
        val rules = MenuPath.parse("firewall/filter")
        val mine = Filter.Equals("description", "rd-test")
        try {
            session.add(rules, mapOf("disabled" to "yes", "action" to "block", "interface" to "lan", "protocol" to "TCP", "destination_port" to "8291", "description" to "rd-test"))
            val rule = (session.watch(rules, mine).first() as ListEvent.Snapshot).items.single()
            assertEquals(listOf("true", "block", "lan", "TCP", "8291"), listOf("disabled", "action", "interface", "protocol", "destination_port").map { rule[it] })
        } finally {
            session.remove(rules, (session.watch(rules, mine).first() as ListEvent.Snapshot).items.map { it.id })
        }
        assertEquals(0, session.count(rules, mine))
    }

    @Test
    fun rulesChangePlaces() = opnsense { session ->
        val rules = MenuPath.parse("firewall/filter")
        val others = (session.watch(rules).first() as ListEvent.Snapshot).items.filterNot { it["description"].orEmpty().startsWith("rd-test") }
        assumeTrue("the firewall has rules besides rd-test, which a move would renumber", others.isEmpty())
        val made = mutableListOf<String>()
        try {
            for (n in 1..3) {
                made += session.add(rules, mapOf("disabled" to "yes", "sequence" to "${9000 + n}", "action" to "block", "interface" to "lan", "description" to "rd-test $n"))
            }
            suspend fun order() = (session.watch(rules).first() as ListEvent.Snapshot).items.filter { it.id in made }.map { it["description"] }
            assertEquals(listOf("rd-test 1", "rd-test 2", "rd-test 3"), order())
            session.move(rules, listOf(made[2]), made[0])
            assertEquals(listOf("rd-test 3", "rd-test 1", "rd-test 2"), order())
            session.move(rules, listOf(made[2]), null)
            assertEquals(listOf("rd-test 1", "rd-test 2", "rd-test 3"), order())
        } finally {
            session.remove(rules, made)
        }
    }
}
