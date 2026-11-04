package com.vasmarfas.networkhelm.ui.log

import com.vasmarfas.networkhelm.miknative.connectNativeHost
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.session.rows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class NativeLogLabTest {
    private val log = MenuPath.parse("log")

    private fun target(): List<String> {
        System.getenv("NETWORKHELM_LAB_CHR")?.takeIf { it.isNotEmpty() }?.split('|')?.let { return it }
        val file = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("lab.properties is missing", file != null)
        val lab = Properties().apply { file?.inputStream()?.use(::load) }
        assumeTrue("chr.host is empty", lab.getProperty("chr.host").orEmpty().isNotEmpty())
        return listOf(lab.getProperty("chr.host"), lab.getProperty("chr.user").orEmpty(), lab.getProperty("chr.password").orEmpty())
    }

    private suspend fun lines(session: DeviceSession): List<Item> = withTimeout(120.seconds) { (session.watch(log).first() as ListEvent.Snapshot).items }

    private suspend fun describing(native: DeviceSession): Boolean = native.packs(null).any { pack -> pack.paths["log"]?.native?.log == true }

    private fun both(test: suspend CoroutineScope.(DeviceSession, DeviceSession, List<String>) -> Unit) = runBlocking {
        val target = target()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val console = connectNativeHost(target[0], 8291, target[1], target[2], scope)
        val native = connectNativeHost(target[0], 8291, target[1], target[2], scope)
        try {
            assumeTrue("the router describes no log window", describing(native))
            scope.test(console, native, target)
        } finally {
            native.close()
            console.close()
            scope.cancel()
        }
    }

    @Test
    fun theLogReadOverTheObjectModelHasTheLinesTheConsoleGivesInTheSameOrder() = both { console, native, _ ->
        val started = TimeSource.Monotonic.markNow()
        val viaConsole = lines(console)
        val consoleTime = started.elapsedNow()
        val later = TimeSource.Monotonic.markNow()
        val viaNative = lines(native)
        println("LOGLAB console ${viaConsole.size} lines in $consoleTime, object model ${viaNative.size} lines in ${later.elapsedNow()}")
        val known = viaNative.mapTo(HashSet()) { it.id }
        val shared = viaConsole.filter { it.id in known }
        assertTrue(viaNative.isNotEmpty() && shared.size * 2 > viaConsole.size, "the two reads share ${shared.size} of ${viaConsole.size} lines")
        val byId = viaNative.associateBy { it.id }
        for (line in shared) {
            val other = byId.getValue(line.id)
            assertEquals(listOf(line["time"], line["topics"], line["message"]), listOf(other["time"], other["topics"], other["message"]), line.id)
        }
        val sharedIds = shared.mapTo(HashSet()) { it.id }
        assertEquals(shared.map { it.id }, viaNative.map { it.id }.filter { it in sharedIds })
        assertTrue(viaNative.all { it["buffer"] != null }, "every line comes with the buffer it was logged to")
    }

    @Test
    fun aLineTheRouterLogsAfterTheFirstReadReachesTheWindowOverTheObjectModel() = both { _, native, target ->
        val before = lines(native).mapTo(HashSet()) { it.id }
        val arrival = async(Dispatchers.Default) {
            withTimeout(90.seconds) {
                native.watch(log).rows().first { rows -> rows.any { it.id !in before && it["message"].orEmpty().contains("logged in") } }
            }
        }
        delay(1.seconds)
        val begun = TimeSource.Monotonic.markNow()
        // a login is a line of the log of the router
        val another = connectNativeHost(target[0], 8291, target[1], target[2], this)
        try {
            val rows = arrival.await()
            println("LOGLAB a login reached the window in ${begun.elapsedNow()}")
            assertTrue(rows.any { it.id !in before })
        } finally {
            another.close()
        }
    }
}
