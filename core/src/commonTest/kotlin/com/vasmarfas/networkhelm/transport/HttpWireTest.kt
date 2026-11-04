package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpWireTest {

    private val post = "POST /rest/system/resource/print HTTP/1.1\r\nHost: 192.0.2.1\r\nConnection: close\r\nContent-Length: 16\r\nAuthorization: Basic YTpi\r\nContent-Type: application/json\r\n\r\n{\".proplist\":[]}"

    @Test
    fun aRequestIsReadOnceAllOfItHasComeAndItsBodyWithIt() {
        val bytes = post.encodeToByteArray()
        assertNull(readRequest(bytes.copyOf(30)))
        assertNull(readRequest(bytes.copyOf(bytes.size - 1)))
        val request = assertNotNull(readRequest(bytes))
        assertEquals("POST", request.method)
        assertEquals("/rest/system/resource/print", request.target)
        assertEquals("{\".proplist\":[]}", request.body)
        assertEquals("Basic YTpi", request.headers["Authorization"])
        assertEquals(bytes.size, request.size)
    }

    @Test
    fun requestsOneAfterAnotherAreTakenOneByOne() {
        val get = "GET /ubus HTTP/1.1\r\nHost: 192.0.2.1\r\n\r\n"
        val bytes = (post + get).encodeToByteArray()
        val first = assertNotNull(readRequest(bytes))
        val second = assertNotNull(readRequest(bytes.copyOfRange(first.size, bytes.size)))
        assertEquals("GET", second.method)
        assertEquals("", second.body)
        assertEquals(get.length, second.size)
    }

    @Test
    fun aRequestWithoutFieldsIsOneToo() {
        val request = assertNotNull(readRequest("GET / HTTP/1.1\r\n\r\n".encodeToByteArray()))
        assertEquals(emptyMap(), request.headers)
        assertEquals("/", request.target)
    }

    @Test
    fun whatIsNoHttpFailsAtOnce() {
        val api = byteArrayOf(6, '/'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(), 0)
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { readRequest(api) }.category)
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { readRequest("SSH-2.0-NetworkHelm\r\n".encodeToByteArray()) }.category)
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { readRequest("FETCH / HTTP/1.1\r\n\r\n".encodeToByteArray()) }.category)
        assertNull(readRequest(ByteArray(0)))
    }

    @Test
    fun anAnswerLosesTheFieldsTheStreamFramesItself() {
        val head = responseHead(200, "content-type: application/json\r\ncontent-length: 7\r\ncontent-encoding: gzip\r\nconnection: keep-alive\r\nx-total: 3")
        assertEquals("HTTP/1.1 200\r\ncontent-type: application/json\r\nx-total: 3\r\n", head)
    }
}
