package com.vasmarfas.networkhelm.ui.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.Pack
import com.vasmarfas.networkhelm.packs.PackPath
import com.vasmarfas.networkhelm.packs.PackTab
import com.vasmarfas.networkhelm.packs.PackWindow
import com.vasmarfas.networkhelm.packs.bundledMenu
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ui.kit.LocalCompact
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ControllerWindowsTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private fun item(id: String, vararg values: Pair<String, String>) = Item(id, mapOf(*values))

    private fun directory(path: String, vararg children: String) = path to MenuSchema(MenuPath.parse(path), NodeKind.Directory, children.toList(), emptyList(), emptyList())

    private fun list(path: String) = path to MenuSchema(MenuPath.parse(path), NodeKind.List, emptyList(), emptyList(), emptyList())

    private fun pack(menu: List<String>, title: String, vararg tabs: Pair<String, String>) = Pack(
        menu = menu,
        window = PackWindow(title, tabs.map { (name, path) -> PackTab(name, path) }),
        paths = tabs.associate { (_, path) -> path to PackPath(columns = listOf("device")) },
    )

    private val schemas = mapOf(
        directory("", "cmr"),
        directory("cmr", "device", "alert", "layout"),
        list("cmr/device"),
        list("cmr/alert"),
        list("cmr/layout"),
    )

    private val described = listOf(
        pack(listOf("Controller", "Devices"), "Devices", "Devices" to "cmr/device", "Resources" to "native/901/1"),
        pack(listOf("Controller", "Ports"), "Ports", "Ports" to "native/901/2"),
        pack(listOf("Controller", "WiFi"), "WiFi", "Interfaces" to "native/901/3", "Clients" to "native/901/4"),
        pack(listOf("Controller", "Traffic"), "Traffic", "Applications" to "native/901/5"),
    )

    private val rows = mapOf(
        "cmr/device" to listOf(
            item("*1", "identity" to "core", "address" to "192.0.2.1", "pairing-status" to "paired", "connected" to "true", "upgrade-available" to "true", "available-version" to "7.25"),
            item("*2", "identity" to "ap", "address" to "192.0.2.2", "pairing-status" to "paired", "connected" to "false"),
            item("*3", "identity" to "new", "address" to "192.0.2.3", "pairing-status" to "pending"),
        ),
        "cmr/alert" to listOf(
            item("*1", "name" to "cpu>95%", "devices-on" to "1", "severity" to "high"),
            item("*2", "name" to "memory>80%", "devices-on" to "0", "severity" to "high"),
        ),
        "native/901/1" to listOf(
            item("*1", "device" to "core", "cpu" to "41%", "memory" to "15%", "hdd" to "58%"),
            item("*2", "device" to "ap", "cpu" to "5%", "memory" to "60%", "hdd" to "20%"),
        ),
        "native/901/2" to listOf(
            item("#0", "device" to "core", "interface" to "ether1", "bandwidth-rx" to "2000000", "bandwidth-tx" to "500000"),
            item("#1", "device" to "core", "interface" to "ether2", "bandwidth-rx" to "1000000", "bandwidth-tx" to "250000"),
        ),
        "native/901/3" to listOf(
            item("#0", "device" to "ap", "interface" to "wifi1", "channel" to "5180/ax", "running" to "true", "bandwidth-rx" to "100000", "bandwidth-tx" to "50000"),
            item("#1", "device" to "ap", "interface" to "wifi2", "channel" to "2412/ax", "running" to "false"),
            item("#2", "device" to "ap", "interface" to "wifi3", "channel" to "5180/ax", "running" to "true"),
        ),
        "native/901/4" to listOf(
            item("*1", "identity" to "phone", "band" to "5ghz-ax"),
            item("*2", "identity" to "laptop", "band" to "5ghz-ax"),
            item("*3", "identity" to "bulb", "band" to "2ghz-n"),
        ),
        "native/901/5" to listOf(
            item("*1", "name" to "video", "last-hour-in" to "3145728", "last-hour-out" to "1024"),
            item("*2", "name" to "dns", "last-hour-in" to "2048", "last-hour-out" to "2048"),
        ),
        "cmr/layout" to listOf(item("*1", "name" to "main"), item("*2", "name" to "floor")),
        "cmr/layout/node" to listOf(
            item("*1", "name" to "core", "layout" to "main", "device" to "core@192.0.2.1", "x" to "100", "y" to "4294967261"),
            item("*2", "name" to "ap", "layout" to "main", "device" to "ap@192.0.2.2", "x" to "4294967272", "y" to "20"),
            item("*3", "name" to "upstairs", "layout" to "main", "target-layout" to "floor", "x" to "0", "y" to "0"),
            item("*4", "name" to "switch", "layout" to "floor", "device" to "sw@192.0.2.9", "x" to "5", "y" to "5"),
            item("*5", "name" to "ap2", "layout" to "floor", "device" to "ap2@192.0.2.10", "x" to "105", "y" to "5"),
        ),
        "cmr/layout/link" to listOf(
            item("*1", "layout" to "main", "node1" to "core", "node2" to "ap"),
            item("*2", "layout" to "main", "node1" to "core", "node2" to "upstairs"),
        ),
    )

    private fun tab() = MemoryStorage().let { storage ->
        RouterTab(FakeRouter(rows, schemas = schemas, made = described, packages = setOf("routeros", "cmr")), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
    }

    private fun SkikoComposeUiTest.openFromTheController(tab: RouterTab, entry: String) {
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Controller")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Controller").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(entry)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(entry).performClick()
    }

    @Test
    fun theDashboardShowsWhatTheControllerKnowsOfItsFleet() = runDesktopComposeUiTest(1500, 950) {
        Locale.setDefault(Locale.ENGLISH)
        openFromTheController(tab(), "Dashboard")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("dashboard-application")).fetchSemanticsNodes().size == 2 }
        onNodeWithTag("dashboard-online").assertTextEquals("1/3")
        onNodeWithTag("dashboard-paired").assertTextEquals("2/3")
        onAllNodesWithTag("dashboard-resource").assertCountEquals(2)
        onAllNodesWithTag("dashboard-alert").assertCountEquals(1)
        onNode(hasTestTag("dashboard-alert") and hasText("cpu>95%", substring = true)).assertTextContains("high", substring = true)
        onAllNodesWithTag("dashboard-update").assertCountEquals(1)
        onNode(hasTestTag("dashboard-update") and hasText("7.25", substring = true)).assertExists()
        onNode(hasTestTag("dashboard-application") and hasText("3.0 MiB", substring = true)).assertExists()
        onNodeWithTag("dashboard-ap-total").assertTextEquals("3")
        onNodeWithTag("dashboard-ap-running").assertTextEquals("2/3")
        onAllNodesWithTag("dashboard-ap-channel").assertCountEquals(2)
        onNodeWithTag("dashboard-station-total").assertTextEquals("3")
        onAllNodesWithTag("dashboard-station-band").assertCountEquals(2)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("dashboard-rates") and hasText("Now 3.1 Mbps", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasTestTag("dashboard-rates") and hasText("Now 3.1 Mbps · average 3.1 Mbps · maximum 3.1 Mbps")).assertExists()
        onNode(hasTestTag("dashboard-rates") and hasText("Now 800 kbps · average 800 kbps · maximum 800 kbps")).assertExists()
        picture("dashboard")
    }

    @Test
    fun theDashboardLeavesOutThePanelsOfWhatTheRouterDoesNotHave() = runDesktopComposeUiTest(1500, 950) {
        Locale.setDefault(Locale.ENGLISH)
        val storage = MemoryStorage()
        val alone = listOf(described.first())
        val tab = RouterTab(FakeRouter(rows, schemas = schemas, made = alone, packages = setOf("routeros", "cmr")), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        openFromTheController(tab, "Dashboard")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("dashboard-resource")).fetchSemanticsNodes().size == 2 }
        onAllNodes(hasText("Stations")).assertCountEquals(0)
        onAllNodes(hasText("Access Points")).assertCountEquals(0)
        onAllNodes(hasTestTag("dashboard-application")).assertCountEquals(0)
    }

    @Test
    fun theTopologyDrawsTheNodesOfALayoutWhereTheirCoordinatesPutThem() = runDesktopComposeUiTest(1500, 950) {
        Locale.setDefault(Locale.ENGLISH)
        openFromTheController(tab(), "Topology")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("topology-node")).fetchSemanticsNodes().size == 3 }
        fun at(name: String) = onNode(hasTestTag("topology-node") and hasText(name, substring = true)).getBoundsInRoot()
        val core = at("core@192.0.2.1")
        val ap = at("ap@192.0.2.2")
        val upstairs = at("floor")
        assertTrue(ap.left < upstairs.left && upstairs.left < core.left, "x grows to the right and -24 lies left of 0")
        assertTrue(core.top < upstairs.top && upstairs.top < ap.top, "y grows downwards and -35 lies above 0")
        picture("topology")
        onNode(hasTestTag("topology-node") and hasText("floor", substring = true)).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("topology-node")).fetchSemanticsNodes().size == 2 }
        onNode(hasTestTag("topology-layout") and hasAnyDescendant(hasText("floor"))).assertExists()
        fun gap() = at("ap2@192.0.2.10").left - at("sw@192.0.2.9").left
        val before = gap()
        onNodeWithTag("topology").performMouseInput {
            moveTo(center)
            scroll(-3f)
        }
        waitForIdle()
        assertTrue(gap() > before, "the wheel zooms the layout that was chosen last, not the first one")
    }

    @Test
    fun theTwoWindowsFitAPhone() = runDesktopComposeUiTest(400, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val tab = tab()
        setContent { NetworkHelmTheme(dark = true) { CompositionLocalProvider(LocalCompact provides true) { MainScreen(tab, onDisconnect = {}) } } }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Controller")).fetchSemanticsNodes().isNotEmpty() }
        tab.open(MenuEntry("Dashboard", "cmr/device", window = "dashboard"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("dashboard-application")).fetchSemanticsNodes().size == 2 }
        picture("dashboard-phone")
        tab.open(MenuEntry("Topology", "cmr/layout", window = "topology"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("topology-node")).fetchSemanticsNodes().size == 3 }
        picture("topology-phone")
    }

    @Test
    fun theControllerOffersItsTwoGraphicWindowsWhereTheRouterHasTheirData() {
        val storage = MemoryStorage()
        fun items(vararg children: String): List<MenuEntryView> {
            val shape = mapOf(directory("", "cmr"), directory("cmr", *children)) + children.associate { list("cmr/$it") }
            val tab = RouterTab(FakeRouter(schemas = shape, packages = setOf("routeros", "cmr")), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
            runBlocking { tab.loadMenu(bundledMenu()) }
            return tab.menu.orEmpty().first { it.title == "Controller" }.items.map { MenuEntryView(it.title, it.window) }
        }
        val both = items("device", "layout")
        assertEquals("dashboard", both.single { it.title == "Dashboard" }.window)
        assertEquals("topology", both.single { it.title == "Topology" }.window)
        assertEquals("Dashboard", both.first().title)
        val devicesOnly = items("device")
        assertEquals("dashboard", devicesOnly.single { it.title == "Dashboard" }.window)
        assertTrue(devicesOnly.none { it.title == "Topology" })
        assertNull(items("alert").firstOrNull { it.title == "Dashboard" })
    }

    private data class MenuEntryView(val title: String, val window: String?)
}
