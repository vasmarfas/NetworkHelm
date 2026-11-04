package com.vasmarfas.networkhelm.routeros.rest

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.routeros.api.ApiTrapException
import com.vasmarfas.networkhelm.transport.CannedStream
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RestClientTest {

    private val streams = ArrayDeque<CannedStream>()
    private val client = RestClient("192.0.2.1", "admin", "secret") { streams.removeFirst() }

    private fun answer(status: Int, json: String) =
        CannedStream("HTTP/1.1 $status X\r\nContent-Length: ${json.encodeToByteArray().size}\r\n\r\n$json").also { streams += it }

    @Test
    fun wordsBecomeAPostWithJsonArguments() = runTest {
        val stream = answer(200, """[{".id":"*1","address":"192.0.2.1"}]""")
        val result = client.call(listOf("/ip/firewall/address-list/print", "=.proplist=.id,address", "?list=blocked", "?#!"))
        assertEquals(listOf(mapOf(".id" to "*1", "address" to "192.0.2.1")), result.rows)
        val request = stream.written.toString()
        assertTrue(request.startsWith("POST /rest/ip/firewall/address-list/print HTTP/1.1\r\n"), request)
        assertTrue("Authorization: Basic YWRtaW46c2VjcmV0\r\n" in request, request)
        assertEquals(
            Json.parseToJsonElement("""{".query":["list=blocked","#!"],".proplist":[".id","address"]}"""),
            Json.parseToJsonElement(request.substringAfter("\r\n\r\n")),
        )
    }

    @Test
    fun objectAnswerIsWhatDoneCarries() = runTest {
        answer(200, """{"ret":"*38"}""")
        assertEquals(mapOf("ret" to "*38"), client.call(listOf("/ip/address/add", "=address=192.0.2.1/32", "=interface=lo")).done)
    }

    @Test
    fun routerErrorsKeepTheRouterText() = runTest {
        answer(400, """{"detail":"failure: already have such entry","error":400,"message":"Bad Request"}""")
        answer(401, """{"error":401,"message":"Unauthorized"}""")
        assertEquals("failure: already have such entry", assertFailsWith<ApiTrapException> { client.call(listOf("/ip/address/add")) }.message)
        assertEquals(ErrorCategory.PermissionDenied, assertFailsWith<DeviceException> { client.call(listOf("/system/identity/print")) }.category)
    }

    @Test
    fun aCommandThatNeverEndsIsAskedARoundAtATime() = runTest {
        val first = answer(200, """[{"name":"total","usage":"0"}]""")
        answer(200, """[{"name":"total","usage":"1"}]""")
        val replies = client.command(listOf("/tool/profile")).take(2).toList()
        assertEquals(listOf("0", "1"), replies.map { it.attributes[".section"] })
        assertEquals(Json.parseToJsonElement("""{"duration":"3"}"""), Json.parseToJsonElement(first.written.toString().substringAfter("\r\n\r\n")))
    }

    @Test
    fun listenIsRefusedSoTheDriverPolls() = runTest {
        assertFailsWith<ApiTrapException> { client.command(listOf("/ip/address/listen")).toList() }
        assertTrue(streams.isEmpty())
    }
}
