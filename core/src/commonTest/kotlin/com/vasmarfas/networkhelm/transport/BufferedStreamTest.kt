package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.model.DeviceException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BufferedStreamTest {

    @Test
    fun lineLimitsCountBytesWithoutConsumingTheFollowingLine() = runTest {
        val stream = BufferedStream(CannedStream("abcd\r\nимя\nlast"))
        assertEquals("abcd", stream.readLine(maxBytes = 4))
        assertEquals("имя", stream.readLine(maxBytes = 6))
        assertEquals("last", stream.readLine(maxBytes = 4))
        assertEquals(null, stream.readLine(maxBytes = 0))
        for (answer in listOf("abcde\n", "abcde", "abcde\r\n", "имя\n")) {
            assertFailsWith<DeviceException> { BufferedStream(CannedStream(answer)).readLine(maxBytes = 4) }
        }
    }

    @Test
    fun anEndlessLineIsStoppedBeforeItsBufferCanGrowPastTheLimit() = runTest {
        var received = 0
        val source = object : ByteStream {
            override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill('x'.code.toByte(), offset, offset + length)
                received += length
                return length
            }

            override suspend fun write(bytes: ByteArray) = Unit
            override fun close() = Unit
        }
        assertFailsWith<DeviceException> { BufferedStream(source).readLine(maxBytes = 16) }
        assertTrue(received <= 18)
    }
}
