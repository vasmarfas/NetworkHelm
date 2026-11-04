package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SnmpWindowsTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun interfacesShowTheirRatesAndNothingToWrite() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val schema = MenuSchema(
            MenuPath.parse("interface"), NodeKind.List, emptyList(), listOf("print", "monitor-traffic"),
            listOf("name", "type", "mtu", "mac-address", "speed", "comment").map { Argument(it, ValueType.Text(), readOnly = true) },
        )
        val interfaces = listOf(
            Item("2", mapOf("name" to "eth0", "type" to "ether", "mtu" to "1500", "mac-address" to "00:00:5E:00:53:19", "speed" to "1Gbps", "running" to "true", "disabled" to "false")),
            Item("4", mapOf("name" to "br-lan", "type" to "ether", "mtu" to "1500", "mac-address" to "00:00:5E:00:53:18", "speed" to "", "running" to "false", "disabled" to "true")),
        )
        val traffic = (0..1).flatMap { round ->
            listOf(
                Item("", mapOf("name" to "eth0", "rx-bits-per-second" to "1000000", "tx-bits-per-second" to "2000", "rx-packets-per-second" to "90", "tx-packets-per-second" to "4", ".section" to "$round")),
                Item("", mapOf("name" to "br-lan", "rx-bits-per-second" to "0", "tx-bits-per-second" to "0", "rx-packets-per-second" to "0", "tx-packets-per-second" to "0", ".section" to "$round")),
            )
        }
        val router = FakeRouter(
            mapOf("interface" to interfaces), schemas = mapOf("interface" to schema), output = mapOf("interface/monitor-traffic" to traffic),
            platform = Platform.Snmp, policies = setOf("read"),
        )
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.82", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Interfaces", "interface"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("1000.0 kbps")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("interface/monitor-traffic" to mapOf("interface" to "eth0,br-lan"), router.ran.first())
        assertTrue(onAllNodes(hasText("New")).fetchSemanticsNodes().isEmpty())
        picture("snmp-interfaces")
    }
}
