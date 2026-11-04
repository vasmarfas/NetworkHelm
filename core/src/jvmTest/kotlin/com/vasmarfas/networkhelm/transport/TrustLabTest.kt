package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.connectSshTrusted
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TrustLabTest {

    @Test
    fun tlsCertificateIsConfirmedOnceAndAChangeIsReported() = runBlocking {
        val host = Lab.require("chr.host").getValue("chr.host")
        val store = MemoryTrustStore()
        val asked = mutableListOf<TrustRequest>()
        val policy = TrustPolicy(store) { asked += it; true }
        openTlsTrusted(host, 8729, policy).close()
        openTlsTrusted(host, 8729, policy).close()
        assertEquals(1, asked.size)
        assertNull(asked.single().previous)
        val real = asked.single().fingerprint

        store.put(host, 8729, TrustKind.Tls, "00".repeat(32))
        val declining = TrustPolicy(store) { asked += it; false }
        val error = assertFailsWith<DeviceException> { openTlsTrusted(host, 8729, declining) }
        assertEquals(ErrorCategory.PermissionDenied, error.category)
        assertEquals("00".repeat(32), asked.last().previous)
        assertEquals(real, asked.last().fingerprint)
    }

    @Test
    fun sshHostKeyIsConfirmedBeforeLogin() = runBlocking {
        val lab = Lab.require("chr.host", "chr.user", "chr.password", "chr.ssh.hostKey")
        val asked = mutableListOf<TrustRequest>()
        val policy = TrustPolicy(MemoryTrustStore()) { asked += it; true }
        val auth = SshAuth.Password(lab.getValue("chr.password"))
        connectSshTrusted(lab.getValue("chr.host"), 22, lab.getValue("chr.user"), auth, policy).close()
        connectSshTrusted(lab.getValue("chr.host"), 22, lab.getValue("chr.user"), auth, policy).close()
        assertEquals(listOf(lab.getValue("chr.ssh.hostKey")), asked.map { it.fingerprint })
    }
}
