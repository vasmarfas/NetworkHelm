package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.UdpLink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsTest {

    private fun u16(bytes: ByteArray, at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    // an answer to the query with one A record 192.0.2.7 pointing back at the question name
    private fun answer(query: ByteArray, truncated: Boolean, id: Int = u16(query, 0)): ByteArray {
        val question = query.copyOfRange(12, query.size - 11)
        val flags = if (truncated) 0x8380 else 0x8180
        val header = byteArrayOf((id shr 8).toByte(), id.toByte(), (flags shr 8).toByte(), flags.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
        val record = byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 0x0E, 0x10, 0, 4, 192.toByte(), 0, 2, 7)
        return header + question + record
    }

    private class Resolver(val reply: (ByteArray) -> ByteArray?) : UdpLink {
        val sent = mutableListOf<ByteArray>()
        var closed = false

        override suspend fun send(datagram: ByteArray) {
            sent += datagram
        }

        override suspend fun receive(timeoutMillis: Int): ByteArray? = reply(sent.last())

        override fun close() {
            closed = true
        }
    }

    private class FramedServer(val reply: (ByteArray) -> ByteArray) : ByteStream {
        var written = byteArrayOf()
        private var answer = byteArrayOf()
        private var at = 0

        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (at == answer.size) return -1
            val count = minOf(length, answer.size - at, 5)
            answer.copyInto(buffer, offset, at, at + count)
            at += count
            return count
        }

        override suspend fun write(bytes: ByteArray) {
            written += bytes
            val body = reply(written.copyOfRange(2, written.size))
            answer = byteArrayOf((body.size shr 8).toByte(), body.size.toByte()) + body
        }

        override fun close() = Unit
    }

    @Test
    fun theQueryAsksForRecursionAndCarriesEdns() {
        val bytes = DnsMessage.buildQuery("example.com", 1, 0x1234)
        assertEquals(0x1234, u16(bytes, 0))
        assertEquals(0x0120, u16(bytes, 2))
        assertEquals(1, bytes[11].toInt())
        assertEquals(7, bytes[12].toInt())
        assertEquals('e'.code, bytes[13].toInt())
        assertEquals(1, bytes[28].toInt())
        assertEquals(41, bytes[31].toInt())
        assertEquals(1232, u16(bytes, 32))
        assertEquals(40, bytes.size)
    }

    @Test
    fun aCompressedNameInTheAnswerPointsBackAtTheQuestion() {
        val header = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
        val question = byteArrayOf(7) + "example".encodeToByteArray() + byteArrayOf(3) + "com".encodeToByteArray() + byteArrayOf(0, 0, 1, 0, 1)
        val answer = byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 0x0E, 0x10, 0, 4, 93, 184.toByte(), 216.toByte(), 34)
        val response = DnsMessage.parse(header + question + answer)
        assertEquals("NOERROR", response.rcodeName)
        assertEquals("example.com", response.answers.single().name)
        assertEquals("93.184.216.34", response.answers.single().data)
        assertEquals("A", response.answers.single().typeName)
        assertEquals(3600L, response.answers.single().ttl)
    }

    @Test
    fun anSoaRecordListsItsServersAndFiveNumbers() {
        val soa = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 0, 0, 1, 0, 0, 0, 0) +
            byteArrayOf(0, 0, 6, 0, 1, 0, 0, 0x0E, 0x10, 0, 30) +
            byteArrayOf(2) + "ns".encodeToByteArray() + byteArrayOf(0) + byteArrayOf(4) + "host".encodeToByteArray() + byteArrayOf(0) +
            byteArrayOf(0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4, 0, 0, 0, 5)
        assertEquals("ns host 1 2 3 4 5", DnsMessage.parse(soa).answers.single().data)
    }

    @Test
    fun anAnswerOverUdpEndsTheQuery() = runTest {
        val resolver = Resolver { answer(it, truncated = false) }
        val response = assertNotNull(dnsQuery("192.0.2.53", "example.com", 1, udp = { _, _ -> resolver }, tcp = { _, _, _ -> error("no TCP") }))
        assertEquals("192.0.2.7", response.answers.single().data)
        assertFalse(response.truncated)
        assertEquals(1, resolver.sent.size)
        assertTrue(resolver.closed)
    }

    @Test
    fun aTruncatedAnswerIsAskedAgainOverTcp() = runTest {
        val server = FramedServer { answer(it, truncated = false) }
        var port = 0
        val response = assertNotNull(
            dnsQuery("192.0.2.53", "example.com", 1, udp = { _, _ -> Resolver { answer(it, truncated = true) } }, tcp = { _, p, _ ->
                port = p
                server
            }),
        )
        assertFalse(response.truncated)
        assertEquals("192.0.2.7", response.answers.single().data)
        assertEquals(53, port)
        assertEquals(server.written.size - 2, u16(server.written, 0))
    }

    @Test
    fun anAnswerToAnotherQueryOrSilenceGivesNothing() = runTest {
        val stranger = Resolver { answer(it, truncated = false, id = u16(it, 0) xor 1) }
        assertNull(dnsQuery("192.0.2.53", "example.com", 1, udp = { _, _ -> stranger }))
        assertEquals(2, stranger.sent.size, "asked twice")
        val silent = Resolver { null }
        assertNull(dnsQuery("192.0.2.53", "example.com", 1, udp = { _, _ -> silent }))
        assertTrue(silent.closed)
    }
}
