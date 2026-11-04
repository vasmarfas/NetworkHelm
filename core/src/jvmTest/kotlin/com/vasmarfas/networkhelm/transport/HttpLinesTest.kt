package com.vasmarfas.networkhelm.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpLinesTest {

    @Test
    fun aStoppedCollectorClosesTheConnectionAtOnce() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val closed = CompletableDeferred<Boolean>()
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\ndata: 1\n".encodeToByteArray())
                    socket.soTimeout = 10_000
                    closed.complete(
                        try {
                            socket.getInputStream().readBytes()
                            true
                        } catch (e: SocketTimeoutException) {
                            false
                        },
                    )
                }
            }
            val first = CompletableDeferred<String>()
            val reading = launch(Dispatchers.IO) {
                httpLines({ openTcp(server.inetAddress.hostAddress, server.localPort, 1_000) }, "localhost", "/", emptyMap()).collect { first.complete(it) }
            }
            assertEquals("data: 1", first.await())
            reading.cancel()
            assertTrue(closed.await(), "the connection stayed open")
        }
    }
}
