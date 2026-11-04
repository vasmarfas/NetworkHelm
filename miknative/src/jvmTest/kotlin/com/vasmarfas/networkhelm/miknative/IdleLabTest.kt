package com.vasmarfas.networkhelm.miknative

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import kotlin.test.Test

class IdleLabTest {
    @Test
    fun anIdleSessionIsKeptOrDropped() = runBlocking {
        val checkpoints = System.getProperty("networkhelm.idle")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        assumeTrue("not asked for", checkpoints.isNotEmpty())
        val lab = Lab.require("chr.host", "chr.user", "chr.password")
        val withConsole = NativeConnection.open(lab.getValue("chr.host"), lab.getValue("chr.user"), lab.getValue("chr.password"), this)
        val bare = NativeConnection.open(lab.getValue("chr.host"), lab.getValue("chr.user"), lab.getValue("chr.password"), this)
        val terminal = NativeTerminal.open(withConsole, lab.getValue("chr.password"), "xterm-256color", 100, 30)
        val buffer = ByteArray(4096)
        withTimeoutOrNull(5000) { terminal.read(buffer, 0, buffer.size) }
        var elapsed = 0
        for (checkpoint in checkpoints) {
            delay((checkpoint - elapsed) * 60_000L)
            elapsed = checkpoint
            val console = runCatching {
                terminal.write(byteArrayOf(13))
                withTimeoutOrNull(5000) { terminal.read(buffer, 0, buffer.size) }?.let { it > 0 } ?: false
            }.getOrDefault(false)
            val plain = runCatching { bare.newSource().request(listOf(13, 4), M2Value.U8(7)); true }.getOrDefault(false)
            println("IDLE after $checkpoint min: console answers=$console, bare session answers=$plain")
            if (!console && !plain) break
        }
        terminal.close()
        withConsole.close()
        bare.close()
    }
}
