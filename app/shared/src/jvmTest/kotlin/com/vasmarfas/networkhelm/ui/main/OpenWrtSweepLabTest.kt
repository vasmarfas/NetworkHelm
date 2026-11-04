package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.app.connectOpenWrtWithForms
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.openwrt.OpenWrtLogin
import com.vasmarfas.networkhelm.openwrt.OpenWrtProtocol
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class OpenWrtSweepLabTest {

    @Test
    fun unifiedViewOfOpenWrt25() = sweep("openwrt", MenuView.RouterOs, "Unified view")

    @Test
    fun openWrtViewOfOpenWrt25() = sweep("openwrt", MenuView.WinBox, "OpenWrt view")

    @Test
    fun luciViewOfOpenWrt25() = sweep("openwrt", MenuView.Native, "LuCI view")

    @Test
    fun unifiedViewOfOpenWrt24() = sweep("openwrt24", MenuView.RouterOs, "Unified view")

    @Test
    fun openWrtViewOfOpenWrt24() = sweep("openwrt24", MenuView.WinBox, "OpenWrt view")

    @Test
    fun luciViewOfOpenWrt24() = sweep("openwrt24", MenuView.Native, "LuCI view")

    @Test
    fun unifiedViewOfOpenWrt25OverHttps() = sweep("openwrt", MenuView.RouterOs, "Unified view", OpenWrtProtocol.UbusSsl, 8)

    @Test
    fun openWrtViewOfOpenWrt25OverHttps() = sweep("openwrt", MenuView.WinBox, "OpenWrt view", OpenWrtProtocol.UbusSsl, 8)

    @Test
    fun luciViewOfOpenWrt25OverHttps() = sweep("openwrt", MenuView.Native, "LuCI view", OpenWrtProtocol.UbusSsl, 8)

    @Test
    fun theMenuOfTheOpenWrtViewComesOverSsh() = sweep("openwrt", MenuView.WinBox, "OpenWrt view", OpenWrtProtocol.Ssh, 1)

    @Test
    fun theMenuOfTheOpenWrtViewComesOverHttps() = sweep("openwrt", MenuView.WinBox, "OpenWrt view", OpenWrtProtocol.UbusSsl, 1)

    @Test
    fun theMenuOfTheOpenWrtViewComesOverHttp() = sweep("openwrt", MenuView.WinBox, "OpenWrt view", OpenWrtProtocol.Ubus, 1)

    private fun lab(): Properties {
        val file = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("lab.properties is missing", file != null)
        return Properties().apply { file?.inputStream()?.use(::load) }
    }

    private fun sweep(
        prefix: String,
        view: MenuView,
        label: String,
        protocol: OpenWrtProtocol = OpenWrtProtocol.Ssh,
        limit: Int = Int.MAX_VALUE,
    ) = runDesktopComposeUiTest(1280, 800, testTimeout = 40.minutes) {
        Locale.setDefault(Locale.ENGLISH)
        val lab = lab()
        val host = lab.getProperty("$prefix.host").orEmpty()
        assumeTrue("$prefix.host is empty", host.isNotEmpty())
        val key = lab.getProperty("$prefix.ssh.hostKey")
        val login = OpenWrtLogin(host, lab.getProperty("$prefix.user"), lab.getProperty("$prefix.password"), protocol)
        val trust = TrustPolicy(MemoryTrustStore()) { protocol != OpenWrtProtocol.Ssh || it.fingerprint == key }
        val found = mutableListOf<String>()
        val slow = mutableListOf<String>()
        val pages = mutableListOf<String>()
        val directory = File("build/sweep").also { it.mkdirs() }
        val name = "$prefix-${view.name}" + (if (protocol == OpenWrtProtocol.Ssh) "" else "-${protocol.name}") + (if (limit == Int.MAX_VALUE) "" else "-first$limit")
        val progress = directory.resolve("$name.progress").also { it.writeText("") }
        val connecting = System.currentTimeMillis()
        val session = runBlocking { connectOpenWrtWithForms(login, trust) }
        progress.appendText("connected: ${System.currentTimeMillis() - connecting} ms\n")
        try {
            val storage = MemoryStorage()
            val tab = RouterTab(session, host, bundledPacks().values, storage, TableLayouts(storage))
            val opening = System.currentTimeMillis()
            setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
            waitUntil(timeoutMillis = 60_000) { tab.menu != null }
            if (view != MenuView.WinBox) {
                onNodeWithTag("view").performClick()
                onNodeWithText(label).performClick()
                waitUntil(timeoutMillis = 180_000) { tab.view == view && tab.menu != null }
            }
            progress.appendText("menu of the ${view.name} view: ${System.currentTimeMillis() - opening} ms\n")
            assertTrue(tab.view == view, "the router has no $label")
            val entries = leaves(stable(tab), emptyList()).take(limit)
            for ((trail, entry) in entries) {
                val where = trail.joinToString(" > ")
                val started = System.currentTimeMillis()
                when {
                    entry.page != null -> pages += "$where: ${entry.page}"
                    entry.window == "terminal" -> Unit
                    else -> try {
                        window(tab, entry, where, found, slow)
                    } catch (e: Exception) {
                        found += "$where | - | the sweep stopped: $e"
                    }
                }
                progress.appendText("$where: ${System.currentTimeMillis() - started} ms, ${found.size} findings\n")
                for (window in tab.workspace.windows.toList()) tab.workspace.close(window)
            }
        } finally {
            session.close()
        }
        val report = directory.resolve("$name.txt")
        report.writeText(
            (listOf("findings: ${found.size}") + found + listOf("slow: ${slow.size}") + slow + listOf("pages in the browser: ${pages.size}") + pages).joinToString("\n"),
        )
        assertTrue(found.isEmpty(), "$prefix $label:\n" + found.joinToString("\n"))
    }

    private fun SkikoComposeUiTest.stable(tab: RouterTab): List<MenuEntry> {
        var last = ""
        repeat(40) {
            val now = leaves(tab.menu.orEmpty(), emptyList()).joinToString { it.first.joinToString(">") }
            if (now == last) return tab.menu.orEmpty()
            last = now
            Thread.sleep(2000)
            waitForIdle()
        }
        return tab.menu.orEmpty()
    }

    private fun leaves(entries: List<MenuEntry>, trail: List<String>): List<Pair<List<String>, MenuEntry>> = entries.flatMap { entry ->
        val here = trail + entry.title
        (if (entry.path != null || entry.window != null || entry.page != null) listOf(here to entry) else emptyList()) + leaves(entry.items, here)
    }

    private fun SkikoComposeUiTest.problems(): List<String> = onAllNodesWithTag("problem").fetchSemanticsNodes().map { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
    }

    private fun SkikoComposeUiTest.settle(): Boolean {
        val done = try {
            waitUntil(timeoutMillis = 30_000) { onAllNodes(hasText("Loading…")).fetchSemanticsNodes().isEmpty() || onAllNodesWithTag("problem").fetchSemanticsNodes().isNotEmpty() }
            true
        } catch (e: Exception) {
            false
        }
        Thread.sleep(500)
        waitForIdle()
        return done
    }

    private fun SkikoComposeUiTest.record(found: MutableList<String>, slow: MutableList<String>, where: String, stage: String) {
        val started = System.currentTimeMillis()
        if (!settle()) found += "$where | $stage | still Loading after 30 s"
        val took = System.currentTimeMillis() - started
        if (took > 3_000) slow += "$where | $stage | Loading for ${took / 1000} s"
        for (text in problems().distinct()) found += "$where | $stage | $text"
    }

    private fun <T> SkikoComposeUiTest.offTest(slow: MutableList<String>, what: String, call: suspend () -> T): T {
        val started = System.currentTimeMillis()
        val answer = CoroutineScope(Dispatchers.Default).async { call() }
        try {
            waitUntil(timeoutMillis = 30_000) { answer.isCompleted }
        } finally {
            val took = System.currentTimeMillis() - started
            if (took > 10_000) slow += "$what took ${took / 1000} s"
            if (!answer.isCompleted) answer.cancel()
        }
        return runBlocking { answer.await() }
    }

    private fun SkikoComposeUiTest.window(tab: RouterTab, entry: MenuEntry, where: String, found: MutableList<String>, slow: MutableList<String>) {
        tab.open(entry)
        val opened = tab.workspace.windows.lastOrNull() ?: return
        val spec = tab.specs[opened.id]
        record(found, slow, where, "window")
        if (spec == null) return
        for (index in spec.tabs.indices.toList()) {
            val shown = spec.tabs.getOrNull(index) ?: break
            val name = "$where [${shown.title}]"
            if (index != spec.selected) {
                spec.selected = index
                record(found, slow, name, "tab")
            }
            val schema = try {
                offTest(slow, "$name describe") { tab.session.describe(shown.path) }
            } catch (e: DeviceException) {
                found += "$name | describe | ${e.message}"
                continue
            } catch (e: ComposeTimeoutException) {
                found += "$name | describe | no answer in 30 s"
                continue
            }
            if (schema.kind != NodeKind.List) continue
            val row = try {
                offTest(slow, "$name rows") { tab.session.watch(shown.path).filterIsInstance<ListEvent.Snapshot>().first().items.firstOrNull() }
            } catch (e: DeviceException) {
                found += "$name | rows | ${e.message}"
                null
            } catch (e: ComposeTimeoutException) {
                found += "$name | rows | no first snapshot in 30 s"
                null
            }
            if (row != null) {
                val before = tab.workspace.windows.toSet()
                openRow(tab, shown, row)
                record(found, slow, name, "row ${row.id}")
                tab.workspace.windows.filter { it !in before }.forEach(tab.workspace::close)
            }
            if ("add" in schema.commands) {
                val before = tab.workspace.windows.toSet()
                tab.openItem(shown.title, shown.path, null)
                record(found, slow, name, "new")
                tab.workspace.windows.filter { it !in before }.forEach(tab.workspace::close)
            }
        }
    }

    private fun openRow(tab: RouterTab, shown: WindowTab, row: Item) {
        val owner = shown.shown?.owner
        val target = shown.shown?.open?.let { open -> row[open.by]?.let(open.paths::get) }
        when {
            owner != null -> row[owner.by]?.let { tab.openItem(owner.title, MenuPath.parse(owner.path), it) }
            target != null -> tab.openItem(shown.title, MenuPath.parse(target), null, row["name"])
            else -> tab.openItem(shown.title, shown.path, row.id)
        }
    }
}
