package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.ssh.ExecResult
import com.vasmarfas.networkhelm.ssh.SftpClient
import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.CannedStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShellFilesTest {

    @Test
    fun namesAndPathsAreQuotedInEveryShellOperation() = runTest {
        for (name in listOf("a\"b", "a'b", "\$(touch marker)", "`touch marker`", "a;b", "a\nb", "a\\b", "имя.txt")) {
            val dropbear = Dropbear()
            val path = "/tmp/$name"
            val files = ShellFiles(dropbear)
            files.rename("/tmp/original", path)
            assertEquals("if [ -e ${quote(path)} ]; then printf '%s\\n' ${quote("$name exists")} >&2; exit 1; fi; mv '/tmp/original' ${quote(path)}", dropbear.commands.single())
            dropbear.commands.clear()
            readFile(SshUbus(dropbear), emptySet(), dropbear, path)
            listFiles(SshUbus(dropbear), emptySet(), dropbear, path)
            assertEquals(listOf("cat ${quote(path)} 2>/dev/null", "ls ${quote(path)} 2>/dev/null"), dropbear.commands)
            dropbear.commands.clear()
            val value = "$name\nRD_EOF\ntouch marker"
            ShellUci(dropbear).set("network", "lan", mapOf("description" to value), emptyMap())
            assertEquals("mkdir -p /tmp/rd-uci; printf '%s\\n' ${quote("set network.lan.description=${quote(value)}")} | uci -P /tmp/rd-uci batch", dropbear.commands.single())
            dropbear.commands.clear()
            val empty = JsonObject(emptyMap())
            val session = OpenWrtSession(SshUbus(dropbear), ShellUci(dropbear), OpenWrtFacts(empty, empty, empty, emptySet(), emptySet(), emptySet(), true), dropbear, true)
            session.export(MenuPath(listOf(name)))
            session.export(MenuPath(emptyList()))
            assertEquals(listOf("uci export ${quote(name)}", "uci export"), dropbear.commands)
        }
    }

    private class Dropbear(private val present: Boolean = true, private val processAnswer: String? = null) : SshSession {
        val commands = mutableListOf<String>()
        val streams = mutableListOf<CannedStream>()

        override suspend fun exec(command: String): ExecResult {
            commands += command
            return when {
                command.startsWith("ls ") -> ExecResult(LISTING, "", 0)
                command.startsWith("[ -x ") -> ExecResult("", "", 1)
                else -> ExecResult("", "", if (present) 0 else 1)
            }
        }

        override suspend fun process(command: String): ByteStream {
            commands += command
            return CannedStream(processAnswer ?: if (command.startsWith("cat ")) "contents" else "").also { streams += it }
        }

        override suspend fun shell(term: String, columns: Int, rows: Int): ShellChannel = error("no shell")
        override suspend fun sftp(): SftpClient = error("no sftp-server")
        override suspend fun forward(host: String, port: Int): ByteStream = error("no forward")
        override fun close() = Unit
    }

    @Test
    fun boundedFileReadsPreserveMissingAndEmptyFilesAndCloseTheirChannel() = runTest {
        val path = "/tmp/a'b.js"
        for ((answer, expected) in listOf("text\n0" to "text", "\n0" to "", "\n1" to null, "\n2" to null)) {
            val ssh = Dropbear(processAnswer = answer)
            assertEquals(expected, readFile(SshUbus(ssh), emptySet(), ssh, path, maxBytes = 4))
            assertEquals("head -c 5 < ${quote(path)} 2>/dev/null; printf '\\n%d' \$?", ssh.commands.single())
            assertTrue(ssh.streams.single().closed)
        }
        for (answer in listOf("abcde\n0", "x".repeat(10_000), "abcd")) {
            val ssh = Dropbear(processAnswer = answer)
            assertFailsWith<DeviceException> { readFile(SshUbus(ssh), emptySet(), ssh, path, maxBytes = 4) }
            assertTrue(ssh.streams.single().closed)
        }
    }

    @Test
    fun withoutSftpServerFilesGoThroughCatAndHead() = runTest {
        val dropbear = Dropbear()
        val files = OpenWrtSsh(dropbear).sftp()
        assertEquals(listOf(".busybox_ash_history", ".uci", "sftp-server", "a file with spaces"), files.list("/tmp").map { it.name })
        val spaced = files.list("/tmp").last()
        assertEquals(listOf(47L, 1792663217L), listOf(spaced.size, spaced.modifiedEpochSeconds))
        assertTrue(files.list("/tmp")[1].directory)
        assertEquals("contents", files.read("/tmp/rd-test.txt").decodeToString())
        files.write("/tmp/rd-test.txt", "abc".encodeToByteArray())
        assertEquals("abc", dropbear.streams.last().written.toString())
        assertTrue("head -c 3 > '/tmp/rd-test.txt'" in dropbear.commands, dropbear.commands.toString())
        assertTrue(dropbear.streams.all { it.closed })
        assertFailsWith<DeviceException> { OpenWrtSsh(Dropbear(present = false)).sftp().read("/tmp/none") }
    }

    @Test
    fun filesAreRenamedMadeAndRemovedThroughCommands() = runTest {
        val dropbear = Dropbear()
        val files = OpenWrtSsh(dropbear).sftp()
        files.rename("/tmp/a b", "/tmp/c")
        files.mkdir("/tmp/rd-test")
        files.remove("/tmp/rd-test")
        val run = dropbear.commands.drop(1)
        assertTrue(run[0].endsWith("mv '/tmp/a b' '/tmp/c'") && "-e '/tmp/c'" in run[0], run.toString())
        assertEquals("mkdir '/tmp/rd-test'", run[1])
        assertTrue("rmdir '/tmp/rd-test'" in run[2] && "rm -f '/tmp/rd-test'" in run[2], run.toString())
    }

    private companion object {
        val LISTING = """
            drwxrwxrwt   18 root     root           500 2026-10-22 14:22:01 +0000 .
            drwxr-xr-x   18 root     root          4096 2026-10-21 22:56:33 +0000 ..
            -rw-------    1 root     root            25 2026-10-22 00:48:32 +0000 .busybox_ash_history
            drwx------    2 root     root           100 2026-10-22 01:03:00 +0000 .uci
            lrwxrwxrwx    1 root     root            18 2026-10-21 23:41:00 +0000 sftp-server -> ../lib/sftp-server
            -rw-r--r--    1 root     root            47 2026-10-22 13:00:17 +0300 a file with spaces
        """.trimIndent()
    }
}
