package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.ssh.connectSsh
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.transport.openTlsTrusted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class UbusLabTest {

    private fun openwrt(block: suspend CoroutineScope.(SshSession, Map<String, String>) -> Unit) =
        Lab.forEachOpenWrt("host", "user", "password", "ssh.hostKey", "version") { lab ->
            runBlocking {
                withTimeout(60.seconds) {
                    val ssh = connectSsh(
                        lab.getValue("host"), 22, lab.getValue("user"), SshAuth.Password(lab.getValue("password")),
                        acceptHostKey = { it.fingerprint == lab.getValue("ssh.hostKey") },
                    )
                    try {
                        block(ssh, lab)
                    } finally {
                        ssh.close()
                    }
                }
            }
        }

    private fun http(lab: Map<String, String>) =
        HttpUbus(lab.getValue("host"), lab.getValue("user"), lab.getValue("password")) { openTcp(lab.getValue("host"), 80) }

    private suspend fun CoroutineScope.firstOf(flow: Flow<JsonObject>, trigger: suspend () -> Unit): JsonObject {
        val first = async { flow.first() }
        while (!first.isCompleted) {
            trigger()
            delay(300)
        }
        return first.await()
    }

    private fun release(board: JsonObject) = board["release"]?.jsonObject?.get("version")?.jsonPrimitive?.content.orEmpty()

    @Test
    fun callsOverOneShellAreQuick() = openwrt { ssh, lab ->
        val ubus = SshUbus(ssh)
        assertEquals(lab.getValue("version"), release(ubus.call("system", "board")))
        val started = TimeSource.Monotonic.markNow()
        repeat(20) { ubus.call("system", "info") }
        val each = started.elapsedNow() / 20
        assertTrue(each < 0.1.seconds, "a call takes $each")
        assertEquals(UbusStatus.NOT_FOUND, assertFailsWith<UbusException> { ubus.call("network.interface.rd_test_none", "status") }.status)
    }

    @Test
    fun eventsAndNotificationsArriveOverSsh() = openwrt { ssh, _ ->
        val ubus = SshUbus(ssh)
        val event = firstOf(ubus.listen("rd-test.event")) { ssh.exec("ubus send rd-test.event '{\"n\":1}'") }
        assertEquals("1", event["rd-test.event"]?.jsonObject?.get("n")?.jsonPrimitive?.content)
        val line = firstOf(ubus.subscribe("log")) { ssh.exec("logger -t rd-test 'networkhelm lab check'") }
        assertTrue(line["message"]?.jsonObject?.get("msg")?.jsonPrimitive?.content.orEmpty().isNotEmpty(), line.toString())
        val waited = TimeSource.Monotonic.markNow()
        assertNull(withTimeoutOrNull(1.seconds) { ubus.listen("rd-test.none").first() })
        assertTrue(waited.elapsedNow() < 5.seconds, "cancelled after ${waited.elapsedNow()}")
        val left = (1..20).firstNotNullOfOrNull {
            delay(100)
            ssh.exec("ps w | grep -c '[u]bus -S listen rd-test'").output.trim().takeIf { it == "0" }
        }
        assertEquals("0", left)
    }

    @Test
    fun theAclIsLaidOverSshOnceAndOutlivesAnUpgrade() = openwrt { ssh, lab ->
        installAcl(ssh)
        assertEquals("1", ssh.exec("grep -cx /usr/share/rpcd/acl.d/networkhelm.json /etc/sysupgrade.conf").output.trim())
        assertEquals(lab.getValue("version"), release(http(lab).call("system", "board")))
    }

    @Test
    fun theWebServerCallsAndSendsNotificationsWithItsOwnSession() = openwrt { ssh, lab ->
        val ubus = http(lab)
        assertEquals(lab.getValue("version"), release(ubus.call("system", "board")))
        assertEquals(UbusStatus.NOT_FOUND, assertFailsWith<UbusException> { ubus.call("network.interface.rd_test_none", "status") }.status)
        val line = firstOf(ubus.subscribe("log")) { ssh.exec("logger -t rd-test 'networkhelm lab check'") }
        assertTrue(line.containsKey("message"), line.toString())
        assertEquals(UbusStatus.NOT_SUPPORTED, assertFailsWith<UbusException> { ubus.listen("rd-test.event").first() }.status)
    }

    @Test
    fun theRouterTellsWhatItIsAndHas() = openwrt { ssh, lab ->
        val ubus = SshUbus(ssh)
        val withoutRpcd = object : Ubus by ubus {
            override suspend fun objects() = ubus.objects() - setOf("rpc-sys", "file", "luci")
        }
        for ((name, facts) in listOf("ssh" to readFacts(ubus, ssh), "http" to readFacts(http(lab), null), "commands" to readFacts(withoutRpcd, ssh))) {
            val device = facts.device
            assertEquals("OpenWrt", device.identity, name)
            assertEquals(listOf(lab.getValue("version"), "x86/64"), listOf(device.version, device.architecture), name)
            assertTrue("dropbear" in device.packages && "firewall4" in device.packages, "$name: ${device.packages}")
            assertTrue("luci-mod-network" in facts.apps, "$name: ${facts.apps}")
            assertEquals("eth0", facts.boardJson["network"]?.jsonObject?.get("lan")?.jsonObject?.get("device")?.jsonPrimitive?.content, name)
            assertTrue(device.canWrite, name)
        }
        assertEquals("true", readFacts(ubus, ssh).features["firewall4"]?.jsonPrimitive?.content)
    }

    @Test
    fun theWebServerAnswersOverTls() = openwrt { _, lab ->
        val host = lab.getValue("host")
        var asked = 0
        val trust = TrustPolicy(MemoryTrustStore()) { asked++; true }
        val ubus = HttpUbus(host, lab.getValue("user"), lab.getValue("password")) { openTlsTrusted(host, 443, trust) }
        assertEquals(lab.getValue("version"), release(ubus.call("system", "board")))
        ubus.call("system", "info")
        assertEquals(1, asked)
    }
}
