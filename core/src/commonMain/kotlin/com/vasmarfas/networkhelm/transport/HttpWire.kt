package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory

private val methods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

// the browser sets these itself in a request, and the rebuilt answer frames them anew
internal val withheld = setOf("host", "connection", "content-length", "transfer-encoding", "content-encoding", "keep-alive", "cookie")

internal class HttpRequest(val method: String, val target: String, val headers: Map<String, String>, val body: String, val size: Int)

internal fun notHttp() = DeviceException(ErrorCategory.Unsupported, "a page in a browser can send HTTP requests only")

internal fun readRequest(bytes: ByteArray): HttpRequest? {
    val first = bytes.indexOfBreak(0)
    if (first == null) {
        if (bytes.isNotEmpty() && bytes[0].toInt().toChar() !in 'A'..'Z') throw notHttp()
        return null
    }
    val (method, target) = bytes.decodeToString(0, first).split(' ').takeIf { it.size >= 3 && it[0] in methods } ?: throw notHttp()
    val end = bytes.indexOfBreak(first, twice = true) ?: return null
    val fields = if (end > first) bytes.decodeToString(first + 2, end).split("\r\n") else emptyList()
    val headers = fields.associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
    val length = headers.entries.firstOrNull { it.key.equals("content-length", ignoreCase = true) }?.value?.toIntOrNull() ?: 0
    val size = end + 4 + length
    if (bytes.size < size) return null
    return HttpRequest(method, target, headers, bytes.decodeToString(end + 4, size), size)
}

internal fun responseHead(status: Int, fields: String): String {
    val kept = fields.split("\r\n").filter { it.isNotEmpty() && it.substringBefore(':').lowercase() !in withheld }
    return "HTTP/1.1 $status\r\n" + kept.joinToString("") { "$it\r\n" }
}

private const val CR = '\r'.code.toByte()
private const val LF = '\n'.code.toByte()

private fun ByteArray.indexOfBreak(from: Int, twice: Boolean = false): Int? {
    val width = if (twice) 4 else 2
    return (from..size - width).firstOrNull { i -> this[i] == CR && this[i + 1] == LF && (!twice || this[i + 2] == CR && this[i + 3] == LF) }
}
