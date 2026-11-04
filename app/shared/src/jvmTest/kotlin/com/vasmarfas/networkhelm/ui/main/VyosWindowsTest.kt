package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class VyosWindowsTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun forwardRulesShowWithTheirTabs() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val path = "firewall/ipv4/forward/filter/rule"
        val properties = listOf(".name", "disabled", "action", "protocol", "source address", "destination address", "destination port", "state", "description")
        val schema = MenuSchema(MenuPath.parse(path), NodeKind.List, emptyList(), listOf("print", "add", "set", "remove"), properties.map { Argument(it, if (it == "disabled") ValueType.YesNo else ValueType.Text()) })
        val rules = listOf(
            Item("10", mapOf(".name" to "10", "disabled" to "false", "action" to "accept", "state" to "established,related", "description" to "rd-test established")),
            Item("20", mapOf(".name" to "20", "disabled" to "true", "action" to "drop", "protocol" to "tcp", "source address" to "203.0.113.0/24", "destination port" to "22", "description" to "rd-test ssh")),
        )
        val router = FakeRouter(mapOf(path to rules), schemas = mapOf(path to schema), platform = Platform.Vyos)
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Firewall", path))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd-test ssh")).fetchSemanticsNodes().isNotEmpty() }
        for (text in listOf("Forward", "Address Groups", "Src. Address", "Dst. Port", "New")) onAllNodes(hasText(text)).onFirst().assertExists()
        picture("vyos-firewall")
    }
}
