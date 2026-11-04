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
class UpnpWindowsTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun portMappingsShowAsDynamicDstNatRules() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val properties = listOf("chain", "action", "protocol", "src-address", "dst-port", "to-addresses", "to-ports", "comment", "disabled", "dynamic", "lease")
        val schema = MenuSchema(MenuPath.parse("ip/firewall/nat"), NodeKind.List, emptyList(), listOf("print"), properties.map { Argument(it, ValueType.Text(), readOnly = true) })
        val mappings = listOf(
            Item("0", mapOf("chain" to "dstnat", "action" to "dst-nat", "protocol" to "tcp", "dst-port" to "50999", "to-addresses" to "192.168.99.10", "to-ports" to "22", "comment" to "rd-test", "disabled" to "false", "dynamic" to "true", "lease" to "10m")),
            Item("1", mapOf("chain" to "dstnat", "action" to "dst-nat", "protocol" to "udp", "dst-port" to "51820", "to-addresses" to "192.168.99.20", "to-ports" to "51820", "comment" to "rd-test wg", "disabled" to "true", "dynamic" to "true")),
        )
        val router = FakeRouter(mapOf("ip/firewall/nat" to mappings), schemas = mapOf("ip/firewall/nat" to schema), platform = Platform.Upnp, policies = setOf("read"))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Firewall", "ip/firewall/nat"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd-test wg")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("Chain", "Dst. Port", "To Addresses", "To Ports")) onAllNodes(hasText(column)).onFirst().assertExists()
        assertTrue(onAllNodes(hasText("New")).fetchSemanticsNodes().isEmpty())
        picture("upnp-nat")
    }
}
