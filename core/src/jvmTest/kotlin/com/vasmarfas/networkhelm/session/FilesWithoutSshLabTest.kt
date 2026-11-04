package com.vasmarfas.networkhelm.session

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.openwrt.OpenWrtLogin
import com.vasmarfas.networkhelm.openwrt.OpenWrtProtocol
import com.vasmarfas.networkhelm.openwrt.connectOpenWrt
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsProtocol
import com.vasmarfas.networkhelm.routeros.connectRouterOs
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FilesWithoutSshLabTest {

    private val files = MenuPath.parse("file")

    private fun text(size: Int): String = buildString {
        var line = 0
        while (length < size) append("rd-test line ${line++} of a text\n")
    }.take(size)

    private fun routerOs(prefix: String, protocol: RouterOsProtocol, block: suspend (DeviceSession) -> Unit) = runBlocking {
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password")
        val scope = CoroutineScope(Dispatchers.Default)
        val session = connectRouterOs(RouterOsLogin(lab.getValue("$prefix.host"), lab.getValue("$prefix.user"), lab.getValue("$prefix.password"), protocol), TrustPolicy(MemoryTrustStore()) { true }, scope)
        try {
            withTimeout(120.seconds) { block(session) }
        } finally {
            session.close()
            scope.cancel()
        }
    }

    private suspend fun idOf(session: DeviceSession, name: String): String? =
        session.watch(files, Filter.Equals("name", name)).rows().drop(1).first().firstOrNull()?.id

    private suspend fun make(session: DeviceSession, name: String): String {
        try {
            session.add(files, mapOf("name" to name, "type" to "file"))
        } catch (e: DeviceException) {
            session.run(files, "print", mapOf("file" to name.removeSuffix(".txt"))).toList()
        }
        repeat(10) {
            idOf(session, name)?.let { return it }
            delay(500)
        }
        error("$name was not made")
    }

    private suspend fun clean(session: DeviceSession, vararg names: String) {
        for (name in names) idOf(session, name)?.let { session.remove(files, listOf(it)) }
    }

    private suspend fun contentsOf(session: DeviceSession, label: String) {
        clean(session, "rd-test-contents.txt")
        val id = make(session, "rd-test-contents.txt")
        try {
            for (size in listOf(100, 1_000, 4_000, 4_095, 4_096, 8_000, 32_000, 60_000, 61_000, 62_000, 63_000, 63_999, 64_000)) {
                val sent = text(size)
                val line = try {
                    session.set(files, id, mapOf("contents" to sent))
                    val item = session.get(files, id, listOf("contents", "size"))
                    "written, read ${item?.get("contents")?.length} chars, size ${item?.get("size")}, same ${item?.get("contents") == sent}"
                } catch (e: DeviceException) {
                    "refused: ${e.message}"
                }
                println("MEASURE $label $size: $line")
                val chunks = try {
                    session.run(files, "read", mapOf("file" to "rd-test-contents.txt", "offset" to "0", "chunk-size" to "${size + 10}")).toList()
                        .joinToString { "keys ${it.values.keys} data ${it["data"]?.length}" }
                } catch (e: DeviceException) {
                    "no read: ${e.message}"
                }
                println("MEASURE $label read of $size: $chunks")
            }
            val mixed = "строка с кириллицей\r\nвторая\r\n"
            session.set(files, id, mapOf("contents" to mixed))
            val back = session.get(files, id, listOf("contents", "size"))
            println("MEASURE $label cyrillic: same ${back?.get("contents") == mixed}, size ${back?.get("size")} of ${mixed.encodeToByteArray().size} bytes")
        } finally {
            clean(session, "rd-test-contents.txt")
        }
    }

    @Test
    fun theApiOfRouterOs7TakesAndGivesContents() = routerOs("chr", RouterOsProtocol.ApiSsl) { contentsOf(it, "chr api-ssl") }

    @Test
    fun theRestOfRouterOs7TakesAndGivesContents() = routerOs("chr", RouterOsProtocol.Rest) { contentsOf(it, "chr rest") }

    @Test
    fun theApiOfRouterOs6TakesAndGivesContents() = routerOs("chr6", RouterOsProtocol.Api) { contentsOf(it, "chr6 api") }

    @Test
    fun aSmallTextIsWrittenReadRenamedAndRemovedOverTheApi() {
        for ((prefix, protocol) in listOf("chr" to RouterOsProtocol.ApiSsl, "chr6" to RouterOsProtocol.Api)) {
            routerOs(prefix, protocol) { session ->
                clean(session, "rd-test-api.txt", "rd-test-api2.txt")
                val id = make(session, "rd-test-api.txt")
                try {
                    session.set(files, id, mapOf("contents" to "one\ntwo\n"))
                    assertEquals("one\ntwo\n", session.get(files, id, listOf("contents"))?.get("contents"), prefix)
                    if (session.device.version.startsWith("7")) {
                        session.set(files, id, mapOf("name" to "rd-test-api2.txt"))
                        assertNotNull(idOf(session, "rd-test-api2.txt"), prefix)
                        assertEquals(null, idOf(session, "rd-test-api.txt"), prefix)
                    }
                } finally {
                    clean(session, "rd-test-api.txt", "rd-test-api2.txt")
                }
                assertEquals(null, idOf(session, "rd-test-api2.txt"), prefix)
            }
        }
    }

    @Test
    fun rpcdOfOpenWrtTakesAndGivesFilesWithoutSsh() = Lab.forEachOpenWrt("host", "user", "password") { lab ->
        runBlocking {
            withTimeout(180.seconds) {
                val session = connectOpenWrt(OpenWrtLogin(lab.getValue("host"), lab.getValue("user"), lab.getValue("password"), OpenWrtProtocol.Ubus), TrustPolicy(MemoryTrustStore()) { true })
                val channel = assertNotNull(session.fileChannel, "rpcd has no file object")
                val path = "/tmp/rd-test-rpcd.txt"
                try {
                    for (size in listOf(100, 24_000, 100_000, 200_000, 262_143, 262_144, 300_000)) {
                        val sent = text(size).encodeToByteArray()
                        val line = try {
                            channel.write(path, sent)
                            val back = try {
                                channel.read(path).size.toString()
                            } catch (e: DeviceException) {
                                "read refused: ${e.message}"
                            }
                            "written whole, read $back"
                        } catch (e: DeviceException) {
                            "write refused: ${e.message}"
                        }
                        println("MEASURE ${lab.getValue("host")} rpcd $size: $line")
                    }
                    channel.write(path, "one\ntwo\n".encodeToByteArray())
                    assertEquals("one\ntwo\n", channel.read(path).decodeToString())
                    val folder = "/tmp/rd-test-rpcd-dir"
                    channel.mkdir(folder)
                    channel.rename(path, "$folder/b.txt")
                    assertEquals(listOf("b.txt"), channel.list(folder).map { it.name })
                    channel.remove("$folder/b.txt")
                    channel.remove(folder)
                    assertTrue(channel.list("/tmp").none { it.name.startsWith("rd-test-rpcd") })
                } finally {
                    runCatching { channel.remove(path) }
                    runCatching { channel.remove("/tmp/rd-test-rpcd-dir/b.txt") }
                    runCatching { channel.remove("/tmp/rd-test-rpcd-dir") }
                    session.close()
                }
            }
        }
    }
}
