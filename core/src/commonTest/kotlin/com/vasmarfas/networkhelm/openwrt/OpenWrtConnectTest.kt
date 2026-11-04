package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.ssh.ExecResult
import com.vasmarfas.networkhelm.ssh.SftpClient
import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenWrtConnectTest {

    private class Shell : ByteStream {
        val commands = mutableListOf<String>()
        private var pending = ByteArray(0)

        override suspend fun write(bytes: ByteArray) {
            commands += bytes.decodeToString()
            pending += "\nrd-status 0\n".encodeToByteArray()
        }

        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pending.isEmpty()) return -1
            val count = minOf(length, pending.size)
            pending.copyInto(buffer, offset, 0, count)
            pending = pending.copyOfRange(count, pending.size)
            return count
        }

        override fun close() = Unit
    }

    private class Ssh : SshSession {
        val shells = mutableListOf<Shell>()
        override suspend fun exec(command: String): ExecResult = ExecResult("", "", 1)
        override suspend fun process(command: String): ByteStream = Shell().also { shells += it }
        override suspend fun shell(term: String, columns: Int, rows: Int): ShellChannel = error("no shell")
        override suspend fun sftp(): SftpClient = error("no sftp")
        override suspend fun forward(host: String, port: Int): ByteStream = error("no forward")
        override fun close() = Unit
    }

    private object NoForms : LuciForms {
        override suspend fun views(): Map<String, List<String>> = emptyMap()
        override suspend fun maps(view: String): List<LuciMap> = emptyList()
        override suspend fun protocol(name: String): List<LuciOption> = emptyList()
        override suspend fun menu(): List<LuciNode> = emptyList()
    }

    @Test
    fun theFormsReadThroughAShellOfTheirOwn() = runTest {
        val ssh = Ssh()
        val given = mutableListOf<Ubus>()
        connectOpenWrt(ssh) { ubus ->
            given += ubus
            NoForms
        }
        assertEquals(1, ssh.shells.size)
        val before = ssh.shells.single().commands.size

        given.single().call("rd-test", "forms")

        assertEquals(2, ssh.shells.size)
        assertEquals(before, ssh.shells.first().commands.size)
        assertTrue("'rd-test' 'forms'" in ssh.shells.last().commands.single())
    }
}
