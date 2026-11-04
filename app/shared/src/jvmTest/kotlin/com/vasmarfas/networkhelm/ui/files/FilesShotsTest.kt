package com.vasmarfas.networkhelm.ui.files

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeClipboard
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ssh.ExecResult
import com.vasmarfas.networkhelm.ssh.RemoteFile
import com.vasmarfas.networkhelm.ssh.SftpClient
import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.ssh.SshReach
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.ui.kit.LocalCompact
import com.vasmarfas.networkhelm.ui.main.MainScreen
import com.vasmarfas.networkhelm.ui.main.RouterTab
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.Legacy
import com.vasmarfas.networkhelm.ui.theme.Modern
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import com.vasmarfas.networkhelm.ui.theme.ThemeFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FilesShotsTest {

    private val script = """
        # rd-test export of 2026-10-27
        /ip firewall filter
        add action=accept chain=input comment="allow ssh" connection-state=established,related dst-port=22 protocol=tcp
        add action=drop chain=input in-interface-list=WAN
        /system script
        add name=rd-test source=":local limit 10;\n:foreach i in=[/ip address find] do={ :put (\"item \" . ${'$'}i) }"
        /ip dhcp-server network
        add address=192.168.88.0/24 dns-server=192.168.88.1 gateway=192.168.88.1
    """.trimIndent() + "\n"

    private class Sftp(private val contents: Map<String, ByteArray>) : SftpClient {
        override suspend fun list(path: String) = emptyList<RemoteFile>()
        override suspend fun read(path: String) = contents[path] ?: ByteArray(0)
        override suspend fun write(path: String, bytes: ByteArray) = Unit
        override suspend fun remove(path: String) = Unit
        override fun close() = Unit
    }

    private class Ssh(val sftp: Sftp, private val answer: SshReach = SshReach.Open) : SshSession {
        override suspend fun reach(fresh: Boolean) = answer
        override suspend fun exec(command: String) = ExecResult("", "", 0)
        override suspend fun process(command: String): ByteStream = error("no process")
        override suspend fun shell(term: String, columns: Int, rows: Int): ShellChannel = error("no shell")
        override suspend fun sftp() = sftp
        override suspend fun forward(host: String, port: Int): ByteStream = error("no forward")
        override fun close() = Unit
    }

    private fun picture(): ByteArray {
        val image = BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 160) for (y in 0 until 90) image.setRGB(x, y, ((x * 255 / 160) shl 16) or ((y * 255 / 90) shl 8) or 0x80)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private val disk = "disk-s-10724519"
    private val long = "usb1/photo-from-the-camera-of-the-owner-taken-on-the-24th-of-october-2026-final-version-2.png"
    private val rows = listOf(
        Item("*1", mapOf("name" to disk, "type" to "disk")),
        Item("*2", mapOf("name" to "$disk/mikrotikfiles", "type" to "directory", "last-modified" to "2026-10-20 18:02:11")),
        Item("*3", mapOf("name" to "$disk/mikrotikfiles/backups", "type" to "directory", "last-modified" to "2026-10-26 19:30:44")),
        Item("*4", mapOf("name" to "$disk/mikrotikfiles/backups/MikroTik-20261026-1930.backup", "type" to "backup", "size" to "184320", "last-modified" to "2026-10-26 19:30:44")),
        Item("*5", mapOf("name" to "$disk/mikrotikfiles/routeros-7.26-x86.npk", "type" to "package", "size" to "20971520", "last-modified" to "2026-10-21 12:00:09")),
        Item("*6", mapOf("name" to "usb1", "type" to "read-only mountpoint")),
        Item("*7", mapOf("name" to long, "type" to ".png file", "size" to "48213", "last-modified" to "2026-10-24 21:20:02")),
        Item("*8", mapOf("name" to "skins", "type" to "directory", "last-modified" to "3h23s")),
        Item("*9", mapOf("name" to "skins/dark.json", "type" to ".json file", "size" to "2412", "last-modified" to "2026-10-24 21:15:30")),
        Item("*10", mapOf("name" to "export.rsc", "type" to "script", "size" to "${script.length}", "last-modified" to "2026-10-27 09:12:01")),
        Item("*11", mapOf("name" to "rc.local", "type" to ".local file", "size" to "127", "last-modified" to "2026-10-22 07:08:26")),
        Item("*12", mapOf("name" to "gradient.png", "type" to ".png file", "size" to "${picture().size}", "last-modified" to "2026-10-24 21:20:02")),
        Item("*13", mapOf("name" to "system.log", "type" to ".log file", "size" to "40960", "last-modified" to "2026-10-27 09:40:12")),
        Item("*14", mapOf("name" to "tool.bin", "type" to "file", "size" to "7", "last-modified" to "2026-10-23 10:00:00")),
        Item("*15", mapOf("name" to ".hidden", "type" to "file", "size" to "3", "last-modified" to "2026-10-23 10:00:00")),
    )

    private val contents = mapOf(
        "/export.rsc" to script.encodeToByteArray(),
        "/gradient.png" to picture(),
        "/tool.bin" to byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x00, 0x01, 0x02),
        "/system.log" to "2026-10-27 09:12:01 system,info,account user admin logged in from 192.168.88.10 via winbox\n2026-10-27 09:12:05 system,error,critical login failure for user admin from 10.0.0.5 via ssh\n2026-10-27 09:13:00 firewall,warning dropped packet\n".repeat(40).encodeToByteArray(),
    )

    private fun SkikoComposeUiTest.shot(name: String) {
        waitForIdle()
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "files-$name.png"))
    }

    private fun ComposeUiTest.show(theme: ThemeFile, dark: Boolean, compact: Boolean = false, reach: SshReach = SshReach.Open): RouterTab {
        Locale.setDefault(Locale.ENGLISH)
        val storage = MemoryStorage()
        val resource = Item("", mapOf("free-hdd-space" to "37748736", "total-hdd-space" to "134217728"))
        val router = FakeRouter(mapOf("file" to rows, "system/resource" to listOf(resource)), ssh = Ssh(Sftp(contents), reach), fileTexts = mapOf("*10" to script, "*14" to "\u007fELF\u0000\u0001\u0002"))
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        val exchange = object : FileExchange {
            override suspend fun pick() = emptyList<LocalFile>()
            override suspend fun save(name: String, bytes: ByteArray) = true
        }
        setContent {
            CompositionLocalProvider(LocalFileExchange provides exchange, LocalCompact provides compact, LocalClipboard provides FakeClipboard()) {
                NetworkHelmTheme(theme, dark) { MainScreen(tab, onDisconnect = {}) }
            }
        }
        tab.open(MenuEntry("Files", "file"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("export.rsc", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        return tab
    }

    private fun SkikoComposeUiTest.manager(name: String, theme: ThemeFile, dark: Boolean, compact: Boolean = false, open: Boolean = false, filter: String? = null) {
        show(theme, dark, compact)
        if (open) {
            for (folder in listOf(disk, "$disk/mikrotikfiles", "$disk/mikrotikfiles/backups", "usb1", "skins")) onNodeWithTag("toggle-$folder").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("MikroTik-20261026-1930.backup", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("row-$disk/mikrotikfiles").performClick()
        } else if (filter != null) {
            onNodeWithTag("find").performTextInput(filter)
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("items", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        } else if (!compact) {
            onNodeWithTag("row-export.rsc").performClick()
            onNodeWithTag("files-download").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("transfers")).fetchSemanticsNodes().isNotEmpty() }
        }
        shot(name)
    }

    @Test
    fun theWindowOfFilesInTheLooksOfTheApp() {
        runDesktopComposeUiTest(1100, 700) { manager("dark", Modern, true) }
        runDesktopComposeUiTest(1100, 700) { manager("light", Modern, false) }
        runDesktopComposeUiTest(1100, 700) { manager("legacy", Legacy, false) }
        runDesktopComposeUiTest(400, 800) { manager("phone", Modern, true, compact = true) }
    }

    @Test
    fun theTreeOfFoldersOpenAtThreeLevelsAndFilteredInAllLooks() {
        runDesktopComposeUiTest(1100, 700) { manager("tree-open-dark", Modern, true, open = true) }
        runDesktopComposeUiTest(1100, 700) { manager("tree-open-light", Modern, false, open = true) }
        runDesktopComposeUiTest(1100, 700) { manager("tree-open-legacy", Legacy, false, open = true) }
        runDesktopComposeUiTest(1100, 700) { manager("tree-filter-dark", Modern, true, filter = "backup") }
        runDesktopComposeUiTest(1100, 700) { manager("tree-filter-light", Modern, false, filter = "backup") }
        runDesktopComposeUiTest(400, 800) { manager("tree-open-phone", Modern, true, compact = true, open = true) }
    }

    private fun SkikoComposeUiTest.withSshOff(name: String, file: String?, theme: ThemeFile, dark: Boolean) {
        show(theme, dark, reach = SshReach.Off)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("files-bulk-note")).fetchSemanticsNodes().isNotEmpty() }
        if (file != null) {
            onNodeWithText(file).performMouseInput { doubleClick() }
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasTestTag("editor")).fetchSemanticsNodes().isNotEmpty() || onAllNodes(hasTestTag("file-needs-bulk")).fetchSemanticsNodes().isNotEmpty()
            }
        }
        shot(name)
    }

    @Test
    fun theWindowsOfFilesWithSshTurnedOff() {
        runDesktopComposeUiTest(1100, 700) { withSshOff("ssh-off-manager-dark", null, Modern, true) }
        runDesktopComposeUiTest(1100, 700) { withSshOff("ssh-off-manager-light", null, Modern, false) }
        runDesktopComposeUiTest(1100, 700) { withSshOff("ssh-off-small-text", "export.rsc", Modern, true) }
        runDesktopComposeUiTest(1100, 700) { withSshOff("ssh-off-big-file", "system.log", Modern, true) }
        runDesktopComposeUiTest(1100, 700) { withSshOff("ssh-off-big-file-light", "system.log", Modern, false) }
    }

    private fun SkikoComposeUiTest.opened(name: String, file: String, theme: ThemeFile, dark: Boolean, query: String? = null) {
        show(theme, dark)
        onNodeWithText(file).performMouseInput { doubleClick() }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag("editor")).fetchSemanticsNodes().isNotEmpty() || onAllNodes(hasTestTag("viewer")).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodes(hasTestTag("hex")).fetchSemanticsNodes().isNotEmpty() || onAllNodes(hasTestTag("image")).fetchSemanticsNodes().isNotEmpty()
        }
        if (query != null) {
            onNodeWithTag("file-find").performClick()
            onNodeWithTag("find-query").performTextInput(query)
        }
        shot(name)
    }

    @Test
    fun theWindowOfAFileAsEditorViewerDumpAndPicture() {
        runDesktopComposeUiTest(1100, 700) { opened("editor-dark", "export.rsc", Modern, true) }
        runDesktopComposeUiTest(1100, 700) { opened("editor-light-find", "export.rsc", Modern, false, query = "chain") }
        runDesktopComposeUiTest(1100, 700) { opened("editor-legacy", "export.rsc", Legacy, false) }
        runDesktopComposeUiTest(1100, 700) { opened("log-viewer", "system.log", Modern, true) }
        runDesktopComposeUiTest(1100, 700) { opened("hex", "tool.bin", Modern, true) }
        runDesktopComposeUiTest(1100, 700) { opened("image", "gradient.png", Modern, true) }
    }
}
