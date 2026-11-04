package com.vasmarfas.networkhelm.ssh

import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LazySshTest {

    private class Exec : SshSession {
        override var connected = true
        var closed = false
        override suspend fun exec(command: String) = ExecResult(command, "", 0)
        override suspend fun process(command: String): ByteStream = error("no process")
        override suspend fun shell(term: String, columns: Int, rows: Int): ShellChannel = error("no shell")
        override suspend fun sftp(): SftpClient = error("no sftp")
        override suspend fun forward(host: String, port: Int): ByteStream = error("no forward")
        override fun close() {
            closed = true
        }
    }

    @Test
    fun theSessionOpensOnFirstUseOnceAndAgainAfterAFailure() = runTest {
        var opened = 0
        var refuse = true
        val ssh = LazySsh {
            opened++
            if (refuse) error("port 22 closed") else Exec()
        }
        assertEquals(0, opened, "nothing is opened before the first call")
        assertFailsWith<IllegalStateException> { ssh.exec("/system identity print") }
        refuse = false
        assertEquals("a", ssh.exec("a").output)
        assertEquals("b", ssh.exec("b").output)
        assertEquals(2, opened)
    }

    @Test
    fun aDroppedSessionIsOpenedAgainByTheNextCall() = runTest {
        val sessions = mutableListOf<Exec>()
        val ssh = LazySsh { Exec().also(sessions::add) }
        ssh.exec("a")
        sessions.single().connected = false
        assertEquals("b", ssh.exec("b").output)
        assertEquals(2, sessions.size)
        assertEquals(true, sessions.first().closed)
        ssh.exec("c")
        assertEquals(2, sessions.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSessionOpenedAfterCloseIsClosedAtOnce() = runTest {
        val login = CompletableDeferred<Unit>()
        val session = Exec()
        val ssh = LazySsh {
            login.await()
            session
        }
        val files = async { runCatching { ssh.exec("ls") } }
        runCurrent()
        ssh.close()
        login.complete(Unit)
        files.await()
        assertTrue(session.closed)
    }

    @Test
    fun theReachIsFoundOnceWithoutALoginAndAskedAgainOnRequest() = runTest {
        var probes = 0
        var opened = 0
        val ssh = LazySsh(probe = {
            probes++
            SshReach.Off
        }) {
            opened++
            Exec()
        }
        assertEquals(SshReach.Off, ssh.reach())
        assertEquals(SshReach.Off, ssh.reach())
        assertEquals(1, probes)
        assertEquals(0, opened, "a probe asks for no login")
        ssh.reach(fresh = true)
        assertEquals(2, probes)
    }

    @Test
    fun anOpenedSessionMakesTheReachOpenAndWithoutAProbeItIsOpen() = runTest {
        val ssh = LazySsh(probe = { SshReach.Closed(22) }) { Exec() }
        assertEquals(SshReach.Closed(22), ssh.reach())
        ssh.exec("a")
        assertEquals(SshReach.Open, ssh.reach())
        assertEquals(SshReach.Open, LazySsh { Exec() }.reach())
    }
}
