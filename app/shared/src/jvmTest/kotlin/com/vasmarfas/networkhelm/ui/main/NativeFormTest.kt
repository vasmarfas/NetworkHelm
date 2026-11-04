package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.Pack
import com.vasmarfas.networkhelm.packs.PackForm
import com.vasmarfas.networkhelm.packs.PackFormTab
import com.vasmarfas.networkhelm.packs.PackNative
import com.vasmarfas.networkhelm.packs.PackPath
import com.vasmarfas.networkhelm.packs.PackTab
import com.vasmarfas.networkhelm.packs.PackWindow
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NativeFormTest {
    private val path = "native/120/6"

    private val names = listOf("mode", "port", "mac-address", "ip-address", "netmask")

    private val schema = MenuSchema(MenuPath.parse(path), NodeKind.Settings, emptyList(), emptyList(), names.map { Argument(it, ValueType.Text(), readOnly = true) })

    private val pack = Pack(
        menu = listOf("Quick Set"),
        window = PackWindow("Quick Set", listOf(PackTab("Quick Set", path))),
        paths = mapOf(
            path to PackPath(
                readonly = names,
                form = path,
                labels = mapOf("mac-address" to "MAC Address", "ip-address" to "IP Address"),
                native = PackNative(listOf(120, 6), emptyList(), single = true),
            ),
        ),
        forms = mapOf(path to PackForm(tabs = listOf(PackFormTab("Ethernet", names)))),
        prio = 10,
    )

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun aWindowOfOneRecordOfTheObjectModelIsAFormOfLabelsAndValues() = runDesktopComposeUiTest(1100, 700) {
        Locale.setDefault(Locale.ENGLISH)
        val storage = MemoryStorage()
        val record = Item("0", mapOf("mode" to "router", "port" to "ether1", "mac-address" to "02:5A:3C:91:0F:E7", "ip-address" to "192.0.2.1", "netmask" to "255.255.255.0 (/24)"))
        val router = FakeRouter(rows = mapOf(path to listOf(record)), schemas = mapOf(path to schema), made = listOf(pack))
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Quick Set")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Quick Set").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("MAC Address")).fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("02:5A:3C:91:0F:E7")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Close").assertExists()
        picture("native-form")
    }
}
