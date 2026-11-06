package com.vasmarfas.networkhelm.nettools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun readHeadLine(input: InputStream): String {
    val line = StringBuilder()
    while (true) {
        val next = input.read()
        if (next < 0 || next == '\n'.code) return line.toString().trimEnd('\r')
        line.append(next.toChar())
    }
}

private fun OutputStream.reply(status: String, body: ByteArray, vararg headers: String) {
    write(("HTTP/1.1 $status\r\nContent-Length: ${body.size}\r\nConnection: close\r\n" + headers.joinToString("") { "$it\r\n" } + "\r\n").encodeToByteArray())
    write(body)
    flush()
}

// a server on the loopback that reads the head of one request per connection and leaves the answer to the handler
private class Loopback(private val handle: (request: String, headers: Map<String, String>, input: InputStream, output: OutputStream) -> Unit) : Closeable {
    private val socket = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
    val base = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (true) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                thread(isDaemon = true) { client.use { serve(it) } }
            }
        }
    }

    override fun close() = socket.close()

    private fun serve(client: Socket) {
        val input = client.getInputStream()
        val request = readHeadLine(input)
        val headers = generateSequence { readHeadLine(input).takeIf { it.isNotEmpty() } }.associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        handle(request, headers, input, client.getOutputStream())
    }
}

class PublicHttpTest {

    private fun http() = assertNotNull(publicHttp("NetworkHelm/test"))

    @Test
    fun aGetGivesTheStatusTheHeadersAndTheBodyAndNamesItself() = runBlocking {
        val agent = CompletableDeferred<String>()
        val accept = CompletableDeferred<String>()
        Loopback { _, headers, _, output ->
            agent.complete(headers["user-agent"].orEmpty())
            accept.complete(headers["accept"].orEmpty())
            output.reply("200 OK", """{"ok":true}""".encodeToByteArray(), "X-Test: yes")
        }.use { server ->
            val answer = http().get("${server.base}/x", mapOf("Accept" to "application/rdap+json"))
            assertEquals(200, answer.status)
            assertEquals("""{"ok":true}""", answer.body.decodeToString())
            assertEquals("yes", answer.headers["x-test"])
            assertEquals("NetworkHelm/test", withTimeout(10_000) { agent.await() })
            assertEquals("application/rdap+json", withTimeout(10_000) { accept.await() })
        }
    }

    @Test
    fun anErrorStatusIsAnAnswerWithItsBodyAndARedirectIsNotFollowed() = runBlocking {
        Loopback { request, _, _, output ->
            if (request.startsWith("GET /gone")) output.reply("404 Not Found", "nope".encodeToByteArray()) else output.reply("302 Found", ByteArray(0), "Location: https://example.test/elsewhere")
        }.use { server ->
            val gone = http().get("${server.base}/gone")
            assertEquals(404, gone.status)
            assertEquals("nope", gone.body.decodeToString())
            val moved = http().get("${server.base}/moved")
            assertEquals(302, moved.status)
            assertEquals("https://example.test/elsewhere", moved.headers["location"])
        }
    }

    @Test
    fun aBodyOverTheLimitIsAnException() = runBlocking {
        Loopback { _, _, _, output -> output.reply("200 OK", ByteArray(100_000)) }.use { server ->
            assertFailsWith<IOException> { http().get("${server.base}/big", limit = 10_000) }
            assertEquals(100_000, http().get("${server.base}/big", limit = 200_000).body.size)
        }
    }

    @Test
    fun aDownloadReportsEveryByteAndAnErrorStatusReportsNone() = runBlocking {
        Loopback { request, _, _, output ->
            if (request.startsWith("GET /ok")) output.reply("200 OK", ByteArray(300_000)) else output.reply("403 Forbidden", ByteArray(1_000))
        }.use { server ->
            var received = 0
            assertEquals(200, http().download("${server.base}/ok") { received += it })
            assertEquals(300_000, received)
            received = 0
            assertEquals(403, http().download("${server.base}/denied") { received += it })
            assertEquals(0, received)
        }
    }

    @Test
    fun anUploadSendsTheBytesAsAFixedLengthBodyAndReportsThemAsTheyLeave() = runBlocking {
        val got = CompletableDeferred<Int>()
        val head = CompletableDeferred<String>()
        Loopback { request, headers, input, output ->
            head.complete("$request ${headers["content-length"]}")
            val total = headers.getValue("content-length").toInt()
            var left = total
            val buffer = ByteArray(16 * 1024)
            while (left > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, left))
                if (count < 0) break
                left -= count
            }
            got.complete(total - left)
            output.reply("204 No Content", ByteArray(0))
        }.use { server ->
            var sent = 0
            assertEquals(204, http().upload("${server.base}/up", 200_000) { sent += it })
            assertEquals(200_000, sent)
            assertEquals(200_000, withTimeout(10_000) { got.await() })
            assertEquals("POST /up HTTP/1.1 200000", withTimeout(10_000) { head.await() })
        }
    }

    @Test
    fun cancellingADownloadThatStillFlowsClosesTheConnectionAtOnce() = runBlocking {
        val closed = CompletableDeferred<Boolean>()
        Loopback { _, _, _, output ->
            output.write("HTTP/1.1 200 OK\r\nContent-Length: 1000000000\r\n\r\n".encodeToByteArray())
            runCatching {
                while (true) {
                    output.write(ByteArray(16 * 1024))
                    output.flush()
                }
            }
            closed.complete(true)
        }.use { server ->
            val started = CompletableDeferred<Unit>()
            val download = launch(Dispatchers.IO) { http().download("${server.base}/flow") { started.complete(Unit) } }
            withTimeout(10_000) { started.await() }
            download.cancel()
            assertTrue(withTimeout(5_000) { closed.await() }, "the server still sees the connection open")
            withTimeout(5_000) { download.join() }
        }
    }

    @Test
    fun cancellingAStalledDownloadReleasesTheCallerAtOnce() = runBlocking {
        Loopback { _, _, input, output ->
            output.write("HTTP/1.1 200 OK\r\nContent-Length: 100000000\r\n\r\n".encodeToByteArray())
            output.write(ByteArray(1_000))
            output.flush()
            runCatching { while (input.read() >= 0) { } }
        }.use { server ->
            val started = CompletableDeferred<Unit>()
            val download = launch(Dispatchers.IO) { http().download("${server.base}/stall", timeoutMillis = 3_000) { started.complete(Unit) } }
            withTimeout(10_000) { started.await() }
            download.cancel()
            withTimeout(1_000) { download.join() }
        }
    }

    @Test
    fun aFinishedRequestLeavesItsConnectionForTheNextOne() = runBlocking {
        val accepted = AtomicInteger()
        ServerSocket(0, 10, InetAddress.getLoopbackAddress()).use { server ->
            thread(isDaemon = true) {
                while (true) {
                    val client = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                    accepted.incrementAndGet()
                    thread(isDaemon = true) {
                        client.use {
                            val input = it.getInputStream()
                            while (readHeadLine(input).isNotEmpty()) {
                                while (readHeadLine(input).isNotEmpty()) { }
                                it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok".encodeToByteArray())
                            }
                        }
                    }
                }
            }
            repeat(3) { assertEquals("ok", http().get("http://127.0.0.1:${server.localPort}/x").body.decodeToString()) }
            assertEquals(1, accepted.get(), "one connection for three requests")
        }
    }
}
