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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KeeneticWindowsTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun hotspotHostsShowWithTheFieldsOfRci() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val properties = listOf("name", "hostname", "ip", "mac", "interface.name", "active", "rxbytes", "txbytes", "uptime")
        val schema = MenuSchema(MenuPath.parse("ip/hotspot"), NodeKind.List, emptyList(), listOf("print"), properties.map { Argument(it, ValueType.Text(), readOnly = true) })
        val hosts = listOf(
            Item("0", mapOf("name" to "rd-test", "hostname" to "rd-test-phone", "ip" to "192.168.1.33", "mac" to "aa:bb:cc:00:00:01", "interface.name" to "Home", "active" to "true", "rxbytes" to "123456", "txbytes" to "654321", "uptime" to "1h2m5s")),
            Item("1", mapOf("name" to "rd-test tv", "ip" to "192.168.1.40", "mac" to "aa:bb:cc:00:00:02", "interface.name" to "Home", "active" to "false")),
        )
        val router = FakeRouter(mapOf("ip/hotspot" to hosts), schemas = mapOf("ip/hotspot" to schema), platform = Platform.Keenetic, policies = setOf("read"))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Hotspot", "ip/hotspot"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd-test tv")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("Address", "MAC Address", "Host Name", "Interface", "Rx Bytes")) onAllNodes(hasText(column)).onFirst().assertExists()
        assertTrue(onAllNodes(hasText("New")).fetchSemanticsNodes().isEmpty())
        picture("keenetic-hotspot")
    }
}
