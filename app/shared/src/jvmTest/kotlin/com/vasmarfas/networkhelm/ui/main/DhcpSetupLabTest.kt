package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsProtocol
import com.vasmarfas.networkhelm.routeros.connectRouterOs
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DhcpSetupLabTest {

    private val bridges = MenuPath.parse("interface/bridge")
    private val addresses = MenuPath.parse("ip/address")
    private val pools = MenuPath.parse("ip/pool")
    private val servers = MenuPath.parse("ip/dhcp-server")
    private val networks = MenuPath.parse("ip/dhcp-server/network")

    private fun lab(): Properties {
        val file = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("lab.properties is missing", file != null)
        return Properties().apply { file?.inputStream()?.use(::load) }
    }

    private suspend fun DeviceSession.rows(path: MenuPath, filter: Filter) = watch(path, filter).filterIsInstance<ListEvent.Snapshot>().first().items

    @Test
    fun theSetupWritesAPoolANetworkAndAServer() = runBlocking<Unit> {
        val lab = lab()
        assumeTrue("the lab file does not let the tests change chr", lab.getProperty("chr.disposable").orEmpty().isNotBlank())
        val scope = CoroutineScope(Dispatchers.Default)
        val login = RouterOsLogin(lab.getProperty("chr.host"), lab.getProperty("chr.user"), lab.getProperty("chr.password"), RouterOsProtocol.Api, 8728)
        val session = connectRouterOs(login, TrustPolicy(MemoryTrustStore()) { true }, scope)
        val plan = assertNotNull(dhcpPlan("10.199.0.1/24"))
        try {
            session.add(bridges, mapOf("name" to "rd-test-dhcp", "comment" to "rd-test"))
            session.add(addresses, mapOf("address" to "10.199.0.1/24", "interface" to "rd-test-dhcp", "comment" to "rd-test"))
            val (refusedPool, refusedServer) = dhcpNames(session)
            assertFailsWith<DeviceException> { setUpDhcp(session, dhcpWrites("rd-test-dhcp", plan, "rd-test-not-a-time", refusedPool, refusedServer)) }
            assertEquals(0, session.count(pools, Filter.Equals("name", refusedPool)))
            assertEquals(0, session.count(networks, Filter.Equals("address", plan.network)))
            val (pool, server) = dhcpNames(session)
            setUpDhcp(session, dhcpWrites("rd-test-dhcp", plan, "30m", pool, server))
            assertEquals("10.199.0.2-10.199.0.254", session.rows(pools, Filter.Equals("name", pool)).single()["ranges"])
            val network = session.rows(networks, Filter.Equals("address", plan.network)).single()
            assertEquals(listOf("10.199.0.1", "10.199.0.1"), listOf(network["gateway"], network["dns-server"]))
            val dhcp = session.rows(servers, Filter.Equals("name", server)).single()
            assertEquals(listOf("rd-test-dhcp", pool, "30m"), listOf(dhcp["interface"], dhcp["address-pool"], dhcp["lease-time"]))
        } finally {
            session.rows(servers, Filter.Equals("interface", "rd-test-dhcp")).map { it.id }.takeIf { it.isNotEmpty() }?.let { session.remove(servers, it) }
            session.rows(networks, Filter.Equals("address", plan.network)).map { it.id }.takeIf { it.isNotEmpty() }?.let { session.remove(networks, it) }
            session.rows(pools, Filter.Equals("ranges", plan.pool)).map { it.id }.takeIf { it.isNotEmpty() }?.let { session.remove(pools, it) }
            session.rows(addresses, Filter.Equals("interface", "rd-test-dhcp")).map { it.id }.takeIf { it.isNotEmpty() }?.let { session.remove(addresses, it) }
            session.rows(bridges, Filter.Equals("name", "rd-test-dhcp")).map { it.id }.takeIf { it.isNotEmpty() }?.let { session.remove(bridges, it) }
            session.close()
            scope.cancel()
        }
    }
}
