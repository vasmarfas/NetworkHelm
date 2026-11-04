package com.vasmarfas.networkhelm.ssh

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsProtocol
import com.vasmarfas.networkhelm.routeros.connectRouterOs
import com.vasmarfas.networkhelm.session.TextStore
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustKind
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.TrustRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class JumpLabTest {

    @Test
    fun theChrAnswersThroughTwoHopsAndIsTrustedByItsOwnAddress() = runBlocking<Unit> {
        val lab = Lab.require(
            "openwrt.host", "openwrt.user", "openwrt.password", "openwrt.ssh.hostKey",
            "openwrt24.host", "openwrt24.user", "openwrt24.password", "openwrt24.ssh.hostKey",
            "chr.host", "chr.user", "chr.password",
        )
        val second = Lab.optional("openwrt24.hopHost") ?: lab.getValue("openwrt24.host")
        val store = MemoryTrustStore()
        store.put(lab.getValue("openwrt.host"), 22, TrustKind.Ssh, lab.getValue("openwrt.ssh.hostKey"))
        store.put(second, 22, TrustKind.Ssh, lab.getValue("openwrt24.ssh.hostKey"))
        val asked = mutableListOf<TrustRequest>()
        val trust = TrustPolicy(store) { request -> asked += request; request.kind == TrustKind.Tls }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hops = listOf("openwrt" to lab.getValue("openwrt.host"), "openwrt24" to second).map { (prefix, host) ->
            Hop(host, 22, lab.getValue("$prefix.user"), SshAuth.Password(lab.getValue("$prefix.password")))
        }
        val jump = Jump.open(hops, trust, scope)
        try {
            withTimeout(60.seconds) {
                val chr = Lab.optional("chr.hopHost") ?: lab.getValue("chr.host")
                val port = jump.tunnel(chr, RouterOsProtocol.ApiSsl.defaultPort)
                val login = RouterOsLogin(Jump.LOOPBACK, lab.getValue("chr.user"), lab.getValue("chr.password"), RouterOsProtocol.ApiSsl, port, webPort = jump.tunnel(chr, 80))
                val session = connectRouterOs(login, trust.through(jump.leads), scope)
                try {
                    assertTrue(session.device.identity.isNotEmpty() && session.device.version.isNotEmpty())
                    val kept = object : TextStore {
                        override suspend fun read(name: String): String? = null

                        override suspend fun write(name: String, text: String) = Unit
                    }
                    assertTrue(session.packs(kept).isNotEmpty())
                } finally {
                    session.close()
                }
                assertEquals(listOf(chr to RouterOsProtocol.ApiSsl.defaultPort), asked.map { it.host to it.port })
                assertNotNull(store.get(chr, RouterOsProtocol.ApiSsl.defaultPort, TrustKind.Tls))
            }
        } finally {
            jump.close()
            scope.cancel()
        }
    }
}
