package com.vasmarfas.networkhelm.ssh

import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SshProbeTest {

    @Test
    fun aPortThatAnswersIsOpenAndOneThatDoesNotIsClosed() = runBlocking<Unit> {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(SshReach.Closed(port), sshReach("127.0.0.1", null, port))
        ServerSocket(0).use { server ->
            assertEquals(SshReach.Open, sshReach("127.0.0.1", null, server.localPort))
            assertEquals(SshReach.Open, sshReach("127.0.0.1", SshService(server.localPort, false), 22))
        }
    }

    @Test
    fun aServiceTurnedOffIsOffWhateverThePortDoes() = runBlocking<Unit> {
        ServerSocket(0).use { server ->
            assertEquals(SshReach.Off, sshReach("127.0.0.1", SshService(server.localPort, true), server.localPort))
        }
    }

    @Test
    fun aRouterThatDoesNotListTheServiceIsTriedAtThePort() = runBlocking<Unit> {
        val closed = sshReach("127.0.0.1", null, ServerSocket(0).use { it.localPort })
        assertIs<SshReach.Closed>(closed)
    }
}
