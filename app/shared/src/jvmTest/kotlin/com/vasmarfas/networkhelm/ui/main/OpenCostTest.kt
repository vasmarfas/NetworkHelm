package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OpenCostTest {

    private class Counting(private val router: FakeRouter) : DeviceSession by router {
        val described = mutableListOf<String>()
        val outlined = mutableListOf<String>()
        val watched = mutableListOf<Pair<String, Filter?>>()

        override suspend fun describe(path: MenuPath): MenuSchema = router.describe(path).also { described += path.toString() }

        override suspend fun outline(path: MenuPath): MenuSchema = router.describe(path).also { outlined += path.toString() }

        override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> {
            watched += path.toString() to filter
            return router.watch(path, filter, properties)
        }
    }

    private val interfaces = listOf(Item("*1", mapOf("name" to "ether1", "type" to "ether", "running" to "true")))

    @Test
    fun aWindowWithTabsDescribesOnlyTheTabItShows() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val router = Counting(FakeRouter(mapOf("interface" to interfaces)))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 10_000) { tab.menu != null }
        val menuDescribed = router.described.toList()
        assertTrue(menuDescribed.isEmpty(), "the menu is built from outlines: $menuDescribed")
        tab.open(MenuEntry("Interfaces", "interface"))
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("1 item")).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        assertEquals(setOf("interface"), router.described.toSet(), "the other tabs and the kinds of New are outlined: ${router.described}")
        assertTrue(router.outlined.size > 5, "the tabs are checked: ${router.outlined}")
    }

    @Test
    fun theDialogOfAnElementWatchesItOnce() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val router = Counting(FakeRouter(mapOf("ip/address" to listOf(Item("*1", mapOf("address" to "192.0.2.1/24", "interface" to "ether1"))))))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 10_000) { tab.menu != null }
        tab.openItem("Addresses", MenuPath.parse("ip/address"), "*1")
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("OK")).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        assertEquals(1, router.watched.count { (_, filter) -> filter == Filter.Equals(".id", "*1") }, router.watched.toString())
    }

    @Test
    fun aWindowOpenedAgainDrawsItsLastRowsWhileTheRouterIsSilent() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val router = FakeRouter(mapOf("ip/address" to listOf(Item("*1", mapOf("address" to "192.0.2.1/24", "interface" to "ether1")))))
        var silent = false
        val session = object : DeviceSession by router {
            override fun watch(path: MenuPath, filter: Filter?, properties: List<String>?): Flow<ListEvent> =
                if (silent) flow { awaitCancellation() } else router.watch(path, filter, properties)
        }
        val storage = MemoryStorage()
        val tab = RouterTab(session, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 10_000) { tab.menu != null }
        tab.open(MenuEntry("Addresses", "ip/address"))
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("192.0.2.1/24")).fetchSemanticsNodes().isNotEmpty() }
        silent = true
        runOnIdle { tab.workspace.close(tab.workspace.windows.last()) }
        waitForIdle()
        tab.open(MenuEntry("Addresses", "ip/address"))
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("192.0.2.1/24")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Loading…")).fetchSemanticsNodes().isEmpty())
    }
}
