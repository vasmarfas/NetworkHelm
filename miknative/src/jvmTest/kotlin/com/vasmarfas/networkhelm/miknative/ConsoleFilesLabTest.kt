package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.routeros.console.connectRouterOsConsole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class ConsoleFilesLabTest {
    private val files = MenuPath.parse("file")

    private fun check(prefix: String) = runBlocking {
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password")
        withTimeout(120.seconds) {
            val ssh = connectNative(lab.getValue("$prefix.host"), lab.getValue("$prefix.user"), lab.getValue("$prefix.password"), this)
            val session = connectRouterOsConsole(ssh, lab.getValue("$prefix.user"), this) ?: error("the console speaks no dialect of the client")
            val name = "rd-test-files-$prefix.txt"
            val bytes = "line one\nline two\n".encodeToByteArray()
            try {
                ssh.sftp().write("/$name", bytes)
                val item = withTimeoutOrNull(10.seconds) {
                    var found: Item?
                    do {
                        val items = (session.watch(files).first() as ListEvent.Snapshot).items
                        println("FILES $prefix menu lists ${items.size} entries")
                        found = items.firstOrNull { it["name"] == name }
                        if (found == null) delay(500)
                    } while (found == null)
                    found
                }
                println("FILES $prefix item: type=${item?.get("type")} size=${item?.get("size")}")
                assertNotNull(item, "the file is not in the list of the menu")
                assertContentEquals(bytes, ssh.sftp().read("/$name"))
                try {
                    val listed = ssh.sftp().list("/")
                    println("FILES $prefix transfer lists ${listed.size} entries")
                } catch (e: Exception) {
                    println("FILES $prefix transfer list failed: ${e.message}")
                }
                session.remove(files, listOf(item.id))
                val after = (session.watch(files).first() as ListEvent.Snapshot).items
                assertEquals(false, after.any { it["name"] == name })
            } finally {
                runCatching { ssh.sftp().remove("/$name") }
                session.close()
            }
        }
    }

    @Test
    fun filesOfRouterOs7() = check("chr")

    @Test
    fun filesOfRouterOs6() = check("chr6")
}
