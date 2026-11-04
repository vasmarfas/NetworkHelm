package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.ssh.connectSsh
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class UciLabTest {

    private suspend fun connect(lab: Map<String, String>) = connectSsh(
        lab.getValue("host"), 22, lab.getValue("user"), SshAuth.Password(lab.getValue("password")),
        acceptHostKey = { it.fingerprint == lab.getValue("ssh.hostKey") },
    )

    private fun eachOpenWrt(block: suspend (Map<String, String>) -> Unit) =
        Lab.forEachOpenWrt("host", "user", "password", "ssh.hostKey") { lab -> runBlocking { block(lab) } }

    private fun rdTest(rpcd: Boolean, block: suspend (SshSession, Uci) -> Unit) = eachOpenWrt { lab ->
        withTimeout(90.seconds) {
            val ssh = connect(lab)
            try {
                ssh.exec("touch /etc/config/rd_test")
                val ubus = SshUbus(ssh)
                block(ssh, if (rpcd) RpcdUci(ubus) { uciSession(ubus) } else ShellUci(ssh))
            } finally {
                ssh.exec("rm -rf /etc/config/rd_test /tmp/rd-uci /tmp/rd-apply")
                ssh.close()
            }
        }
    }

    private suspend fun SshSession.file() = exec("cat /etc/config/rd_test").output

    @Test
    fun sectionsOptionsListsAndOrderWaitInTheDraft() = rdTest(rpcd = true, ::draft)

    @Test
    fun sectionsOptionsListsAndOrderWaitInTheDraftWithoutRpcd() = rdTest(rpcd = false, ::draft)

    private suspend fun draft(ssh: SshSession, uci: Uci) {
        uci.add("rd_test", "rd_test", "main", mapOf("value" to "it's"), mapOf("item" to listOf("a", "b")))
        val anonymous = uci.add("rd_test", "rd_test")
        uci.set("rd_test", anonymous, mapOf("value" to "2"))
        uci.order("rd_test", listOf(anonymous, "main"))
        val sections = uci.sections("rd_test")
        assertEquals(listOf(anonymous, "main"), sections.map { it.name })
        assertEquals(listOf(true, false), sections.map { it.anonymous })
        assertEquals(listOf("a", "b"), sections.last().lists["item"])
        assertEquals("it's", sections.last().options["value"])
        assertEquals("2", sections.first().options["value"])
        assertTrue(uci.changes().isNotEmpty() && uci.changes().all { it.config == "rd_test" })
        assertEquals("", ssh.file().trim(), "nothing reaches the file before apply")
        uci.delete("rd_test", "main", listOf("item"))
        assertEquals(null, uci.sections("rd_test").last().lists["item"])
        uci.revert("rd_test")
        assertEquals(emptyList(), uci.changes())
    }

    @Test
    fun aMissingConfigIsNotFound() = rdTest(rpcd = true) { _, uci -> notFound(uci) }

    @Test
    fun aMissingConfigIsNotFoundWithoutRpcd() = rdTest(rpcd = false) { _, uci -> notFound(uci) }

    private suspend fun notFound(uci: Uci) {
        assertEquals(UbusStatus.NOT_FOUND, assertFailsWith<UbusException> { uci.sections("rd_test_none") }.status)
    }

    @Test
    fun applyComesBackUnlessConfirmed() = rdTest(rpcd = true, ::applyAndConfirm)

    @Test
    fun applyComesBackUnlessConfirmedWithoutRpcd() = rdTest(rpcd = false, ::applyAndConfirm)

    private suspend fun applyAndConfirm(ssh: SshSession, uci: Uci) {
        ssh.exec("printf \"config rd_test 'main'\\n\\toption value '1'\\n\" > /etc/config/rd_test")
        uci.set("rd_test", "main", mapOf("value" to "5"))
        uci.apply(rollbackSeconds = 5)
        assertTrue("option value '5'" in ssh.file())
        delay(8.seconds)
        assertTrue("option value '1'" in ssh.file(), "the router has put the file back")
        assertEquals("5", uci.changes().single().value, "and the edit into the draft")
        uci.apply(rollbackSeconds = 5)
        uci.confirm()
        delay(7.seconds)
        assertTrue("option value '5'" in ssh.file())
        assertEquals(UbusStatus.NO_DATA, assertFailsWith<UbusException> { uci.apply(rollbackSeconds = 5) }.status)
        assertEquals(UbusStatus.NO_DATA, assertFailsWith<UbusException> { uci.confirm() }.status)
    }

    @Test
    fun anApplyWaitingElsewhereTurnsDownOurs() = rdTest(rpcd = true) { ssh, uci ->
        val ubus = SshUbus(ssh)
        val other = RpcdUci(ubus) { uciSession(ubus) }
        other.add("rd_test", "rd_test", "other", mapOf("value" to "2"))
        other.apply(rollbackSeconds = 30)
        try {
            uci.add("rd_test", "rd_test", "main", mapOf("value" to "1"))
            assertEquals(UbusStatus.PERMISSION_DENIED, assertFailsWith<UbusException> { uci.apply(rollbackSeconds = 5) }.status)
            assertEquals(UbusStatus.PERMISSION_DENIED, assertFailsWith<UbusException> { uci.confirm() }.status)
        } finally {
            other.rollback()
        }
        uci.apply(rollbackSeconds = 5)
        uci.confirm()
    }

    @Test
    fun aLapsedSessionDoesNotDenyTheNextRead() = rdTest(rpcd = true) { ssh, _ ->
        val ubus = SshUbus(ssh)
        val lapsed = uciSession(ubus, timeoutSeconds = 1)
        val uci = RpcdUci(ubus) { uciSession(ubus, timeoutSeconds = 1) }
        assertTrue(uci.sections("network").isNotEmpty())
        delay(2_500)
        val get = JsonObject(mapOf("config" to JsonPrimitive("network"), "ubus_rpc_session" to JsonPrimitive(lapsed)))
        assertEquals(UbusStatus.PERMISSION_DENIED, assertFailsWith<UbusException> { ubus.call("uci", "get", get) }.status)
        assertTrue(uci.sections("network").isNotEmpty())
    }

    @Test
    fun editsOfTheConsoleHoldTheApplyWithoutRpcd() = rdTest(rpcd = false) { ssh, uci ->
        ssh.exec("printf \"config rd_test 'main'\\n\\toption value '1'\\n\" > /etc/config/rd_test; uci set rd_test.main.value=9")
        try {
            uci.set("rd_test", "main", mapOf("value" to "5"))
            val held = assertFailsWith<UbusException> { uci.apply(rollbackSeconds = 5) }
            assertEquals(UbusStatus.PERMISSION_DENIED, held.status)
            assertTrue("rd_test" in held.message.orEmpty(), held.message)
        } finally {
            ssh.exec("uci revert rd_test")
        }
    }

    @Test
    fun applyWithoutRpcdComesBackAfterTheConnectionIsGone() = eachOpenWrt { lab ->
        withTimeout(60.seconds) {
            val first = connect(lab)
            first.exec("printf \"config rd_test 'main'\\n\\toption value '1'\\n\" > /etc/config/rd_test")
            ShellUci(first).set("rd_test", "main", mapOf("value" to "5"))
            ShellUci(first).apply(rollbackSeconds = 5)
            first.close()
            delay(8.seconds)
            val second = connect(lab)
            try {
                assertTrue("option value '1'" in second.file())
                assertEquals("5", ShellUci(second).changes().single().value)
            } finally {
                second.exec("rm -rf /etc/config/rd_test /tmp/rd-uci /tmp/rd-apply")
                second.close()
            }
        }
    }

    @Test
    fun theInterfaceOfTheConnectionIsFound() = rdTest(rpcd = true) { ssh, _ ->
        assertTrue("wan" in peerInterfaces(ssh, SshUbus(ssh)))
    }
}
