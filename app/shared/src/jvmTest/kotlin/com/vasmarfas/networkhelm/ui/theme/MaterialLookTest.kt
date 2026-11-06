package com.vasmarfas.networkhelm.ui.theme

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.App
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.Shell
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.app.Services
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MaterialLookTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun onlyTheMaterialThemeTurnsTheMaterialDrawingOn() = runDesktopComposeUiTest(200, 100) {
        val seen = mutableMapOf<String, Boolean>()
        setContent {
            for (theme in BundledThemes) NetworkHelmTheme(theme, dark = false) { seen[theme.name] = Theme.material }
        }
        waitForIdle()
        assertEquals(mapOf("Modern" to false, "Legacy" to false, "Material" to true), seen)
    }

    private fun SkikoComposeUiTest.materialShell(dark: Boolean, shots: String) {
        Locale.setDefault(Locale.ENGLISH)
        fun directory(path: String, vararg children: String) = path to MenuSchema(MenuPath.parse(path), NodeKind.Directory, children.toList(), emptyList(), emptyList())
        val addresses = listOf(Item("*1", mapOf("address" to "192.0.2.1/24", "network" to "192.0.2.0", "interface" to "ether1")))
        val router = FakeRouter(mapOf("ip/address" to addresses), schemas = mapOf(directory("", "interface", "ip"), directory("ip", "address", "arp")))
        val shell = Shell(Services(MemoryStorage(), MemoryTrustStore(), neighbors = null), TestScope())
        runBlocking { shell.settings.change { it.copy(theme = "Material", dark = dark) } }
        shell.add(router, "192.0.2.1")
        setContent { App(shell) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("IP")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("IP").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Addresses")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Addresses").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("192.0.2.1/24", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        picture("$shots-list")
        shell.routers.first().openItem("Address", MenuPath.parse("ip/address"), "*1")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("OK")).fetchSemanticsNodes().isNotEmpty() }
        picture("$shots-dialog")
        onNodeWithText("IP").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("ARP")).fetchSemanticsNodes().isNotEmpty() }
        picture("$shots-main")
    }

    @Test
    fun theShellWearsTheLightLookOfMaterial() = runDesktopComposeUiTest(1100, 700) { materialShell(false, "material") }

    @Test
    fun theShellWearsTheDarkLookOfMaterial() = runDesktopComposeUiTest(1100, 700) { materialShell(true, "material-dark") }
}
