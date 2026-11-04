package com.vasmarfas.networkhelm.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ssh.ExecResult
import com.vasmarfas.networkhelm.ssh.SftpClient
import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.listenTcp
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TunnelsTest {

    private class Direct : SshSession {
        val opened = mutableListOf<Pair<String, Int>>()
        override suspend fun exec(command: String): ExecResult = error("no exec")
        override suspend fun process(command: String): ByteStream = error("no process")
        override suspend fun shell(term: String, columns: Int, rows: Int): ShellChannel = error("no shell")
        override suspend fun sftp(): SftpClient = error("no sftp")
        override suspend fun forward(host: String, port: Int): ByteStream = openTcp(host, port).also { opened += host to port }
        override fun close() = Unit
    }

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun aTunnelOpensFromItsWindowAndCarriesAConnection() = runDesktopComposeUiTest(1100, 700) {
        Locale.setDefault(Locale.ENGLISH)
        val echoes = CoroutineScope(Dispatchers.Default)
        val echo = runBlocking { listenTcp("127.0.0.1", 0) }
        echoes.launch {
            while (true) {
                val peer = runCatching { echo.accept() }.getOrNull() ?: break
                launch {
                    val buffer = ByteArray(1024)
                    while (true) {
                        val read = peer.read(buffer, 0, buffer.size)
                        if (read < 0) break
                        peer.write(buffer.copyOf(read))
                    }
                    peer.close()
                }
            }
        }
        val ssh = Direct()
        val storage = MemoryStorage()
        val tab = RouterTab(FakeRouter(ssh = ssh), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("SSH Tunnels")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("SSH Tunnels").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("tunnel-port")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("tunnel-port").performTextClearance()
        onNodeWithTag("tunnel-port").performTextInput("${echo.port}")
        onNodeWithTag("tunnel-open").performClick()
        waitUntil(timeoutMillis = 5_000) { tab.tunnels.all.singleOrNull()?.forwarding != null }
        val forwarding = tab.tunnels.all.single().forwarding
        val reply = runBlocking {
            val client = openTcp("127.0.0.1", checkNotNull(forwarding).localPort)
            client.write("rd-test".encodeToByteArray())
            val buffer = ByteArray(7)
            var read = 0
            while (read < 7) read += client.read(buffer, read, 7 - read)
            client.close()
            buffer.decodeToString()
        }
        assertEquals("rd-test", reply)
        assertEquals(listOf("127.0.0.1" to echo.port), ssh.opened)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("127.0.0.1:${forwarding?.localPort} → 127.0.0.1:${echo.port}")).fetchSemanticsNodes().isNotEmpty() }
        picture("tunnels")
        onNodeWithTag("tunnel-close").performClick()
        waitUntil(timeoutMillis = 5_000) { tab.tunnels.all.isEmpty() }
        assertTrue(runCatching { runBlocking { openTcp("127.0.0.1", forwarding?.localPort ?: 0, 1_000) } }.isFailure, "the port is free again")
        echo.close()
        echoes.cancel()
    }

    @Test
    fun aChannelThatCannotForwardHasNoTunnelsInItsMenu() = runDesktopComposeUiTest(1100, 700) {
        Locale.setDefault(Locale.ENGLISH)
        val ssh = object : SshSession by Direct() {
            override val canForward = false
        }
        val storage = MemoryStorage()
        val tab = RouterTab(FakeRouter(ssh = ssh), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        waitUntil(timeoutMillis = 5_000) { tab.menu != null }
        waitForIdle()
        assertTrue(tab.menu.orEmpty().any { it.title == "SSH Tunnels" })
        assertTrue(onAllNodes(hasText("SSH Tunnels")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aTunnelOpenedOnceIsKeptForTheNextConnection() = runDesktopComposeUiTest(1100, 700) {
        Locale.setDefault(Locale.ENGLISH)
        val storage = MemoryStorage()
        val first = RouterTab(FakeRouter(ssh = Direct()), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        var tab by mutableStateOf(first)
        setContent { NetworkHelmTheme(dark = true) { key(tab) { TunnelsView(tab) } } }
        onNodeWithTag("tunnel-open").performClick()
        waitUntil(timeoutMillis = 5_000) { first.tunnels.all.singleOrNull()?.forwarding != null }
        waitUntil(timeoutMillis = 5_000) { first.savedTunnels == listOf(SavedTunnel(0, "127.0.0.1", 80)) }
        first.tunnels.closeAll()

        val second = RouterTab(FakeRouter(ssh = Direct()), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        tab = second
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Opened before")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("saved-tunnel-open").performClick()
        waitUntil(timeoutMillis = 5_000) { second.tunnels.all.singleOrNull()?.forwarding != null }
        assertTrue(onAllNodesWithTag("saved-tunnel").fetchSemanticsNodes().isEmpty(), "an open tunnel is not among those opened before")
        second.tunnels.close(second.tunnels.all.single())
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("saved-tunnel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("saved-tunnel-forget").performClick()
        waitUntil(timeoutMillis = 5_000) { second.savedTunnels.isEmpty() }
        assertEquals("[]", runBlocking { storage.read("tunnels-rd-test-id.json") })
        second.tunnels.closeAll()
    }
}
