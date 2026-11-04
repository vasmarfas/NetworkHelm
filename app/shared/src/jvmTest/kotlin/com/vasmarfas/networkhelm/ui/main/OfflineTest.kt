package com.vasmarfas.networkhelm.ui.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.App
import com.vasmarfas.networkhelm.Shell
import com.vasmarfas.networkhelm.app.HiddenValue
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.app.Services
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.ui.files.FileExchange
import com.vasmarfas.networkhelm.ui.files.LocalFile
import com.vasmarfas.networkhelm.ui.files.LocalFileExchange
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OfflineTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun anExportOpensInTheWindowsOfARouterToRead() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val export = """
            # 2026-10-23 17:40:12 by RouterOS 7.25.1
            # model = CHR
            /ip address
            add address=192.168.88.1/24 comment=defconf interface=bridge network=192.168.88.0
            add address=10.0.0.1/24 disabled=yes interface=ether2 network=10.0.0.0
            /system identity
            set name=rd-test-office
        """.trimIndent()
        val files = ArrayDeque(listOf(LocalFile("picture.png") { byteArrayOf(-119, 80, 78, 71) }, LocalFile("office.rsc") { export.encodeToByteArray() }))
        val exchange = object : FileExchange {
            override suspend fun pick() = listOf(files.removeFirst())
            override suspend fun save(name: String, bytes: ByteArray) = false
        }
        lateinit var shell: Shell
        setContent {
            val scope = rememberCoroutineScope()
            shell = remember { Shell(Services(MemoryStorage(), MemoryTrustStore(), neighbors = null), scope) }
            CompositionLocalProvider(LocalFileExchange provides exchange) { App(shell) }
        }
        onNodeWithTag("open-file").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("picture.png is neither an /export of RouterOS nor a configuration or backup of OpenWrt")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("open-file").performClick()
        waitUntil(timeoutMillis = 5_000) { shell.routers.isNotEmpty() }
        val tab = shell.routers.single()
        assertEquals("rd-test-office" to "office.rsc", tab.session.device.identity to tab.address)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("offline")).fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { tab.open(MenuEntry("Addresses", "ip/address")) }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("192.168.88.1/24")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("Disconnect")).fetchSemanticsNodes().isEmpty(), "a file is closed, not disconnected")
        assertTrue(onAllNodes(hasText("Close")).fetchSemanticsNodes().isNotEmpty())
        picture("offline-export")
    }

    @Test
    fun twoExportsCompareFromTheToolbar() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val old = "/ip address\nadd address=192.168.88.1/24 interface=bridge\n/ip dns\nset servers=1.1.1.1\n/ppp secret\nadd name=rd-test password=rd-test-old\n" +
            "/system identity\nset name=rd-test-office\n"
        val new = "/ip address\nadd address=192.168.88.1/24 interface=bridge\nadd address=10.0.0.1/24 interface=ether2\n/ip dns\nset servers=1.1.1.1,8.8.8.8\n" +
            "/ppp secret\nadd name=rd-test password=rd-test-new\n/system identity\nset name=rd-test-office\n"
        val files = ArrayDeque(listOf(LocalFile("old.rsc") { old.encodeToByteArray() }, LocalFile("new.rsc") { new.encodeToByteArray() }))
        val exchange = object : FileExchange {
            override suspend fun pick() = listOf(files.removeFirst())
            override suspend fun save(name: String, bytes: ByteArray) = false
        }
        lateinit var shell: Shell
        setContent {
            val scope = rememberCoroutineScope()
            shell = remember { Shell(Services(MemoryStorage(), MemoryTrustStore(), neighbors = null), scope) }
            CompositionLocalProvider(LocalFileExchange provides exchange) { App(shell) }
        }
        onNodeWithTag("open-file").performClick()
        waitUntil(timeoutMillis = 5_000) { shell.routers.size == 1 }
        runOnIdle { shell.selected = 1 }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("open-file")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("open-file").performClick()
        waitUntil(timeoutMillis = 5_000) { shell.routers.size == 2 }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("compare")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("compare").performClick()
        onAllNodesWithText("rd-test-office (old.rsc)").onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("compare-status") and hasText("Menus that differ: 3")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasText("address=10.0.0.1/24 interface=ether2")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("servers: 1.1.1.1 → 1.1.1.1,8.8.8.8", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("password: $HiddenValue → $HiddenValue", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("rd-test-new", substring = true)).fetchSemanticsNodes().isEmpty(), "the password itself stays hidden")
        picture("compare")
    }
}
