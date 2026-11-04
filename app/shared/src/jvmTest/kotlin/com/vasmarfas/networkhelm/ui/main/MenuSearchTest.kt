package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MenuSearchTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private fun directory(path: String, vararg children: String) = path to MenuSchema(MenuPath.parse(path), NodeKind.Directory, children.toList(), emptyList(), emptyList())

    @Test
    fun theSearchOfTheMenuFindsTheTabsOfWindows() = runDesktopComposeUiTest(1100, 700) {
        Locale.setDefault(Locale.ENGLISH)
        val router = FakeRouter(schemas = mapOf(directory("ip", "firewall"), directory("ip/firewall", "filter", "nat", "mangle", "raw", "address-list")))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 5_000) { tab.menu?.isNotEmpty() == true }
        onNodeWithTag("menu-search").performTextInput("nat")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("IP > Firewall > NAT")).fetchSemanticsNodes().isNotEmpty() }
        picture("menu-search")
        onNodeWithTag("menu-search").performKeyInput { pressKey(Key.Enter) }
        waitUntil(timeoutMillis = 5_000) { tab.workspace.windows.isNotEmpty() }
        val spec = tab.specs.getValue(tab.workspace.windows.single().id)
        assertEquals("Firewall" to "ip/firewall/nat", spec.title to spec.tabs[spec.selected].path.toString())

        spec.selected = 0
        onNodeWithTag("menu-search").performKeyInput { pressKey(Key.Enter) }
        waitUntil(timeoutMillis = 5_000) { spec.tabs[spec.selected].path.toString() == "ip/firewall/nat" }
        assertEquals(1, tab.workspace.windows.size)

        onNodeWithTag("menu-search").performTextReplacement("firewall nat")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("IP > Firewall > NAT")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("IP > Firewall > Mangle")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aPathOfTheConsoleOpensItsWindow() = runDesktopComposeUiTest(1100, 700) {
        Locale.setDefault(Locale.ENGLISH)
        val router = FakeRouter(schemas = mapOf(directory("ip", "firewall"), directory("ip/firewall", "filter", "nat", "connection")))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 5_000) { tab.menu?.isNotEmpty() == true }
        fun row(text: String) = onAllNodes(hasText(text) and !hasSetTextAction()).fetchSemanticsNodes()
        onNodeWithTag("menu-search").performTextInput("/ip firewall")
        waitForIdle()
        assertTrue(row("/ip/firewall").isEmpty(), "a directory opens no window")
        onNodeWithTag("menu-search").performTextClearance()
        onNodeWithTag("menu-search").performTextInput("/ip firewall connection tracking")
        waitUntil(timeoutMillis = 5_000) { row("/ip/firewall/connection/tracking").isNotEmpty() }
        onNodeWithTag("menu-search").performKeyInput { pressKey(Key.Enter) }
        waitUntil(timeoutMillis = 5_000) { tab.workspace.windows.isNotEmpty() }
        val spec = tab.specs.getValue(tab.workspace.windows.single().id)
        assertEquals("ip/firewall/connection/tracking", spec.tabs[spec.selected].path.toString())
    }
}
