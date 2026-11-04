package com.vasmarfas.networkhelm.routeros.api

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApiCodecTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun readerOf(data: ByteArray): SentenceReader {
        var at = 0
        return SentenceReader { buffer, offset, _ ->
            if (at == data.size) -1 else {
                buffer[offset] = data[at++]
                1
            }
        }
    }

    @Test
    fun lengthPrefixesAtEveryBoundary() {
        assertContentEquals(bytes(0x00), encodeLength(0))
        assertContentEquals(bytes(0x7F), encodeLength(0x7F))
        assertContentEquals(bytes(0x80, 0x80), encodeLength(0x80))
        assertContentEquals(bytes(0xBF, 0xFF), encodeLength(0x3FFF))
        assertContentEquals(bytes(0xC0, 0x40, 0x00), encodeLength(0x4000))
        assertContentEquals(bytes(0xDF, 0xFF, 0xFF), encodeLength(0x1FFFFF))
        assertContentEquals(bytes(0xE0, 0x20, 0x00, 0x00), encodeLength(0x200000))
        assertContentEquals(bytes(0xEF, 0xFF, 0xFF, 0xFF), encodeLength(0xFFFFFFF))
        assertContentEquals(bytes(0xF0, 0x10, 0x00, 0x00, 0x00), encodeLength(0x10000000))
    }

    @Test
    fun wordsOfEveryLengthClassSurviveARoundTrip() = runTest {
        val words = listOf(1, 0x7F, 0x80, 0x3FFF, 0x4000, 0x1FFFFF, 0x200000).map { size -> ByteArray(size) { (it % 251).toByte() } }
        val reader = readerOf(encodeSentence(words) + encodeSentence(listOf("!done".encodeToByteArray())))
        val first = assertNotNull(reader.next())
        assertEquals(words.size, first.size)
        words.zip(first).forEach { (sent, received) -> assertContentEquals(sent, received) }
        assertEquals("!done", assertNotNull(reader.next()).single().decodeToString())
        assertNull(reader.next())
    }

    @Test
    fun streamEndingInsideASentenceIsAnError() = runTest {
        val sentence = encodeSentence(listOf("/ip/address/print".encodeToByteArray()))
        assertFailsWith<ApiProtocolException> { readerOf(sentence.copyOf(sentence.size - 1)).next() }
        assertFailsWith<ApiProtocolException> { readerOf(sentence.copyOf(5)).next() }
    }

    @Test
    fun reservedControlByteIsAnError() = runTest {
        assertFailsWith<ApiProtocolException> { readerOf(bytes(0xF8, 0x00)).next() }
    }

    @Test
    fun wordLongerThanTheLimitIsRejectedBeforeAllocation() = runTest {
        assertFailsWith<ApiProtocolException> { readerOf(bytes(0xF0, 0x7F, 0xFF, 0xFF, 0xFF)).next() }
    }

    @Test
    fun replyAttributesKeepEqualsSignsInValues() {
        val reply = parseReply(listOf("!re", "=.id=*1A", "=comment=a=b=", "=disabled=", ".tag=7"))
        assertEquals("!re", reply.type)
        assertEquals("7", reply.tag)
        assertEquals(mapOf(".id" to "*1A", "comment" to "a=b=", "disabled" to ""), reply.attributes)
    }

    @Test
    fun fatalCarriesABareReason() {
        val reply = parseReply(listOf("!fatal", "session terminated on request"))
        assertEquals("session terminated on request", reply.attributes["message"])
        assertNull(reply.tag)
    }
}
