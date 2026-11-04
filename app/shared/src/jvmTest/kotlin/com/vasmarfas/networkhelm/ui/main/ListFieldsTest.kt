package com.vasmarfas.networkhelm.ui.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.center
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeClipboard
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ui.kit.HintHost
import com.vasmarfas.networkhelm.ui.kit.readText
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
class ListFieldsTest {

    private val advertised = "10M-baseT-half,10M-baseT-full,100M-baseT-half,100M-baseT-full,1G-baseT-half,1G-baseT-full"

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun aListIsRecognisedByTheSchemaOrByTheShapeOfAMonitorValue() {
        assertEquals(listOf("10M-baseT-half", "1G-baseT-full"), elementsOf("advertising", "10M-baseT-half,1G-baseT-full", null))
        assertEquals(listOf("192.0.2.1", "192.0.2.2"), elementsOf("dns-server", "192.0.2.1,192.0.2.2", ValueType.Many(ValueType.Ip4)))
        assertEquals(listOf("ether1", "ether2"), elementsOf("tagged", "ether1,ether2", ValueType.Enumeration(multiple = true)))
        assertNull(elementsOf("comment", "web,ssh", null))
        assertNull(elementsOf("name", "web,ssh", ValueType.Text()))
        assertNull(elementsOf("limit", "10/1m,5:packet", ValueType.Rate(listOf("packet"))))
        assertNull(elementsOf("protocol", "tcp,udp", ValueType.Enumeration()))
        assertNull(elementsOf("status", "link ok,slave", null))
        assertNull(elementsOf("status", "link-ok", null))
        assertNull(elementsOf("status", "link-ok,", null))
        assertNull(elementsOf("rate", "1 / 2", null))
    }

    @Test
    fun theMediaOfAPortReadAsWordsAndAddressesRangesAndNamesStay() {
        assertEquals("10M baseT half", readable("10M-baseT-half", emptyMap()))
        assertEquals("1G baseT full", readable("1G-baseT-full", emptyMap()))
        assertEquals("2.5G baseT", readable("2.5G-baseT", emptyMap()))
        assertEquals("10G baseSR LR", readable("10G-baseSR-LR", emptyMap()))
        assertEquals("192.0.2.0/24", readable("192.0.2.0/24", emptyMap()))
        assertEquals("1000-2000", readable("1000-2000", emptyMap()))
        assertEquals("bridge-local", readable("bridge-local", emptyMap()))
        assertEquals("Ten", readable("10M-baseT-half", mapOf("10M-baseT-half" to "Ten")))
    }

    private fun SkikoComposeUiTest.status(dark: Boolean, clip: FakeClipboard) {
        Locale.setDefault(Locale.ENGLISH)
        val interfaces = listOf(Item("*1", mapOf("name" to "ether1", "type" to "ether", "running" to "true")))
        val ethernet = listOf(Item("*1", mapOf("name" to "ether1", "default-name" to "ether1", "mtu" to "1500", "mac-address" to "00:00:5E:00:53:ED")))
        val schema = MenuSchema(
            MenuPath.parse("interface/ethernet"), NodeKind.List, emptyList(), listOf("print", "set", "unset"),
            listOf(Argument("name", ValueType.Text()), Argument("mtu", ValueType.Integer(0, 65536))),
        )
        val monitor = Item("", mapOf("name" to "ether1", "status" to "link-ok", "rate" to "1Gbps", "advertising" to advertised, "link-partner-advertising" to advertised))
        val router = FakeRouter(
            mapOf("interface" to interfaces, "interface/ethernet" to ethernet),
            schemas = mapOf("interface/ethernet" to schema),
            output = mapOf("interface/ethernet/monitor" to listOf(monitor)),
        )
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { CompositionLocalProvider(LocalClipboard provides clip) { NetworkHelmTheme(dark = dark) { HintHost { MainScreen(tab, onDisconnect = {}) } } } }
        tab.open(MenuEntry("Interfaces", "interface"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("ether1")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("ether1").performMouseInput { doubleClick() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Interface <ether1>")).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText("Status")).onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("1G baseT full")).fetchSemanticsNodes().size == 2 }
    }

    @Test
    fun theStatusOfAPortListsWhatItAdvertisesAnElementALine() = runDesktopComposeUiTest(1280, 900) {
        val clip = FakeClipboard()
        status(dark = true, clip)
        assertEquals(2, onAllNodes(hasText("10M baseT half")).fetchSemanticsNodes().size, "the port advertises it and so does the other end")
        assertTrue(onAllNodes(hasText("10M-baseT-half", substring = true)).fetchSemanticsNodes().isEmpty(), "no element keeps the hyphens, no line has all")
        assertTrue(onAllNodes(hasText("Advertising")).fetchSemanticsNodes().isNotEmpty() && onAllNodes(hasText("Link Partner Advertising")).fetchSemanticsNodes().isNotEmpty())
        picture("list-field-dark")

        onAllNodes(hasText("100M baseT full")).onFirst().performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasContentDescription("Copy value")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Copy value").performClick()
        waitUntil(timeoutMillis = 5_000) { clip.entry != null }
        assertEquals("100M-baseT-full", runBlocking { clip.readText() })

        clip.entry = null
        onAllNodes(hasText("10M baseT half")).onFirst().performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasContentDescription("Copy the whole list")).fetchSemanticsNodes().isNotEmpty() }
        picture("list-field-copy")
        onNodeWithContentDescription("Copy the whole list").performClick()
        waitUntil(timeoutMillis = 5_000) { clip.entry != null }
        assertEquals(advertised, runBlocking { clip.readText() })
    }

    @Test
    fun theSameInTheLightTheme() = runDesktopComposeUiTest(1280, 900) {
        status(dark = false, FakeClipboard())
        picture("list-field-light")
    }
}
