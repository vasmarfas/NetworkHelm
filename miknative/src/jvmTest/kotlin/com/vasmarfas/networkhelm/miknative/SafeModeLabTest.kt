package com.vasmarfas.networkhelm.miknative

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class SafeModeLabTest {
    private suspend fun NativeTerminal.readUntil(marker: String): String {
        val text = StringBuilder()
        val buffer = ByteArray(4096)
        while (marker !in text) {
            val count = read(buffer, 0, buffer.size)
            if (count < 0) break
            text.append(buffer.decodeToString(0, count))
        }
        return text.toString()
    }

    private fun held(prefix: String) = runBlocking {
        assumeTrue("not asked for", System.getProperty("networkhelm.safemode") != null)
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password")
        withTimeout(60_000) {
            val connection = NativeConnection.open(lab.getValue("$prefix.host"), lab.getValue("$prefix.user"), lab.getValue("$prefix.password"), this)
            val terminal = NativeTerminal.open(connection, lab.getValue("$prefix.password"), "xterm", 100, 30)
            try {
                terminal.readUntil("] > ")
                terminal.write(byteArrayOf(0x18))
                assertTrue("<SAFE>" in terminal.readUntil("<SAFE>"))
                terminal.write(byteArrayOf(0x18))
                assertTrue("] > " in terminal.readUntil("] > ").substringAfterLast("<SAFE>"))
            } finally {
                terminal.close()
                connection.close()
            }
        }
    }

    @Test
    fun routerOs7HoldsSafeModeInTheConsole() = held("chr")

    @Test
    fun routerOs6HoldsSafeModeInTheConsole() = held("chr6")
}
