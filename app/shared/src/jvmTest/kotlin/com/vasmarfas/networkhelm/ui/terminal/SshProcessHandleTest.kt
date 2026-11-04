package com.vasmarfas.networkhelm.ui.terminal

import com.vasmarfas.networkhelm.ssh.ShellChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SshProcessHandleTest {

    private class Scripted(private val chunks: List<ByteArray>) : ShellChannel {
        private var next = 0
        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (next == chunks.size) return -1
            val chunk = chunks[next++]
            chunk.copyInto(buffer, offset)
            return chunk.size
        }

        override suspend fun write(bytes: ByteArray) = Unit
        override fun resize(columns: Int, rows: Int) = Unit
        override fun close() = Unit
    }

    private fun text(vararg chunks: ByteArray): String = runBlocking {
        val handle = SshProcessHandle(Scripted(chunks.toList()))
        buildString {
            while (true) append(handle.read() ?: break)
        }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun aSequenceSplitBetweenTwoReadsIsJoined() {
        assertEquals("ж!", text(bytes(0xD0), bytes(0xB6, '!'.code)))
        assertEquals("a›b", text(bytes('a'.code, 0xE2), bytes(0x80), bytes(0xBA, 'b'.code)))
    }

    @Test
    fun aByteThatIsNoUtf8IsReplacedAndTheRestGoesOn() {
        assertEquals("�ab", text(bytes(0xFF, 'a'.code), bytes('b'.code)))
        assertEquals("x�y", text(bytes('x'.code, 0x80), bytes('y'.code)))
    }

    @Test
    fun aSequenceCutShortByTheNextByteDoesNotStopTheOnesAfterIt() {
        val shown = text(bytes('a'.code, 0xE2, 0x80), bytes('A'.code), bytes('B'.code))
        assertTrue(shown.startsWith("a�") && shown.endsWith("AB"), shown)
    }

    @Test
    fun theEndOfTheChannelEndsTheReads() = runBlocking {
        val handle = SshProcessHandle(Scripted(listOf(bytes('o'.code, 'k'.code))))
        assertEquals("ok", handle.read())
        assertEquals(null, handle.read())
        assertTrue(!handle.isAlive())
    }
}
