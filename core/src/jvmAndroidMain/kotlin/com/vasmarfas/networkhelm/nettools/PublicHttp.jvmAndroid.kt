package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.HttpResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

private const val BLOCK_BYTES = 64 * 1024
private const val ANSWER_BYTES = 1024 * 1024

private val noise = Random(1).nextBytes(BLOCK_BYTES)
private val identity = mapOf("Accept-Encoding" to "identity")

private class UrlHttp(private val userAgent: String) : PublicHttp {

    override suspend fun get(url: String, headers: Map<String, String>, timeoutMillis: Int, limit: Int): HttpResponse {
        val connection = connect(url, headers, timeoutMillis)
        return connection.guarded {
            val status = connection.responseCode
            val fields = connection.headerFields.entries.mapNotNull { (name, values) -> name?.let { it.lowercase() to values.last() } }.toMap()
            val body = (if (status >= 400) connection.errorStream else connection.inputStream)?.use { it.readUpTo(limit) } ?: ByteArray(0)
            HttpResponse(status, body, fields)
        }
    }

    override suspend fun download(url: String, timeoutMillis: Int, onBytes: (Int) -> Unit): Int {
        val connection = connect(url, identity, timeoutMillis)
        return connection.guarded {
            val status = connection.responseCode
            if (status in 200..299) {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BLOCK_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        onBytes(read)
                    }
                }
            } else {
                connection.disconnect()
            }
            status
        }
    }

    override suspend fun upload(url: String, size: Int, timeoutMillis: Int, onBytes: (Int) -> Unit): Int {
        val connection = connect(url, identity + ("Content-Type" to "application/octet-stream"), timeoutMillis)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(size)
        return connection.guarded {
            connection.outputStream.use { output ->
                var left = size
                while (left > 0) {
                    val count = minOf(left, noise.size)
                    output.write(noise, 0, count)
                    onBytes(count)
                    left -= count
                }
            }
            val status = connection.responseCode
            (if (status >= 400) connection.errorStream else connection.inputStream)?.use { it.readUpTo(ANSWER_BYTES) }
            status
        }
    }

    private fun connect(url: String, headers: Map<String, String>, timeoutMillis: Int): HttpURLConnection {
        val connection = URI(url).toURL().openConnection() as? HttpURLConnection ?: throw IOException("$url is not an HTTP address")
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", userAgent)
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        return connection
    }
}

// a blocking read does not see the cancellation, so the caller is released at once and the thread that reads is left to end by itself: disconnect() wakes it
// when data still flows, a stalled read ends with its timeout (disconnect() waits behind the lock of the stream, so it is called from a thread of its own);
// a connection that finished is left to the JDK for reuse, a new handshake would otherwise count into every latency sample
private suspend fun <T> HttpURLConnection.guarded(block: () -> T): T = suspendCancellableCoroutine { continuation ->
    thread(isDaemon = true) {
        try {
            continuation.resume(block())
        } catch (e: Exception) {
            disconnect()
            continuation.resumeWithException(e)
        }
    }
    continuation.invokeOnCancellation { thread(isDaemon = true) { disconnect() } }
}

private fun InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(BLOCK_BYTES)
    while (true) {
        val read = read(chunk)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > limit) throw IOException("the answer is longer than $limit bytes")
        out.write(chunk, 0, read)
    }
}

actual fun publicHttp(userAgent: String): PublicHttp? = UrlHttp(userAgent)
