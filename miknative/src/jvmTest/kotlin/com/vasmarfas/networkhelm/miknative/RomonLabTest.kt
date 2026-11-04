package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Collections
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RomonLabTest {
    private class Router(val prefix: String, val host: String, val user: String, val password: String)

    private class Overlay(val seven: Router, val six: Router, val identities: Map<Router, String>, val ids: Map<Router, String>)

    private suspend fun NativeTerminal.pull(marker: String) {
        val text = StringBuilder()
        val buffer = ByteArray(8192)
        while (marker !in text) {
            val count = read(buffer, 0, buffer.size)
            check(count >= 0) { "the console ended" }
            text.append(buffer.decodeToString(0, count))
        }
    }

    private suspend fun NativeTerminal.ask(expression: String): String = withTimeout(15_000) {
        write(":put (\"rdx\" . $expression . \"x\")\r".encodeToByteArray())
        val answer = Regex("""rdx([^\r\n]*)x\r?\n""")
        val buffer = ByteArray(8192)
        var text = ""
        var found = answer.find(text)
        while (found == null) {
            val count = read(buffer, 0, buffer.size)
            check(count >= 0) { "the console ended" }
            text += buffer.decodeToString(0, count)
            found = answer.find(text)
        }
        found.groupValues[1]
    }

    private suspend fun <T> CoroutineScope.direct(router: Router, block: suspend (NativeTerminal) -> T): T {
        val connection = NativeConnection.open(router.host, router.user, router.password, this)
        try {
            val terminal = NativeTerminal.open(connection, router.password, "xterm-256color", 100, 30)
            try {
                terminal.pull("] > ")
                return block(terminal)
            } finally {
                terminal.close()
            }
        } finally {
            connection.close()
        }
    }

    private suspend fun CoroutineScope.hasFile(router: Router, name: String): Boolean {
        val connection = NativeConnection.open(router.host, router.user, router.password, this)
        try {
            NativeFiles(connection).read("/$name")
            return true
        } catch (e: DeviceException) {
            if (e.category == ErrorCategory.ConnectionLost) throw e
            return false
        } finally {
            connection.close()
        }
    }

    private suspend fun CoroutineScope.relayed(agent: Router, target: Router, targetId: String): Pair<NativeConnection, NativeTerminal> {
        var failure: DeviceException? = null
        repeat(10) {
            val connection = NativeConnection.open(agent.host, agent.user, agent.password, this)
            try {
                connection.relay(RomonTarget(targetId, target.user, target.password))
                val terminal = NativeTerminal.open(connection, target.password, "xterm-256color", 100, 30)
                terminal.pull("] > ")
                return connection to terminal
            } catch (e: Throwable) {
                connection.close()
                if (e !is DeviceException || e.category != ErrorCategory.ConnectionLost) throw e
                failure = e
                delay(3_000)
            }
        }
        throw checkNotNull(failure)
    }

    private suspend fun step(results: MutableList<String>, name: String, block: suspend () -> String) {
        try {
            results += "$name: ${block()}"
        } catch (e: Throwable) {
            if (e is CancellationException && !currentCoroutineContext().isActive) throw e
            results += "FAIL $name: ${e::class.simpleName} ${e.message}"
        }
    }

    private fun overlayTest(mode: String, block: suspend CoroutineScope.(Overlay, MutableList<String>) -> Unit) {
        assumeTrue("not asked for", System.getProperty("networkhelm.romon") == mode)
        val lab = Lab.require("chr.host", "chr.user", "chr.password", "chr6.host", "chr6.user", "chr6.password")
        val seven = Router("chr", lab.getValue("chr.host"), lab.getValue("chr.user"), lab.getValue("chr.password"))
        val six = Router("chr6", lab.getValue("chr6.host"), lab.getValue("chr6.user"), lab.getValue("chr6.password"))
        val secret = UUID.randomUUID().toString().replace("-", "")
        val changed = ArrayList<Router>()
        val results = Collections.synchronizedList(ArrayList<String>())
        runBlocking {
            try {
                withTimeout(600_000) {
                    for (router in listOf(seven, six)) {
                        val (enabled, current) = direct(router) { it.ask("[/tool romon get enabled]") to it.ask("[/tool romon get secrets]") }
                        check(enabled == "false" && current.isEmpty()) { "RoMON is configured on ${router.prefix} already (enabled=$enabled, secret ${current.length} characters); nothing was changed" }
                        val note = File(checkNotNull(Lab.root), ".notes/lab/romon-before-${router.prefix}.txt")
                        note.parentFile.mkdirs()
                        note.writeText("enabled=$enabled secret=<empty>\n")
                    }
                    val identities = listOf(seven, six).associateWith { direct(it) { terminal -> terminal.ask("[/system identity get name]") } }
                    assertNotEquals(identities.getValue(seven), identities.getValue(six), "the routers must differ by identity to tell them apart")
                    for (router in listOf(seven, six)) {
                        changed += router
                        direct(router) { terminal ->
                            terminal.write("/tool romon set enabled=yes secrets=$secret\r".encodeToByteArray())
                            assertEquals("true", terminal.ask("[/tool romon get enabled]"))
                        }
                    }
                    val ids = listOf(seven, six).associateWith { direct(it) { terminal -> terminal.ask("[/tool romon get current-id]") } }
                    results += "ids: chr ${ids.getValue(seven)}, chr6 ${ids.getValue(six)}"
                    block(Overlay(seven, six, identities, ids), results)
                }
            } finally {
                for (router in changed) {
                    try {
                        withTimeout(60_000) {
                            direct(router) { terminal ->
                                terminal.write("/tool romon set enabled=no secrets=\"\"\r".encodeToByteArray())
                                assertEquals("false", terminal.ask("[/tool romon get enabled]"))
                                assertTrue(terminal.ask("[/tool romon get secrets]").isEmpty(), "the secret is still set")
                                terminal.write("/file remove [find where name~\"rd-test-8291-romon\"]\r".encodeToByteArray())
                                delay(500)
                            }
                        }
                        results += "restored ${router.prefix}: enabled=no, secret empty"
                    } catch (e: Throwable) {
                        results += "FAIL restoring ${router.prefix}: ${e::class.simpleName} ${e.message}"
                    }
                }
                results.forEach { println("ROMON $it") }
            }
        }
        assertTrue(results.none { it.startsWith("FAIL") }, results.joinToString("\n"))
    }

    @Test
    fun linksBetweenTheTwoLabRoutersAndTheEndOfALink() = overlayTest("links") { overlay, results ->
        val (seven, six) = overlay.seven to overlay.six
        val bytes = ByteArray(5_000) { (it * 11).toByte() }
        val fileOnSeven = "rd-test-8291-romon-seven.bin"
        val fileOnSix = "rd-test-8291-romon-six.bin"

        step(results, "agent chr6, target chr") {
            val (connection, console) = relayed(six, seven, overlay.ids.getValue(seven))
            try {
                assertEquals(overlay.identities.getValue(seven), console.ask("[/system identity get name]"), "the console must be the target's")
                val files = NativeFiles(connection)
                files.write("/$fileOnSeven", bytes)
                assertContentEquals(bytes, files.read("/$fileOnSeven"))
                assertTrue(files.list("/").any { it.name == fileOnSeven && it.size == bytes.size.toLong() })
                assertTrue(hasFile(seven, fileOnSeven), "the file must be on the target")
                assertFalse(hasFile(six, fileOnSeven), "the file must not be on the agent")
                files.remove("/$fileOnSeven")
                assertFalse(files.list("/").any { it.name == fileOnSeven })
                "console, write, read, list and remove on the target"
            } finally {
                console.close()
                connection.close()
            }
        }

        step(results, "agent chr, target chr6, then the end of the link") {
            val (connection, console) = relayed(seven, six, overlay.ids.getValue(six))
            try {
                assertEquals(overlay.identities.getValue(six), console.ask("[/system identity get name]"), "the console must be the target's")
                val files = NativeFiles(connection)
                files.write("/$fileOnSix", bytes)
                assertContentEquals(bytes, files.read("/$fileOnSix"))
                assertTrue(hasFile(six, fileOnSix), "the file must be on the target")
                assertFalse(hasFile(seven, fileOnSix), "the file must not be on the agent")
                console.write("/file remove $fileOnSix\r".encodeToByteArray())
                delay(1_000)
                assertFailsWith<DeviceException> { files.read("/$fileOnSix") }
                results += "agent chr, target chr6: console, write, read and remove on the target"

                direct(six) { terminal ->
                    terminal.write("/tool romon set enabled=no\r".encodeToByteArray())
                    assertEquals("false", terminal.ask("[/tool romon get enabled]"))
                }
                val started = System.nanoTime()
                val ended = withTimeoutOrNull(90_000) {
                    val buffer = ByteArray(4096)
                    try {
                        while (console.read(buffer, 0, buffer.size) >= 0) delay(10)
                        "the read ended without an error"
                    } catch (e: DeviceException) {
                        "${e.category} ${e.message}"
                    }
                }
                val seconds = (System.nanoTime() - started) / 1_000_000_000
                assertTrue(ended != null && "RoMON link ended" in ended, "no end of the link in $seconds s: $ended")
                "the link ended $seconds s after RoMON was switched off on the target ($ended)"
            } finally {
                console.close()
                connection.close()
            }
        }
    }
}
