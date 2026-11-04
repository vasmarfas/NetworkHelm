package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SecureChannelTest {
    private val secret = ByteArray(32) { (it * 5).toByte() }
    private val client = SecureChannel(streamKeys(secret, server = false))
    private val server = SecureChannel(streamKeys(secret, server = true))

    private suspend fun payloadOf(framed: ByteArray) = checkNotNull(FrameReader(MemoryStream(framed)).next()).payload

    @Test
    fun aMessageOpensOnTheOtherEndWhateverItsSize() = runTest {
        for (size in listOf(0, 1, 15, 16, 17, 100, 236, 237, 254, 255, 256, 3510, 3520, 3531, 3532, 20_000)) {
            val message = secureRandomBytes(size)
            assertContentEquals(message, server.open(payloadOf(client.seal(message))).single(), "size $size")
            assertContentEquals(message, client.open(payloadOf(server.seal(message))).single(), "size $size")
        }
    }

    @Test
    fun aFrameThatCarriesSeveralMessagesGivesThemInOrder() = runTest {
        val messages = listOf(secureRandomBytes(3510), secureRandomBytes(1), secureRandomBytes(560), secureRandomBytes(16))
        val opened = server.open(messages.map(client::record).reduce(ByteArray::plus))
        assertEquals(messages.size, opened.size)
        messages.zip(opened).forEach { (sent, got) -> assertContentEquals(sent, got) }
    }

    @Test
    fun aRecordThatClaimsMoreThanTheFrameHoldsOrNothingIsRefused() = runTest {
        val record = client.record("a message".encodeToByteArray())
        assertFailsWith<DeviceException> { server.open(record + record.copyOf(record.size - 16)) }
        val empty = record.copyOf().also { it[0] = 0; it[1] = 0 }
        assertFailsWith<DeviceException> { server.open(empty) }
    }

    @Test
    fun theCiphertextIsAWholeNumberOfBlocksBehindALengthAndAnIv() = runTest {
        val framed = client.seal(ByteArray(40))
        val payload = payloadOf(framed)
        val length = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        assertEquals(payload.size - 18, length)
        assertEquals(0, length % 16)
    }

    @Test
    fun theSameMessageGoesOutDifferentlyEachTime() = runTest {
        val message = ByteArray(40)
        assertFalse(payloadOf(client.seal(message)).contentEquals(payloadOf(client.seal(message))))
    }

    @Test
    fun aFlippedBitIsCaught() = runTest {
        val payload = payloadOf(client.seal("a message".encodeToByteArray()))
        for (position in listOf(2, 20, payload.size - 1)) {
            val damaged = payload.copyOf().also { it[position] = (it[position].toInt() xor 1).toByte() }
            assertFailsWith<DeviceException> { server.open(damaged) }
        }
    }

    @Test
    fun aMessageForTheOtherDirectionIsRefused() = runTest {
        val payload = payloadOf(client.seal("a message".encodeToByteArray()))
        assertFailsWith<DeviceException> { client.open(payload) }
    }

    @Test
    fun aTruncatedFrameIsRefused() = runTest {
        val payload = payloadOf(client.seal("a message".encodeToByteArray()))
        assertFailsWith<DeviceException> { server.open(payload.copyOf(payload.size - 5)) }
        assertFailsWith<DeviceException> { server.open(ByteArray(10)) }
    }
}
