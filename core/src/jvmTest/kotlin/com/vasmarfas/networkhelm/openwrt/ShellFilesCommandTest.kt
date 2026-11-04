package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.ssh.ExecResult
import com.vasmarfas.networkhelm.ssh.SftpClient
import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ShellFilesCommandTest {

    @Test
    fun anExistingTargetCannotExecuteItsNameInTheErrorMessage() = runTest {
        val sh = if (System.getProperty("os.name").startsWith("Windows")) File(System.getenv("ProgramFiles"), "Git/bin/sh.exe").path else "sh"
        assumeTrue("sh or touch is unavailable", runCatching { ProcessBuilder(sh, "-c", "PATH=/usr/bin:/bin; command -v touch >/dev/null").start().waitFor() == 0 }.getOrDefault(false))
        val directory = Files.createTempDirectory("shell-files-").toFile()
        val original = File(directory, "original").also { it.writeText("original contents") }
        val ssh = object : SshSession {
            override suspend fun exec(command: String): ExecResult {
                val process = ProcessBuilder(sh, "-s").directory(directory).redirectErrorStream(true).start()
                process.outputStream.bufferedWriter().use { it.write("PATH=/usr/bin:/bin\n$command\n") }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                return ExecResult(output, "", process.waitFor())
            }

            override suspend fun process(command: String): ByteStream = error("no process")
            override suspend fun shell(term: String, columns: Int, rows: Int): ShellChannel = error("no shell")
            override suspend fun sftp(): SftpClient = error("no SFTP")
            override suspend fun forward(host: String, port: Int): ByteStream = error("no forwarding")
            override fun close() = Unit
        }
        for (name in listOf("\$(touch marker)", "`touch marker`", "it's \$(touch marker)")) {
            val target = File(directory, name).also { it.writeText("target contents") }
            val failure = assertFailsWith<DeviceException> { ShellFiles(ssh).rename(original.name, target.name) }
            assertEquals("$name exists", failure.message)
            assertEquals("original contents", original.readText())
            assertEquals("target contents", target.readText())
            assertFalse(File(directory, "marker").exists())
        }
    }
}
