package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.ui.kit.HintHost
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import jdk.jfr.Recording
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.File
import java.time.Duration
import java.util.Locale
import org.junit.Assume.assumeTrue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FrameMeasureTest {
    private fun asked() = assumeTrue("not asked for", System.getProperty("networkhelm.measure") != null)

    private val out = StringBuilder()

    private fun line(text: String) {
        println(text)
        out.appendLine(text)
    }

    private fun rows(count: Int, round: Int = 0) = List(count) { i ->
        Item(
            "*${i.toString(16)}",
            buildMap {
                put("chain", if (i % 3 == 0) "input" else "forward")
                put("action", if (i % 5 == 0) "drop" else "accept")
                put("src-address", "10.${i / 256 % 256}.${i % 256}.0/24")
                put("dst-address", "192.168.${i / 256 % 256}.${i % 256}")
                put("protocol", if (i % 2 == 0) "tcp" else "udp")
                put("dst-port", (i % 65535).toString())
                put("in-interface", "ether${i % 5}")
                put("out-interface", "bridge${i % 3}")
                put("bytes", (i * 1000L + round).toString())
                put("packets", (i * 10L + round).toString())
                if (i % 7 == 0) put("comment", "rule number $i")
            },
        )
    }

    private fun counters(count: Int, round: Int) = (0 until count).associate { i -> "*${i.toString(16)}" to mapOf("bytes" to (i * 1000L + round * 17).toString(), "packets" to (i * 10L + round * 3).toString()) }

    private val schema = MenuSchema(
        MenuPath.parse("ip/firewall/filter"),
        NodeKind.List,
        emptyList(),
        listOf("add", "print", "remove", "set"),
        listOf("chain", "action", "src-address", "dst-address", "protocol", "dst-port", "in-interface", "out-interface", "comment").map { Argument(it, ValueType.Text()) } +
            Argument("disabled", ValueType.YesNo),
    )

    private class Stats(val values: List<Long>) {
        fun ms(p: Double) = values.sorted().let { if (it.isEmpty()) 0.0 else it[((it.size - 1) * p).toInt()] / 1e6 }

        override fun toString() = "n=${values.size} p50=%.1f p95=%.1f max=%.1f sum=%.0f ms".format(ms(0.5), ms(0.95), ms(1.0), values.sum() / 1e6)
    }

    private fun SkikoComposeUiTest.timed(frames: Int, before: (Int) -> Unit = {}): Stats {
        val times = ArrayList<Long>(frames)
        repeat(frames) { i ->
            before(i)
            val started = System.nanoTime()
            mainClock.advanceTimeByFrame()
            times += System.nanoTime() - started
        }
        return Stats(times)
    }

    private fun scenario(name: String, count: Int, hint: Boolean = true, record: Boolean = false, only: String? = null) {
        asked()
        Locale.setDefault(Locale.ENGLISH)
        out.clear()
        val router = FakeRouter(mapOf("ip/firewall/filter" to rows(count)), schemas = mapOf("ip/firewall/filter" to schema))
        val updates = MutableSharedFlow<ListEvent>(extraBufferCapacity = 8)
        val live = object : DeviceSession by router {
            override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> = flow {
                emit(ListEvent.Snapshot(rows(count)))
                emitAll(updates)
            }
        }
        runDesktopComposeUiTest(1280, 800) {
            val storage = MemoryStorage()
            val tab = RouterTab(live, "perf", bundledPacks().values, storage, TableLayouts(storage))
            setContent {
                NetworkHelmTheme(dark = true) {
                    if (hint) HintHost { MainScreen(tab, onDisconnect = {}) } else MainScreen(tab, onDisconnect = {})
                }
            }
            waitUntil(timeoutMillis = 30_000) { tab.menu != null }
            runOnIdle { tab.open(MenuEntry("Firewall", "ip/firewall/filter")) }
            tab.workspace.windows.lastOrNull()?.maximized = true
            waitUntil(timeoutMillis = 60_000) { onAllNodes(hasText("$count items")).fetchSemanticsNodes().isNotEmpty() }
            mainClock.autoAdvance = false
            fun phase(label: String, frames: Int, before: (Int) -> Unit = {}) {
                val recording = if (record) Recording().apply {
                    enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2))
                    enable("jdk.GarbageCollection")
                    start()
                } else null
                val stats = timed(frames, before)
                recording?.let {
                    it.stop()
                    val directory = File("build/perf").also { d -> d.mkdirs() }
                    it.dump(File(directory, "$name-${label.replace(' ', '-')}.jfr").toPath())
                    it.close()
                }
                line("%-18s %s".format(label, stats))
            }
            timed(120)
            line("== $name rows=$count hint=$hint")
            if (only == "snapshots") {
                repeat(3) { round -> phase("snapshot ticks $round", 1200) { if (it % 12 == 0) updates.tryEmit(ListEvent.Snapshot(rows(count, it + 1))) } }
                return@runDesktopComposeUiTest
            }
            if (only == "scroll") {
                onNodeWithTag("table").performMouseInput { moveTo(center) }
                repeat(3) { round -> phase("scroll $round", 600) { onNodeWithTag("table").performMouseInput { scroll(if (it / 100 % 2 == 0) 3f else -3f) } } }
                return@runDesktopComposeUiTest
            }
            phase("idle frames", 60)
            phase("counter ticks", 240) { if (it % 12 == 0) router.counted.tryEmit(counters(count, it)) }
            phase("snapshot ticks", 240) { if (it % 12 == 0) updates.tryEmit(ListEvent.Snapshot(rows(count, it + 1))) }
            onNodeWithTag("table").performMouseInput { moveTo(center) }
            phase("scroll down", 200) { onNodeWithTag("table").performMouseInput { scroll(3f) } }
            phase("scroll up", 200) { onNodeWithTag("table").performMouseInput { scroll(-3f) } }
            val window = tab.workspace.windows.last()
            window.maximized = false
            timed(30)
            phase("window drag", 120) { window.position = DpOffset((20 + it % 60 * 4).dp, (20 + it % 40 * 3).dp) }
            phase("window resize", 120) { window.size = DpSize((900 + it % 80 * 4).dp, (500 + it % 60 * 3).dp) }
        }
        val directory = File("build/perf").also { it.mkdirs() }
        File(directory, "$name.txt").writeText(out.toString())
    }

    @Test
    fun rows1000() = scenario("frames-1000", 1000)

    @Test
    fun rows10000() = scenario("frames-10000", 10000)

    @Test
    fun rows1000Plain() = scenario("frames-1000-nohint", 1000, hint = false)

    @Test
    fun rows1000Recorded() = scenario("jfr-1000", 1000, record = true)

    @Test
    fun snapshots1000() = scenario("jfr-snap-1000", 1000, record = true, only = "snapshots")

    @Test
    fun snapshots10000() = scenario("jfr-snap-10000", 10000, record = true, only = "snapshots")

    @Test
    fun scroll1000() = scenario("jfr-scroll-1000", 1000, record = true, only = "scroll")

    @Test
    fun rows10000Recorded() = scenario("jfr-10000", 10000, record = true)

    @Test
    fun opening() {
        asked()
        Locale.setDefault(Locale.ENGLISH)
        out.clear()
        val packs = bundledPacks().values
        val firewall = packs.first { "ip/firewall/filter" in it.paths }
        val form = firewall.forms.getValue(checkNotNull(firewall.paths.getValue("ip/firewall/filter").form))
        val names = form.tabs.flatMap { it.properties }.distinct()
        val arguments = names.mapIndexed { at, name ->
            Argument(name, when (at % 4) {
                0 -> ValueType.Text()
                1 -> ValueType.Enumeration()
                2 -> ValueType.YesNo
                else -> ValueType.Integer(0, 65535)
            }, if (at % 4 == 1) listOf("accept", "drop", "reject", "jump", "log") else emptyList())
        }
        val path = MenuPath.parse("ip/firewall/filter")
        val schema = MenuSchema(path, NodeKind.List, emptyList(), listOf("add", "print", "remove", "set"), arguments)
        val router = FakeRouter(
            mapOf("ip/firewall/filter" to rows(3), "interface" to rows(40), "ip/address" to rows(20)),
            schemas = mapOf("ip/firewall/filter" to schema),
        )
        runDesktopComposeUiTest(1280, 800) {
            val storage = MemoryStorage()
            val tab = RouterTab(router, "perf", packs, storage, TableLayouts(storage))
            setContent { NetworkHelmTheme(dark = true) { HintHost { MainScreen(tab, onDisconnect = {}) } } }
            waitUntil(timeoutMillis = 30_000) { tab.menu != null }
            mainClock.autoAdvance = false
            repeat(60) { mainClock.advanceTimeByFrame() }
            fun burst(label: String, open: () -> Unit) {
                runOnIdle { open() }
                val times = ArrayList<Long>()
                repeat(12) {
                    val started = System.nanoTime()
                    mainClock.advanceTimeByFrame()
                    times += System.nanoTime() - started
                }
                line("%-34s first frames: %s ms".format(label, times.take(8).joinToString(" ") { "%.1f".format(it / 1e6) }))
            }
            for (round in 0 until 3) {
                burst("dialog of a firewall rule (${names.size})") { tab.openItem("Filter Rules", path, "*1") }
                runOnIdle { tab.workspace.close(tab.workspace.windows.last()) }
                repeat(5) { mainClock.advanceTimeByFrame() }
                burst("window Firewall") { tab.open(MenuEntry("Firewall", "ip/firewall/filter")) }
                runOnIdle { tab.workspace.close(tab.workspace.windows.last()) }
                repeat(5) { mainClock.advanceTimeByFrame() }
                burst("window Interfaces") { tab.open(MenuEntry("Interfaces", "interface")) }
                runOnIdle { tab.workspace.close(tab.workspace.windows.last()) }
                repeat(5) { mainClock.advanceTimeByFrame() }
            }
            val started = System.nanoTime()
            for (title in listOf("Addresses" to "ip/address", "Routes" to "ip/route", "ARP" to "ip/arp", "Pool" to "ip/pool", "DNS" to "ip/dns", "Services" to "ip/service", "Neighbors" to "ip/neighbor", "Settings" to "ip/settings", "Cloud" to "ip/cloud", "UPnP" to "ip/upnp")) {
                runOnIdle { tab.open(MenuEntry(title.first, title.second)) }
                repeat(6) { mainClock.advanceTimeByFrame() }
            }
            line("ten windows in a row: %.0f ms for 60 frames".format((System.nanoTime() - started) / 1e6))
        }
        val directory = File("build/perf").also { it.mkdirs() }
        File(directory, "opening.txt").writeText(out.toString())
    }

    @Test
    fun openingLoop() {
        asked()
        Locale.setDefault(Locale.ENGLISH)
        val packs = bundledPacks().values
        val router = FakeRouter(mapOf("ip/firewall/filter" to rows(3), "interface" to rows(40)))
        runDesktopComposeUiTest(1280, 800) {
            val storage = MemoryStorage()
            val tab = RouterTab(router, "perf", packs, storage, TableLayouts(storage))
            setContent { NetworkHelmTheme(dark = true) { HintHost { MainScreen(tab, onDisconnect = {}) } } }
            waitUntil(timeoutMillis = 30_000) { tab.menu != null }
            mainClock.autoAdvance = false
            repeat(60) { mainClock.advanceTimeByFrame() }
            fun cycle(entry: MenuEntry) {
                runOnIdle { tab.open(entry) }
                repeat(8) { mainClock.advanceTimeByFrame() }
                runOnIdle { tab.workspace.close(tab.workspace.windows.last()) }
                repeat(3) { mainClock.advanceTimeByFrame() }
            }
            repeat(20) { cycle(MenuEntry("Firewall", "ip/firewall/filter")) }
            val recording = Recording().apply {
                enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2))
                start()
            }
            val started = System.nanoTime()
            repeat(120) { cycle(MenuEntry(if (it % 2 == 0) "Firewall" else "Interfaces", if (it % 2 == 0) "ip/firewall/filter" else "interface")) }
            recording.stop()
            val directory = File("build/perf").also { it.mkdirs() }
            recording.dump(File(directory, "jfr-opening-loop.jfr").toPath())
            recording.close()
            File(directory, "opening-loop.txt").writeText("120 open and close cycles: %.0f ms\n".format((System.nanoTime() - started) / 1e6))
        }
    }
}
