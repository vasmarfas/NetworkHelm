package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.HttpResponse

class HttpStatusException(val host: String, val status: Int) : Exception("HTTP $status from $host")

// HTTPS services of the internet; the certificate is checked against the system store, unlike openTls, which takes any certificate for the sake of routers
interface PublicHttp {
    // one request without redirects: any status is an answer, a body over the limit is an exception
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMillis: Int = 10_000, limit: Int = 4 * 1024 * 1024): HttpResponse

    // reads the body to its end and reports every piece; an answer that is not 2xx returns its status and reports nothing
    suspend fun download(url: String, timeoutMillis: Int = 15_000, onBytes: (Int) -> Unit): Int

    // POSTs size bytes of noise and reports the pieces as they leave; returns the status of the answer
    suspend fun upload(url: String, size: Int, timeoutMillis: Int = 30_000, onBytes: (Int) -> Unit): Int
}

// the desktop and Android; iOS and the browser have none for now
expect fun publicHttp(userAgent: String): PublicHttp?

fun hostOf(url: String): String {
    val authority = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
    return if (authority.startsWith("[")) authority.substringBefore(']').trimStart('[') else authority.substringBefore(':')
}
