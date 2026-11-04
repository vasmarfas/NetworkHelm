package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.ssh.ExecResult
import com.vasmarfas.networkhelm.ssh.SftpClient
import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.TcpListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TunnelOpeningTest {
    private val ssh = object : SshSession {
        override suspend fun exec(command: String): ExecResult = error("not used")
        override suspend fun process(command: String): ByteStream = error("not used")
        override suspend fun shell(term: String, columns: Int, rows: Int): ShellChannel = error("not used")
        override suspend fun sftp(): SftpClient = error("not used")
        override suspend fun forward(host: String, port: Int): ByteStream = error("not used")
        override fun close() = Unit
    }

    @Test
    fun closingWhileTheListenerOpensClosesItsLateResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        var closed = false
        var accepted = false
        val listener = object : TcpListener {
            override val port = 8080
            override suspend fun accept(): ByteStream { accepted = true; error("not used") }
            override fun close() { closed = true }
        }
        val tunnels = Tunnels(this) { _, _ -> gate.await(); listener }
        tunnels.open(ssh, 0, "192.0.2.1", 80)
        runCurrent()
        val tunnel = tunnels.all.single()
        tunnels.close(tunnel)
        assertTrue(tunnel.opening?.isCancelled == true)
        gate.complete(Unit)
        runCurrent()
        assertTrue(closed)
        assertFalse(accepted)
        assertTrue(tunnels.all.isEmpty())
    }
}
