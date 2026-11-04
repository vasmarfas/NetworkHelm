package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.session.SshHostSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class HostFilesLabTest {
    private val files = MenuPath.parse("file")

    @Test
    fun theFilesOfTheRootAndOfItsDirectoriesAreListedAndRemoved() = runBlocking {
        val lab = Lab.require("chr.host", "chr.user", "chr.password")
        withTimeout(60.seconds) {
            val ssh = connectNative(lab.getValue("chr.host"), lab.getValue("chr.user"), lab.getValue("chr.password"), this)
            val session = SshHostSession(ssh, DeviceInfo(Platform.Ssh, "RouterOS", "TCP 8291", "", "", emptySet(), setOf("read", "write")))
            suspend fun listed() = (session.watch(files).first() as ListEvent.Snapshot).items.associateBy { it.id }
            try {
                val directory = ssh.sftp().list("/").first { it.directory }.name
                val names = listOf("rd-test-8291-host.txt", "$directory/rd-test-8291-nested.txt")
                try {
                    names.forEach { ssh.sftp().write("/$it", ByteArray(3)) }
                    val items = listed()
                    assertEquals(listOf("file" to "3", "file" to "3"), names.map { items[it]?.get("type") to items[it]?.get("size") }, items.keys.toString())
                } finally {
                    session.remove(files, names)
                }
                assertTrue(listed().keys.none { it in names })
            } finally {
                ssh.close()
            }
        }
    }
}
