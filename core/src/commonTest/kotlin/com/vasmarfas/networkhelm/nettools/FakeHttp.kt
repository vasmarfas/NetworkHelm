package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.HttpResponse

fun cannedReply(status: Int = 200, body: String = "", vararg headers: Pair<String, String>) = HttpResponse(status, body.encodeToByteArray(), mapOf(*headers))

// answers a GET by its address; whatever the lambda throws is a connection that failed
class FakeHttp(private val answer: suspend (String) -> HttpResponse) : PublicHttp {
    val asked = mutableListOf<String>()

    override suspend fun get(url: String, headers: Map<String, String>, timeoutMillis: Int, limit: Int): HttpResponse {
        asked += url
        return answer(url)
    }

    override suspend fun download(url: String, timeoutMillis: Int, onBytes: (Int) -> Unit): Int = error("no download here")

    override suspend fun upload(url: String, size: Int, timeoutMillis: Int, onBytes: (Int) -> Unit): Int = error("no upload here")
}
