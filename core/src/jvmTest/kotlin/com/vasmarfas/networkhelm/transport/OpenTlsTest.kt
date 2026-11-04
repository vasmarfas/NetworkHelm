package com.vasmarfas.networkhelm.transport

import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class OpenTlsTest {

    @Test
    fun aHandshakeWithoutAnAnswerFailsInItsTime() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            thread(isDaemon = true) { server.accept().use { Thread.sleep(10_000) } }
            val started = TimeSource.Monotonic.markNow()
            assertFails { openTls(server.inetAddress.hostAddress, server.localPort, null, 500) }
            assertTrue(started.elapsedNow() < 5.seconds, started.elapsedNow().toString())
        }
    }
}
