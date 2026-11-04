package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import com.vasmarfas.networkhelm.vyos.VyosLogin
import com.vasmarfas.networkhelm.vyos.connectVyos
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class VyosScreenLabTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun aRouteIsMadeInTheDialogOfAnEmptyList() = runDesktopComposeUiTest(1280, 800) {
        val file = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("lab.properties is missing", file != null)
        val lab = Properties().apply { file?.inputStream()?.use(::load) }
        assumeTrue("vyos.host is empty", !lab.getProperty("vyos.host").isNullOrEmpty())
        Locale.setDefault(Locale.ENGLISH)
        val login = VyosLogin(lab.getProperty("vyos.host"), lab.getProperty("vyos.key"), lab.getProperty("vyos.port")?.toInt() ?: 443)
        val session = runBlocking { connectVyos(login, TrustPolicy(MemoryTrustStore()) { true }) }
        val routes = MenuPath.parse("protocols/static/route")
        try {
            val storage = MemoryStorage()
            val tab = RouterTab(session, login.host, bundledPacks().values, storage, TableLayouts(storage))
            setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
            tab.open(MenuEntry("Routes", routes.toString()))
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("New")).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(hasText("New")).onFirst().performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("field-blackhole")).fetchSemanticsNodes().isNotEmpty() }
            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("field-.name"))).performTextInput("192.0.2.0/24")
            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("field-description"))).performTextInput("rd-test")
            onNode(isToggleable() and hasAnyAncestor(hasTestTag("field-blackhole"))).performClick()
            picture("vyos-route-dialog")
            onNodeWithText("OK").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("OK")).fetchSemanticsNodes().isEmpty() }
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("192.0.2.0/24")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals("true", runBlocking { session.get(routes, "192.0.2.0/24") }?.get("blackhole"))
            picture("vyos-routes")
        } finally {
            runBlocking { if (session.get(routes, "192.0.2.0/24") != null) session.remove(routes, listOf("192.0.2.0/24")) }
            session.close()
        }
    }

    @Test
    fun theRoutingTableAndTheStateOfInterfacesAreTabs() = runDesktopComposeUiTest(1280, 800) {
        val file = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("lab.properties is missing", file != null)
        val lab = Properties().apply { file?.inputStream()?.use(::load) }
        assumeTrue("vyos.host is empty", !lab.getProperty("vyos.host").isNullOrEmpty())
        Locale.setDefault(Locale.ENGLISH)
        val login = VyosLogin(lab.getProperty("vyos.host"), lab.getProperty("vyos.key"), lab.getProperty("vyos.port")?.toInt() ?: 443)
        val session = runBlocking { connectVyos(login, TrustPolicy(MemoryTrustStore()) { true }) }
        try {
            val storage = MemoryStorage()
            val tab = RouterTab(session, login.host, bundledPacks().values, storage, TableLayouts(storage))
            setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
            tab.open(MenuEntry("Routes", "protocols/static/route"))
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Routing Table")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Routing Table").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("connected")).fetchSemanticsNodes().isNotEmpty() }
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("GiB", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("Load ", substring = true)).assertExists()
            onNode(hasText("Uptime\u00A0", substring = true)).assertExists()
            picture("vyos-routing-table")
            tab.workspace.windows.toList().forEach(tab.workspace::close)
            tab.open(MenuEntry("Interfaces", "interfaces/ethernet"))
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Status")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Status").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("u/u")).fetchSemanticsNodes().isNotEmpty() }
            picture("vyos-interfaces-status")
        } finally {
            session.close()
        }
    }
}
