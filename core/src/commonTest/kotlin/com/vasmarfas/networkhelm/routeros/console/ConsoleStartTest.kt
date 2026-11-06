package com.vasmarfas.networkhelm.routeros.console

import com.vasmarfas.networkhelm.routeros.api.ApiConnectionClosedException
import com.vasmarfas.networkhelm.routeros.api.ApiEncoding
import com.vasmarfas.networkhelm.ssh.ShellChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class SaysThenSilent(private var said: String) : ShellChannel {
    private val never = CompletableDeferred<Int>()
    val written = mutableListOf<Byte>()

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (said.isEmpty()) return never.await()
        val bytes = said.encodeToByteArray()
        bytes.copyInto(buffer, offset)
        said = ""
        return bytes.size
    }

    override suspend fun write(bytes: ByteArray) {
        written += bytes.asList()
    }

    override fun resize(columns: Int, rows: Int) = Unit

    override fun close() = Unit
}

class ConsoleStartTest {
    @Test
    fun aSilentConsoleReportsWhatItSaidLast() = runTest {
        val channel = SaysThenSilent("\r\nWelcome to the demo\r\nChoose a language: ")
        val failure = assertFailsWith<ApiConnectionClosedException> { ConsoleTerminal.open({ channel }, Dialect.Serialize, ApiEncoding.Utf8) }
        assertTrue("Choose a language:" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun aQuestionForANewPasswordIsLeftWithCtrlCOnceAndStillReported() = runTest {
        val channel = SaysThenSilent("\r\nChange your password (Ctrl-C to skip)\r\nnew password> ")
        val failure = assertFailsWith<ApiConnectionClosedException> { ConsoleTerminal.open({ channel }, Dialect.Serialize, ApiEncoding.Utf8) }
        assertEquals(listOf<Byte>(3), channel.written)
        assertTrue("new password>" in failure.message.orEmpty(), failure.message)
    }
}
