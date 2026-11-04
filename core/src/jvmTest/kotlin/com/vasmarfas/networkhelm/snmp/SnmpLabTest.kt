package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SnmpLabTest {

    private fun snmp(block: suspend (SnmpSession, String) -> Unit) = runBlocking {
        val lab = Lab.require("openwrtwifi.host", "openwrtwifi.snmp.community")
        val host = lab.getValue("openwrtwifi.host")
        val session = connectSnmp(SnmpLogin(host, lab.getValue("openwrtwifi.snmp.community")))
        try {
            withTimeout(60.seconds) { block(session, host) }
        } finally {
            session.close()
        }
    }

    private suspend fun SnmpSession.rows(path: String) = (watch(MenuPath.parse(path)).first() as ListEvent.Snapshot).items

    @Test
    fun usersOfSnmpV3ReadTheAgent() = runBlocking<Unit> {
        val prefixes = listOf("snmp3.rd-test-sha-aes", "snmp3.rd-test-md5-des")
        val lab = Lab.require("snmp3.host", *prefixes.flatMap { listOf("$it.auth", "$it.priv") }.toTypedArray())
        val host = lab.getValue("snmp3.host")
        suspend fun name(user: SnmpUser): String {
            val session = connectSnmp(SnmpLogin(host, "", Lab.optional("snmp3.port")?.toInt() ?: 161, user))
            return try {
                assertTrue(session.capabilities.encrypted)
                session.device.identity
            } finally {
                session.close()
            }
        }
        val (sha, md5) = prefixes.zip(listOf(AuthProtocol.Sha1 to PrivProtocol.Aes, AuthProtocol.Md5 to PrivProtocol.Des)) { prefix, (auth, priv) ->
            SnmpUser(prefix.removePrefix("snmp3."), auth, lab.getValue("$prefix.auth"), priv, lab.getValue("$prefix.priv"))
        }
        withTimeout(60.seconds) {
            val named = name(sha)
            assertTrue(named.isNotEmpty() && named != host, named)
            assertEquals(named, name(md5))
            val refused = assertFailsWith<DeviceException> { name(SnmpUser(sha.name, sha.auth, sha.authPassword.reversed(), sha.priv, sha.privPassword)) }
            assertEquals(ErrorCategory.PermissionDenied, refused.category, refused.message)
        }
    }

    @Test
    fun theRouterTellsWhatItIsWithItsResourcesAndClock() = snmp { session, _ ->
        assertEquals("rd-test-wifi", session.device.identity)
        assertTrue(session.device.board.startsWith("Linux OpenWrt"), session.device.board)
        val resource = MenuPath.parse("system/resource")
        val first = session.get(resource, null)
        assertTrue(first?.get("uptime").orEmpty().matches(Regex("(\\d+[wdhms])+")), first?.get("uptime"))
        assertTrue((first?.get("total-memory")?.toLongOrNull() ?: 0) > 100_000_000, first?.get("total-memory"))
        assertTrue((first?.get("free-memory")?.toLongOrNull() ?: 0) > 0)
        delay(2.seconds)
        val load = session.get(resource, null)?.get("cpu-load")?.toIntOrNull()
        assertTrue(load != null && load in 0..100, "$load")
        assertTrue(session.get(MenuPath.parse("system/clock"), null)?.get("date").orEmpty().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun interfacesAddressesRoutesArpAndStorageOfTheRouter() = snmp { session, host ->
        val eth0 = session.rows("interface").single { it["name"] == "eth0" }
        assertEquals(listOf("ether", "true", "1Gbps"), listOf(eth0["type"], eth0["running"], eth0["speed"]))
        assertTrue(eth0["mac-address"].orEmpty().matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")), eth0["mac-address"])
        val subnet = host.substringBeforeLast('.')
        assertTrue(session.rows("ip/address").any { it["address"] == "$host/24" && it["interface"] == "eth0" && it["network"] == "$subnet.0" })
        assertTrue(session.rows("ipv6/address").any { it["address"].orEmpty().startsWith("fe80::") && it["interface"] == "eth0" })
        val default = session.rows("ip/route").single { it["dst-address"] == "0.0.0.0/0" }
        val gateway = default["gateway"].orEmpty()
        assertTrue(gateway.startsWith("$subnet.") && default["interface"] == "eth0", default.values.toString())
        assertTrue(session.rows("ip/route").any { it["dst-address"] == "$subnet.0/24" && it["connect"] == "true" })
        assertTrue(session.rows("ip/arp").any { it["address"] == gateway && it["mac-address"].orEmpty().isNotEmpty() && it["interface"] == "eth0" })
        assertTrue(session.rows("system/storage").any { it["description"] == "/overlay" && (it["size"]?.toLongOrNull() ?: 0) > 0 })
        assertFailsWith<DeviceException> { session.describe(MenuPath.parse("ip/neighbor")) }
    }

    @Test
    fun monitorTrafficCountsWhatComesInOnEth0() = snmp { session, _ ->
        val rounds = session.run(MenuPath.parse("interface"), "monitor-traffic", mapOf("interface" to "eth0")).take(2).toList()
        assertTrue(rounds.all { it["name"] == "eth0" })
        assertTrue((rounds.last()["rx-bits-per-second"]?.toLongOrNull() ?: 0) > 0, rounds.toString())
    }
}
