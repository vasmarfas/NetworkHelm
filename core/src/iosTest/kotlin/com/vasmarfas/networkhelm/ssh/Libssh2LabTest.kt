package com.vasmarfas.networkhelm.ssh

import com.vasmarfas.networkhelm.IosLab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.routeros.api.ApiConnection
import com.vasmarfas.networkhelm.routeros.api.ApiEncoding
import com.vasmarfas.networkhelm.routeros.api.call
import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class Libssh2LabTest {

    private fun session(prefix: String, block: suspend CoroutineScope.(SshSession, Map<String, String>) -> Unit) = runBlocking {
        val router = IosLab.router(prefix, "host", "user", "password", "ssh.hostKey") ?: return@runBlocking
        withTimeout(60.seconds) {
            val ssh = connectSsh(
                router.getValue("host"), 22, router.getValue("user"), SshAuth.Password(router.getValue("password")),
                acceptHostKey = { it.fingerprint == router.getValue("ssh.hostKey") },
            )
            try {
                block(ssh, router)
            } finally {
                ssh.close()
            }
        }
    }

    private suspend fun ByteStream.readUntil(marker: String): String {
        val text = StringBuilder()
        val buffer = ByteArray(4096)
        while (marker !in text) {
            val count = read(buffer, 0, buffer.size)
            if (count < 0) break
            val chunk = buffer.decodeToString(0, count)
            repeat(Regex("\u001b\\[6n").findAll(chunk).count()) { write("\u001b[40;120R".encodeToByteArray()) }
            text.append(chunk)
        }
        return text.toString()
    }

    @Test
    fun routerOs7OverSsh() = session("chr") { ssh, router -> everything(ssh, router, "7.") }

    @Test
    fun routerOs6OverSsh() = session("chr6") { ssh, router -> everything(ssh, router, "6.") }

    private suspend fun CoroutineScope.everything(ssh: SshSession, router: Map<String, String>, version: String) {
        val identity = ssh.exec("/system identity print")
        assertEquals(0, identity.exitStatus)
        assertTrue("name:" in identity.output, identity.output)

        val shell = ssh.shell("xterm", 120, 40)
        try {
            shell.readUntil("] > ")
            shell.write("/system identity print\r".encodeToByteArray())
            assertTrue("name:" in shell.readUntil("name:"))
            shell.resize(100, 30)
            shell.write("/quit\r".encodeToByteArray())
        } finally {
            shell.close()
        }

        val sftp = ssh.sftp()
        val name = "rd-test-ios.bin"
        val bytes = ByteArray(70_000) { (it * 31).toByte() }
        try {
            sftp.write("/$name", bytes)
            assertTrue(sftp.list("/").any { it.name == name && it.size == bytes.size.toLong() && !it.directory })
            assertContentEquals(bytes, sftp.read("/$name"))
            sftp.remove("/$name")
            assertFalse(sftp.list("/").any { it.name == name })
        } finally {
            sftp.close()
        }

        val api = ApiConnection(ssh.forward("127.0.0.1", 8728), ApiEncoding.Utf8, this)
        try {
            api.login(router.getValue("user"), router.getValue("password"))
            val running = api.call("/system/resource/print", "=.proplist=version").rows.single().getValue("version")
            assertTrue(running.startsWith(version), running)
        } finally {
            api.close()
        }
    }

    @Test
    fun anUnknownHostKeyStopsTheConnectionBeforeLogin() = runBlocking {
        for (prefix in listOf("chr", "chr6", "openwrt", "openwrt24")) {
            val router = IosLab.router(prefix, "host", "ssh.hostKey") ?: continue
            val error = assertFailsWith<HostKeyRejectedException>(prefix) {
                withTimeout(30.seconds) { connectSsh(router.getValue("host"), 22, "rd-test", SshAuth.Password(""), acceptHostKey = { false }) }
            }
            assertEquals(router.getValue("ssh.hostKey"), error.key.fingerprint, prefix)
            if (prefix == "chr6") assertEquals("ssh-rsa", error.key.type)
        }
    }

    @Test
    fun aWrongPasswordIsRefused() = runBlocking {
        val router = IosLab.router("chr", "host", "user", "ssh.hostKey") ?: return@runBlocking
        val error = assertFailsWith<DeviceException> {
            connectSsh(router.getValue("host"), 22, router.getValue("user"), SshAuth.Password("rd-test-wrong"), acceptHostKey = { it.fingerprint == router.getValue("ssh.hostKey") })
        }
        assertEquals(ErrorCategory.PermissionDenied, error.category)
    }

    @Test
    fun ed25519KeyLogin() = keyLogin("chr", "keyEd25519")

    @Test
    fun rsaKeyLogin() = keyLogin("chr", "keyRsa")

    @Test
    fun rsaKeyLoginOnRouterOs6() = keyLogin("chr6", "keyRsa")

    private fun keyLogin(prefix: String, keyField: String) = runBlocking {
        val router = IosLab.router(prefix, "host", "keyUser", keyField, "ssh.hostKey") ?: return@runBlocking
        withTimeout(30.seconds) {
            val ssh = connectSsh(
                router.getValue("host"), 22, router.getValue("keyUser"), SshAuth.Key(IosLab.bytes(router.getValue(keyField))),
                acceptHostKey = { it.fingerprint == router.getValue("ssh.hostKey") },
            )
            try {
                assertEquals(0, ssh.exec("/system identity print").exitStatus)
            } finally {
                ssh.close()
            }
        }
    }

    @Test
    fun dropbearOfOpenWrt() {
        for (prefix in listOf("openwrt", "openwrt24")) {
            session(prefix) { ssh, _ ->
                val board = ssh.exec("ubus call system board")
                assertEquals(0, board.exitStatus)
                assertTrue("\"release\"" in board.output, board.output)

                val sftp = ssh.sftp()
                try {
                    assertTrue(sftp.list("/etc/config").any { it.name == "network" })
                } finally {
                    sftp.close()
                }

                val shell = ssh.shell("xterm", 120, 40)
                try {
                    shell.readUntil("# ")
                    shell.write("exit\n".encodeToByteArray())
                } finally {
                    shell.close()
                }

                val http = ssh.forward("127.0.0.1", 80)
                try {
                    http.write("HEAD / HTTP/1.0\r\nHost: localhost\r\n\r\n".encodeToByteArray())
                    val buffer = ByteArray(64)
                    val count = http.read(buffer, 0, buffer.size)
                    assertTrue(count > 0 && buffer.decodeToString(0, count).startsWith("HTTP/1."))
                } finally {
                    http.close()
                }
            }
        }
    }
}
