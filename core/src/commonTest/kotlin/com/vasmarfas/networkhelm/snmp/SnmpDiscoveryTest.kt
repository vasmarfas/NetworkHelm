package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.ValueType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SnmpDiscoveryTest {

    private val device = DeviceInfo(Platform.Snmp, "rd-test-switch", "", "", "", emptySet(), setOf("read", "write"))

    private val vlans = MenuPath.parse("mib/Q-BRIDGE-MIB/dot1qVlanStaticTable")
    private val status = oid("1.3.6.1.2.1.17.7.1.4.3.1.5")
    private val name = oid("1.3.6.1.2.1.17.7.1.4.3.1.1")
    private val egress = oid("1.3.6.1.2.1.17.7.1.4.3.1.2")

    private fun text(value: String) = SnmpValue.Octets(value.encodeToByteArray())

    private fun number(value: Long) = SnmpValue.Number(INTEGER, value)

    private fun bridge() = MibAgent().apply {
        this["1.3.6.1.2.1.1.5.0"] = text("rd-test-switch")
        this["1.3.6.1.2.1.2.2.1.2.1"] = text("eth1")
        for (port in 1L..2L) {
            this["1.3.6.1.2.1.17.1.4.1.1.$port"] = number(port)
            this["1.3.6.1.2.1.17.1.4.1.2.$port"] = number(port + 10)
        }
        this["1.3.6.1.2.1.17.5.1.1.4.0.0.94.0.83.25.0"] = number(3)
        this["1.3.6.1.2.1.17.7.1.1.2.0"] = number(4094)
        this["1.3.6.1.2.1.17.7.1.1.5.0"] = number(1)
        for ((vlan, title) in listOf(1L to "default", 5L to "office")) {
            values[name + listOf(vlan)] = text(title)
            values[egress + listOf(vlan)] = SnmpValue.Octets(byteArrayOf(0x80.toByte(), 0))
            values[status + listOf(vlan)] = number(1)
        }
        statuses += status
        this["1.3.6.1.4.1.14988.1.1.3.10.0"] = number(45)
        this["1.3.6.1.4.1.14988.1.1.4.1.0"] = text("7.25")
        this["1.3.6.1.4.1.14988.1.1.5.0"] = SnmpValue.Number(COUNTER32, 7)
    }

    private fun enterprise(count: Int, latency: Long = 0) = MibAgent(latency).apply {
        for (i in 1..count) this["1.3.6.1.4.1.99999.1.$i.0"] = number(i.toLong())
    }

    @Test
    fun theWalkSortsScalarsTablesAndSubtreesTheDictionaryDoesNotKnow() = runTest {
        val found = SnmpClient(bridge(), "rd-test").discover(SnmpMib.standard)
        assertTrue(found.complete)
        assertEquals(setOf("sysName", "dot1qMaxVlanId", "dot1qGvrpStatus"), found.scalars.keys.map { it.name }.toSet())
        assertEquals(number(4094), found.scalars.entries.single { it.key.name == "dot1qMaxVlanId" }.value)
        val tables = found.tables.mapKeys { it.key.name }.mapValues { (_, columns) -> columns.map { it.name }.toSet() }
        assertEquals(setOf("dot1qVlanStaticName", "dot1qVlanStaticEgressPorts", "dot1qVlanStaticRowStatus"), tables["dot1qVlanStaticEntry"])
        assertEquals(setOf("dot1dBasePort", "dot1dBasePortIfIndex"), tables["dot1dBasePortEntry"])
        assertEquals(setOf(oid("1.3.6.1.4.1.14988.1")), found.unknown)
    }

    @Test
    fun theWalkStopsAtItsBudgetOfVarbindsAndKeepsWhatItFound() = runTest {
        val agent = enterprise(300)
        val found = SnmpClient(agent, "rd-test").discover(SnmpMib.standard, WalkBudget(bindings = 100), roots = listOf(SnmpMib.ENTERPRISES))
        assertFalse(found.complete)
        assertEquals(setOf(oid("1.3.6.1.4.1.99999.1")), found.unknown)
        assertEquals(3, agent.requests, "48 varbinds an answer, the third passes 100")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theWalkStopsAtItsBudgetOfTime() = runTest {
        val agent = enterprise(300, latency = 1_000)
        val found = SnmpClient(agent, "rd-test").discover(SnmpMib.standard, WalkBudget(time = 2_500.milliseconds), testScheduler.timeSource, listOf(SnmpMib.ENTERPRISES))
        assertFalse(found.complete)
        assertEquals(3, agent.requests)
    }

    @Test
    fun aSubtreeTheDictionaryDoesNotKnowIsSampledAndSkipped() = runTest {
        val agent = enterprise(300)
        val found = SnmpClient(agent, "rd-test").discover(SnmpMib.standard, WalkBudget(raw = 10), roots = listOf(SnmpMib.ENTERPRISES))
        assertTrue(found.complete)
        assertEquals(setOf(oid("1.3.6.1.4.1.99999.1")), found.unknown)
        assertEquals(2, agent.requests)
    }

    @Test
    fun anAgentThatAnswersAnEarlierOidDoesNotTurnTheWalkIntoALoop() = runTest {
        val agent = enterprise(10).apply { backwardsAfter = oid("1.3.6.1.4.1.99999.1.5.0") }
        val found = SnmpClient(agent, "rd-test").discover(SnmpMib.standard, roots = listOf(SnmpMib.ENTERPRISES))
        assertTrue(found.complete)
        assertEquals(1, agent.requests)
        assertEquals(setOf(oid("1.3.6.1.4.1.99999.1")), found.unknown)
    }

    @Test
    fun aTableIsSampledByTheWalkAndReadWholeByItsWindow() = runTest {
        val agent = MibAgent().apply {
            for (port in 1L..30L) {
                this["1.3.6.1.2.1.17.1.4.1.1.$port"] = number(port)
                this["1.3.6.1.2.1.17.1.4.1.2.$port"] = number(port)
            }
        }
        val client = SnmpClient(agent, "rd-test")
        val found = client.discover(SnmpMib.standard, WalkBudget(rows = 5))
        assertEquals(setOf("dot1dBasePort", "dot1dBasePortIfIndex"), found.tables.values.single().map { it.name }.toSet())
        assertTrue(agent.requests <= 6, "${agent.requests} requests")
        val session = SnmpSession(client, device)
        assertEquals(30, session.count(MenuPath.parse("mib/BRIDGE-MIB/dot1dBasePortTable")))
    }

    @Test
    fun whatWasFoundGoesToTheMibMenuBesideTheHandMadeWindows() = runTest {
        val packs = SnmpSession(SnmpClient(bridge(), "rd-test"), device).packs(null)
        assertTrue(packs.all { it.platform == "snmp" && it.menu.first() == "MIB" })
        val bridge = packs.single { it.menu == listOf("MIB", "Q-BRIDGE-MIB") }
        val paths = bridge.window.tabs.map { it.path }
        assertTrue("$vlans" in paths && "mib/Q-BRIDGE-MIB/dot1qBase" in paths, "$paths")
        assertEquals(listOf("dot1qVlanIndex", "dot1qVlanStaticName", "dot1qVlanStaticEgressPorts"), bridge.paths.getValue("$vlans").columns.take(3))
        assertEquals("Name", bridge.labels["dot1qVlanStaticName"])
        assertTrue("mib/BRIDGE-MIB/dot1dBasePortTable" in packs.single { it.menu == listOf("MIB", "BRIDGE-MIB") }.window.tabs.map { it.path })
        val vendor = packs.single { it.menu == listOf("MIB", "MikroTik") }.window.tabs.single()
        assertEquals("mib/oid/1.3.6.1.4.1.14988.1" to "enterprises.14988.1", vendor.path to vendor.title)
        val all = packs.flatMap { pack -> pack.window.tabs.map { it.path } }
        assertTrue(all.none { it.endsWith("/ifTable") || it.endsWith("/system") }, "$all")
    }

    @Test
    fun aTableShowsItsValuesAsAPersonReadsThemWithItsIndex() = runTest {
        val session = SnmpSession(SnmpClient(bridge(), "rd-test"), device)
        val schema = session.describe(vlans)
        assertEquals(NodeKind.List, schema.kind)
        assertTrue(schema.commands.containsAll(listOf("set", "add", "remove")))
        val index = schema.arguments.first()
        assertEquals("index" to true, index.name to index.fixed)
        assertEquals("6", schema.prefill["index"])
        assertTrue(schema.argument("dot1qVlanIndex")?.readOnly == true && schema.argument("dot1qVlanStaticName")?.readOnly == false)
        assertTrue("createAndGo" in schema.argument("dot1qVlanStaticRowStatus")?.choices.orEmpty())
        val office = assertNotNull(session.get(vlans, "5"))
        assertEquals(
            listOf("5", "5", "office", "80:00", "active"),
            listOf("index", "dot1qVlanIndex", "dot1qVlanStaticName", "dot1qVlanStaticEgressPorts", "dot1qVlanStaticRowStatus").map { office[it] },
        )
        assertTrue("Q-BRIDGE-MIB" in session.describe(MenuPath.parse("mib")).children)
    }

    @Test
    fun aRowIsCreatedWithCreateAndGoAndItsColumnsInOneSet() = runTest {
        val agent = bridge()
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        assertEquals("10", session.add(vlans, mapOf("index" to "10", "dot1qVlanStaticName" to "rd-test")))
        val sent = agent.sets.last()
        assertEquals(listOf(status + listOf(10L), name + listOf(10L)), sent.map { it.first })
        assertEquals(number(4), sent.first().second)
        assertEquals("rd-test", session.get(vlans, "10")?.get("dot1qVlanStaticName"))
    }

    @Test
    fun anAgentThatBuildsARowInStepsGetsCreateAndWaitTheColumnsAndActive() = runTest {
        val agent = bridge().apply { stepsOnly = true }
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        session.add(vlans, mapOf("index" to "10", "dot1qVlanStaticName" to "rd-test"))
        val steps = agent.sets.takeLast(4)
        assertEquals(number(4), steps[0].first().second)
        assertEquals(listOf(status + listOf(10L) to number(5)), steps[1])
        assertEquals(listOf(name + listOf(10L)), steps[2].map { it.first })
        assertEquals(listOf(status + listOf(10L) to number(1)), steps[3])
        assertEquals(number(1), agent.values[status + listOf(10L)])
    }

    @Test
    fun aRowThatCannotBecomeActiveIsDestroyedAndTheRefusalShown() = runTest {
        val agent = bridge().apply {
            stepsOnly = true
            required += egress
        }
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        val failure = assertFailsWith<DeviceException> { session.add(vlans, mapOf("index" to "10", "dot1qVlanStaticName" to "rd-test")) }
        assertTrue("inconsistentValue" in failure.message.orEmpty() && "dot1qVlanStaticRowStatus.10" in failure.message.orEmpty(), failure.message)
        assertEquals(listOf(status + listOf(10L) to number(6)), agent.sets.last())
        assertTrue(agent.values.keys.none { it == status + listOf(10L) || it == name + listOf(10L) })
    }

    @Test
    fun aRowIsRemovedWithDestroyAndAChangeOfARowGoesInOneSet() = runTest {
        val agent = bridge()
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        session.describe(MenuPath.parse("mib"))
        val before = agent.requests
        session.set(vlans, "5", mapOf("dot1qVlanStaticName" to "lab", "dot1qVlanStaticEgressPorts" to "C0:00"))
        assertEquals(1, agent.requests - before)
        assertContentEquals(byteArrayOf(0xC0.toByte(), 0), (agent.values.getValue(egress + listOf(5L)) as SnmpValue.Octets).bytes)
        session.remove(vlans, listOf("5"))
        assertEquals(listOf(status + listOf(5L) to number(6)), agent.sets.last())
        assertTrue(name + listOf(5L) !in agent.values)
    }

    @Test
    fun whatTheDictionaryCallsReadOnlyIsRefusedBeforeAnythingIsSent() = runTest {
        val agent = bridge()
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        val ports = MenuPath.parse("mib/BRIDGE-MIB/dot1dBasePortTable")
        session.describe(ports)
        val before = agent.requests
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { session.set(ports, "1", mapOf("dot1dBasePortIfIndex" to "3")) }.category)
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { session.add(ports, mapOf("index" to "3")) }.category)
        assertEquals(ErrorCategory.ArgumentValue, assertFailsWith<DeviceException> { session.set(vlans, "5", mapOf("dot1qVlanStaticRowStatus" to "sideways")) }.category)
        val moved = assertFailsWith<DeviceException> { session.move(vlans, listOf("5"), "1") }
        assertTrue(moved.category == ErrorCategory.Unsupported && "order" in moved.message.orEmpty(), moved.message)
        assertEquals(before, agent.requests)
    }

    @Test
    fun aTableWithoutRowStatusRemovesARowByItsInvalidValue() = runTest {
        val agent = bridge()
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        val statics = MenuPath.parse("mib/BRIDGE-MIB/dot1dStaticTable")
        val row = assertNotNull(session.get(statics, null))
        assertEquals(listOf("0.0.94.0.83.25.0", "00:00:5E:00:53:19", "0", "permanent"), listOf(row.id, row["dot1dStaticAddress"], row["dot1dStaticReceivePort"], row["dot1dStaticStatus"]))
        assertFalse("add" in session.describe(statics).commands)
        session.remove(statics, listOf(row.id))
        assertEquals(listOf(oid("1.3.6.1.2.1.17.5.1.1.4.0.0.94.0.83.25.0") to number(2)), agent.sets.last())
    }

    @Test
    fun scalarsOfAGroupStandAsOneFormThatWrites() = runTest {
        val agent = bridge()
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        val base = MenuPath.parse("mib/Q-BRIDGE-MIB/dot1qBase")
        val schema = session.describe(base)
        assertEquals(NodeKind.Settings, schema.kind)
        assertEquals(ValueType.Enumeration(closed = true), schema.argument("dot1qGvrpStatus")?.type)
        assertTrue(schema.argument("dot1qMaxVlanId")?.readOnly == true)
        val values = assertNotNull(session.get(base, null))
        assertEquals(listOf("enabled", "4094"), listOf(values["dot1qGvrpStatus"], values["dot1qMaxVlanId"]))
        session.set(base, null, mapOf("dot1qGvrpStatus" to "disabled"))
        assertEquals(listOf(oid("1.3.6.1.2.1.17.7.1.1.5.0") to number(2)), agent.sets.last())
    }

    @Test
    fun anUnknownSubtreeShowsItsOidsAndWritesAValueInTheTypeTheAgentGave() = runTest {
        val agent = bridge()
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        val raw = MenuPath.parse("mib/oid/1.3.6.1.4.1.14988.1")
        assertEquals(NodeKind.List, session.describe(raw).kind)
        val rows = (session.watch(raw).first() as ListEvent.Snapshot).items
        assertEquals(
            listOf(listOf("1.3.10.0", "INTEGER", "45"), listOf("1.4.1.0", "OCTET STRING", "7.25"), listOf("1.5.0", "Counter32", "7")),
            rows.map { listOf(it["oid"], it["type"], it["value"]) },
        )
        session.set(raw, "1.3.10.0", mapOf("value" to "50"))
        assertEquals(listOf(oid("1.3.6.1.4.1.14988.1.1.3.10.0") to number(50)), agent.sets.last())
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { session.set(raw, "1.5.0", mapOf("value" to "8")) }.category)
    }

    @Test
    fun aRefusalNamesTheObjectAndTheStatus() = runTest {
        val agent = bridge().apply { refused[name + listOf(5L)] = 17 }
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device)
        val failure = assertFailsWith<SnmpError> { session.set(vlans, "5", mapOf("dot1qVlanStaticName" to "lab")) }
        assertEquals(17, failure.status)
        assertTrue("notWritable for dot1qVlanStaticName.5 (1.3.6.1.2.1.17.7.1.4.3.1.1.5)" in failure.message.orEmpty(), failure.message)
    }
}
