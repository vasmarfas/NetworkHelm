package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NetwatchTest {

    private val path = MenuPath.parse("tool/netwatch")

    private val schema = MenuSchema(
        path, NodeKind.List, emptyList(), listOf("add", "print", "remove", "set"),
        listOf(
            Argument("name", ValueType.Text()),
            Argument("host", ValueType.Text()),
            Argument("type", ValueType.Enumeration(closed = true), listOf("dns", "http-get", "https-get", "icmp", "simple", "tcp-conn")),
            Argument("interval", ValueType.Time, optional = true),
            Argument("port", ValueType.Integer(1, 65535), optional = true),
            Argument("packet-count", ValueType.Integer(1, 100), optional = true),
            Argument("disabled", ValueType.YesNo),
        ),
    )

    private val probe = Item(
        "*1",
        mapOf(
            "name" to "rd-test-nw", "host" to "127.0.0.1", "type" to "icmp", "interval" to "5s", "status" to "up", "since" to "2026-10-24 20:24:08",
            "done-tests" to "38", "failed-tests" to "0", "rtt-avg" to "13us", "loss-percent" to "0",
        ),
    )

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun aProbeShowsTheStateItHasAndANewOneNone() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val router = FakeRouter(mapOf(path.toString() to listOf(probe)), schemas = mapOf(path.toString() to schema))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Netwatch", path.toString()))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd-test-nw")).fetchSemanticsNodes().isNotEmpty() }
        val columns = onAllNodes(hasText("Status")).fetchSemanticsNodes().size

        onNodeWithText("New").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("New Netwatch")).fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-host")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(absent("packet-count") && absent("port"), "the fields of icmp and tcp-conn")
        assertEquals(columns, onAllNodes(hasText("Status")).fetchSemanticsNodes().size)
        onAllNodes(hasText("Cancel")).onLast().performClick()

        onNodeWithText("rd-test-nw").performMouseInput { doubleClick() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Netwatch <127.0.0.1>")).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText("Status")).onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("RTT Avg")).fetchSemanticsNodes().isNotEmpty() }
        picture("netwatch-status")
        assertTrue(listOf("TCP Connect Time", "HTTP Status Code", "Resolved IP").all { onAllNodes(hasText(it)).fetchSemanticsNodes().isEmpty() })
    }

    @Test
    fun aPortIsThereForAProbeOfTcpOrHttpOnly() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val router = FakeRouter(mapOf(path.toString() to listOf(probe)), schemas = mapOf(path.toString() to schema))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Netwatch", path.toString()))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd-test-nw")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("rd-test-nw").performMouseInput { doubleClick() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Netwatch <127.0.0.1>")).fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("set-packet-count")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(absent("port"), "the port of an ICMP probe")
        onNode(hasClickAction() and hasAnyAncestor(hasTestTag("field-type"))).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("tcp-conn")).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText("tcp-conn")).onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("set-port")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("set-port").assertIsEnabled()
        assertTrue(absent("packet-count"), "the packet count of a TCP probe")
        picture("netwatch-tcp")
    }

    private fun SkikoComposeUiTest.absent(name: String) = onAllNodes(hasTestTag("set-$name") or hasTestTag("field-$name")).fetchSemanticsNodes().isEmpty()
}
