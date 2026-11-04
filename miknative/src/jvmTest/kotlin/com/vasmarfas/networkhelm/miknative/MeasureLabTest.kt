package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.transport.openTcp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test

class MeasureLabTest {
    private suspend fun ShellChannel.readUntil(marker: String): String {
        val text = StringBuilder()
        val buffer = ByteArray(8192)
        while (marker !in text) {
            val count = read(buffer, 0, buffer.size)
            if (count < 0) break
            text.append(buffer.decodeToString(0, count))
        }
        return text.toString()
    }

    private fun median(values: List<Long>) = if (values.isEmpty()) -1 else values.sorted()[values.size / 2]

    private fun measure(prefix: String, logins: Int) = runBlocking {
        assumeTrue("not asked for", System.getProperty("networkhelm.measure") != null)
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password")
        withTimeout(240_000) { measureRouter(prefix, lab.getValue("$prefix.host"), lab.getValue("$prefix.user"), lab.getValue("$prefix.password"), logins) }
    }

    private suspend fun CoroutineScope.measureRouter(prefix: String, host: String, user: String, password: String, logins: Int) {
        val times = ArrayList<Long>()
        val failures = ArrayList<String>()
        repeat(logins) {
            val started = System.nanoTime()
            try {
                NativeConnection.open(host, user, password, this).close()
                times += (System.nanoTime() - started) / 1_000_000
            } catch (e: Exception) {
                failures += e.toString()
            }
        }
        println("MEASURE $prefix login: n=$logins failed=${failures.size} $failures median=${median(times)} ms max=${times.maxOrNull()} ms")

        val stream = openTcp(host, 8291)
        println("MEASURE $prefix wrong password: " + runCatching { NativeConnection.open(stream, user, "rd-test-8291-wrong", this) }.exceptionOrNull())
        stream.close()

        val connection = NativeConnection.open(host, user, password, this)
        val files = NativeFiles(connection)
        val big = "rd-test-8291-speed.bin"
        val small = "rd-test-8291-small.txt"
        try {
            val terminal = NativeTerminal.open(connection, password, "xterm-256color", 100, 30)
            terminal.readUntil("] > ")
            val round = ArrayList<Long>()
            repeat(10) {
                val started = System.nanoTime()
                terminal.write(":put (123*7)\r".encodeToByteArray())
                val answer = terminal.readUntil("861")
                round += (System.nanoTime() - started) / 1_000_000
                if ("] > " !in answer.substringAfter("861")) terminal.readUntil("] > ")
            }
            println("MEASURE $prefix console round trip: median=${median(round)} ms max=${round.maxOrNull()} ms")
            terminal.close()

            val megabyte = ByteArray(1_000_000) { (it * 7).toByte() }
            val up = System.nanoTime()
            files.write("/$big", megabyte)
            val upMs = (System.nanoTime() - up) / 1_000_000
            val down = System.nanoTime()
            files.read("/$big")
            println("MEASURE $prefix 1 MB: write $upMs ms, read ${(System.nanoTime() - down) / 1_000_000} ms")

            if (prefix == "chr6") {
                val source = connection.newSource()
                val opened = source.request(listOf(2, 2), M2Value.U8(3), 1 to M2Value.Text(big))
                val piece = source.request(listOf(2, 2), M2Value.U8(4), M2Key.SessionId to M2Value.U32(checkNotNull(opened.number(M2Key.SessionId))), 2 to M2Value.U32(32_000))
                println("MEASURE $prefix piece of a read asked for 32000 bytes: ${piece.raw(3)?.size}")
                source.close()
            }

            files.write("/$small", ByteArray(10))
            val many = System.nanoTime()
            var failedAt = -1
            for (round in 1..300) {
                try {
                    files.read("/$small")
                } catch (e: Exception) {
                    failedAt = round
                    println("MEASURE $prefix read $round failed: $e")
                    break
                }
            }
            println("MEASURE $prefix 300 small reads: failedAt=$failedAt, ${(System.nanoTime() - many) / 1_000_000} ms")

            if (prefix == "chr") {
                val listing = System.nanoTime()
                val count = files.list("/").size
                println("MEASURE $prefix listing: $count entries in ${(System.nanoTime() - listing) / 1_000_000} ms")
                val empty = System.nanoTime()
                files.list("/rd-test-8291-nothing")
                println("MEASURE $prefix listing of a missing path: ${(System.nanoTime() - empty) / 1_000_000} ms")
            }
        } finally {
            if (prefix == "chr") {
                files.remove("/$big")
                files.remove("/$small")
            } else {
                val console = NativeTerminal.open(connection, password, "xterm-256color", 100, 30)
                console.readUntil("] > ")
                console.write("/file remove $big,$small\r".encodeToByteArray())
                delay(1000)
                console.close()
            }
            connection.close()
        }
    }

    @Test
    fun routerOs7() = measure("chr", 40)

    @Test
    fun routerOs6() = measure("chr6", 20)

    private fun loginFields(prefix: String) = runBlocking {
        assumeTrue("not asked for", System.getProperty("networkhelm.measure") != null)
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password")
        withTimeout(60_000) {
            val connection = NativeConnection.open(lab.getValue("$prefix.host"), lab.getValue("$prefix.user"), lab.getValue("$prefix.password"), this)
            val variants = mapOf(
                "all" to listOf(8 to M2Value.U8(0), 11 to M2Value.Message(M2Message.of(1 to M2Value.U8(0)))),
                "without 8" to listOf(11 to M2Value.Message(M2Message.of(1 to M2Value.U8(0)))),
                "without 11" to listOf(8 to M2Value.U8(0)),
                "without both" to emptyList(),
            )
            for ((label, extra) in variants) {
                val source = connection.newSource()
                source.request(listOf(13, 4), M2Value.U8(7))
                val outcome = runCatching {
                    val fields = arrayOf(5 to M2Value.U32(80), 6 to M2Value.U32(24), 7 to M2Value.Text("vt102"), 1 to M2Value.Text(lab.getValue("$prefix.password"))) + extra
                    source.request(listOf(76), M2Value.U32(0x0A0065), *fields).number(M2Key.SessionId)
                }
                println("MEASURE $prefix login fields $label: $outcome")
                source.close()
            }
            connection.close()
        }
    }

    @Test
    fun routerOs7LoginFields() = loginFields("chr")

    @Test
    fun routerOs6LoginFields() = loginFields("chr6")
}
