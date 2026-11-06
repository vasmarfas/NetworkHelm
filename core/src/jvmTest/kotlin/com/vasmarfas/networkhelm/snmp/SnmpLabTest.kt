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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // net-snmp on a dummy interface with a community that writes and one that does not: .notes/lab/snmp-rw/ holds the scripts that start it
    @Test
    fun aWriteChangesAnInterfaceAndTheSystemOfARealAgentAndAReadingCommunityIsRefused() = runBlocking<Unit> {
        val lab = Lab.require("snmprw.host", "snmprw.community", "snmprw.readCommunity", "snmprw.index")
        val host = lab.getValue("snmprw.host")
        val port = Lab.optional("snmprw.port")?.toInt() ?: 161
        val index = lab.getValue("snmprw.index")
        val interfaces = MenuPath.parse("interface")
        val system = MenuPath.parse("system/resource")
        withTimeout(60.seconds) {
            val writer = connectSnmp(SnmpLogin(host, lab.getValue("snmprw.community"), port))
            try {
                writer.set(interfaces, index, mapOf("disabled" to "true", "comment" to "rd-test"))
                val changed = assertNotNull(writer.get(interfaces, index))
                assertEquals(listOf("true", "rd-test"), listOf(changed["disabled"], changed["comment"]))
                writer.set(system, null, mapOf("contact" to "rd-test contact", "location" to "rd-test place"))
                val resource = assertNotNull(writer.get(system, null))
                assertEquals(listOf("rd-test contact", "rd-test place"), listOf(resource["contact"], resource["location"]))
            } finally {
                writer.set(interfaces, index, mapOf("disabled" to "false", "comment" to null))
                writer.set(system, null, mapOf("contact" to null, "location" to null))
                writer.close()
            }
            val reader = connectSnmp(SnmpLogin(host, lab.getValue("snmprw.readCommunity"), port))
            try {
                val refused = assertFailsWith<DeviceException> { reader.set(interfaces, index, mapOf("comment" to "rd-test")) }
                assertTrue("noAccess" in refused.message.orEmpty() || refused.category == ErrorCategory.ConnectionLost, refused.message)
            } finally {
                reader.close()
            }
        }
    }

    // a volatile row of SNMP-TARGET-MIB, which net-snmp creates with RowStatus and does not keep past a restart
    @Test
    fun theWalkOfARealAgentOffersItsTablesAndARowIsCreatedAndDestroyed() = runBlocking<Unit> {
        val lab = Lab.require("snmprw.host", "snmprw.community")
        val port = Lab.optional("snmprw.port")?.toInt() ?: 161
        withTimeout(90.seconds) {
            val session = connectSnmp(SnmpLogin(lab.getValue("snmprw.host"), lab.getValue("snmprw.community"), port))
            try {
                val tabs = session.packs(null).flatMap { pack -> pack.window.tabs.map { it.path } }
                assertTrue("mib/UCD-SNMP-MIB/laTable" in tabs, "$tabs")
                val loads = session.rows("mib/UCD-SNMP-MIB/laTable")
                assertTrue(loads.size == 3 && loads.all { it["laLoad"]?.toDoubleOrNull() != null }, "$loads")
                val targets = MenuPath.parse("mib/SNMP-TARGET-MIB/snmpTargetAddrTable")
                assertTrue("$targets" in tabs, "$tabs")
                val id = session.add(targets, mapOf(
                    "index" to "rd-test",
                    "snmpTargetAddrTDomain" to "1.3.6.1.6.1.1",
                    "snmpTargetAddrTAddress" to "7F:00:00:01:00:A2",
                    "snmpTargetAddrParams" to "rd-test",
                    "snmpTargetAddrStorageType" to "volatile",
                ))
                try {
                    val row = assertNotNull(session.get(targets, id))
                    assertEquals(listOf("rd-test", "active", "7F:00:00:01:00:A2"), listOf(row["index"], row["snmpTargetAddrRowStatus"], row["snmpTargetAddrTAddress"]))
                } finally {
                    session.remove(targets, listOf(id))
                }
                assertNull(session.get(targets, id))
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun monitorTrafficCountsWhatComesInOnEth0() = snmp { session, _ ->
        val rounds = session.run(MenuPath.parse("interface"), "monitor-traffic", mapOf("interface" to "eth0")).take(2).toList()
        assertTrue(rounds.all { it["name"] == "eth0" })
        assertTrue((rounds.last()["rx-bits-per-second"]?.toLongOrNull() ?: 0) > 0, rounds.toString())
    }
}
