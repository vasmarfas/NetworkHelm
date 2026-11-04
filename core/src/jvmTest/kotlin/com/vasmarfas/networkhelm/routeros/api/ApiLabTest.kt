package com.vasmarfas.networkhelm.routeros.api

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.transport.CertificateMismatchException
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.transport.openTls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ApiLabTest {

    private val list = "rd-test-api"

    private fun router(prefix: String, block: suspend CoroutineScope.(ApiConnection) -> Unit) = runBlocking {
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password")
        withTimeout(60.seconds) {
            val api = ApiConnection(openTcp(lab.getValue("$prefix.host"), 8728), ApiEncoding.Utf8, this)
            try {
                api.login(lab.getValue("$prefix.user"), lab.getValue("$prefix.password"))
                block(api)
            } finally {
                api.close()
            }
        }
    }

    private suspend fun ApiConnection.removeTestEntries() {
        call("/ip/firewall/address-list/print", "=.proplist=.id", "?list=$list").rows
            .forEach { call("/ip/firewall/address-list/remove", "=.id=${it.getValue(".id")}") }
    }

    @Test
    fun plainLoginReadsResources() = router("chr") { api ->
        val resource = api.call("/system/resource/print", "=.proplist=version,architecture-name").rows.single()
        assertTrue(resource.getValue("version").startsWith("7."), resource.toString())
    }

    @Test
    fun plainLoginWorksOnRouterOs6() = router("chr6") { api ->
        val version = api.call("/system/resource/print").rows.single().getValue("version")
        assertTrue(version.startsWith("6."), version)
    }

    @Test
    fun challengeLoginWorksOnRouterOs642() = runBlocking<Unit> {
        val lab = Lab.require("chr642.host", "chr642.user", "chr642.password")
        withTimeout(60.seconds) {
            suspend fun version(password: String): String {
                val api = ApiConnection(openTcp(lab.getValue("chr642.host"), 8728), ApiEncoding.Utf8, this)
                try {
                    api.login(lab.getValue("chr642.user"), password)
                    return api.call("/system/resource/print").rows.single().getValue("version")
                } finally {
                    api.close()
                }
            }
            val version = version(lab.getValue("chr642.password"))
            assertTrue(version.startsWith("6.42"), version)
            assertFailsWith<ApiTrapException> { version(lab.getValue("chr642.password").reversed()) }
        }
    }

    @Test
    fun tlsRemembersTheCertificateAndRejectsAnotherOne() = runBlocking {
        val lab = Lab.require("chr.host", "chr.user", "chr.password")
        val host = lab.getValue("chr.host")
        val first = openTls(host, 8729, pinnedSha256 = null)
        val seen = first.certificateSha256
        first.close()
        assertEquals(64, seen.length)

        val pinned = openTls(host, 8729, pinnedSha256 = seen)
        val api = ApiConnection(pinned, ApiEncoding.Utf8, this)
        try {
            api.login(lab.getValue("chr.user"), lab.getValue("chr.password"))
            assertTrue(api.call("/system/identity/print").rows.single().containsKey("name"))
        } finally {
            api.close()
        }

        val wrong = seen.reversed()
        val error = assertFailsWith<CertificateMismatchException> { openTls(host, 8729, pinnedSha256 = wrong) }
        assertEquals(seen, error.actual)
    }

    @Test
    fun concurrentCommandsShareOneConnection() = concurrentCommands("chr")

    @Test
    fun concurrentCommandsShareOneConnectionOnRouterOs6() = concurrentCommands("chr6")

    private fun concurrentCommands(prefix: String) = router(prefix) { api ->
        val interfaces = async { api.call("/interface/print", "=.proplist=name,type").rows }
        val identity = async { api.call("/system/identity/print").rows }
        assertTrue(interfaces.await().isNotEmpty())
        assertTrue(identity.await().single().getValue("name").isNotEmpty())
    }

    @Test
    fun listenReportsChangesAndRemovalAsDead() = listenUntilDead("chr")

    @Test
    fun listenReportsChangesAndRemovalAsDeadOnRouterOs6() = listenUntilDead("chr6")

    private fun listenUntilDead(prefix: String) = router(prefix) { api ->
        api.removeTestEntries()
        val events = Channel<Map<String, String>>(Channel.UNLIMITED)
        val listener = launch { api.command(listOf("/ip/firewall/address-list/listen")).collect { events.send(it.attributes) } }
        suspend fun next(id: String): Map<String, String> {
            while (true) events.receive().takeIf { it[".id"] == id }?.let { return it }
        }
        try {
            delay(500)
            val id = api.call("/ip/firewall/address-list/add", "=list=$list", "=address=192.0.2.1", "=comment=rd-test").done.getValue("ret")
            assertEquals("rd-test", next(id)["comment"])
            api.call("/ip/firewall/address-list/set", "=.id=$id", "=comment=rd-test-changed")
            assertEquals("rd-test-changed", next(id)["comment"])
            api.call("/ip/firewall/address-list/remove", "=.id=$id")
            assertEquals("true", next(id)[".dead"])
        } finally {
            listener.cancel()
            api.removeTestEntries()
        }
    }

    @Test
    fun streamingCommandStopsWhenTheCollectorDoes() = streamingCommand("chr")

    @Test
    fun streamingCommandStopsWhenTheCollectorDoesOnRouterOs6() = streamingCommand("chr6")

    private fun streamingCommand(prefix: String) = router(prefix) { api ->
        val replies = api.command(listOf("/ping", "=address=127.0.0.1", "=interval=200ms")).take(3).toList()
        assertEquals(listOf("0", "1", "2"), replies.map { it.attributes["seq"] })
        assertTrue(api.call("/system/identity/print").rows.isNotEmpty())
    }

    @Test
    fun trapCarriesTheRouterMessage() = trapMessage("chr")

    @Test
    fun trapCarriesTheRouterMessageOnRouterOs6() = trapMessage("chr6")

    private fun trapMessage(prefix: String) = router(prefix) { api ->
        val error = assertFailsWith<ApiTrapException> {
            api.call("/ip/firewall/address-list/add", "=list=$list", "=address=192.0.2.1", "=rd-test-unknown=1")
        }
        assertTrue(error.message.orEmpty().isNotEmpty())
        api.removeTestEntries()
    }

    @Test
    fun routerOs6HasNoConsoleInspect() = router("chr6") { api ->
        val error = assertFailsWith<ApiTrapException> { api.call("/console/inspect", "=request=child", "=path=ip") }
        assertEquals("no such command", error.message)
    }

    @Test
    fun emptyResultArrivesAsEmptyThenDoneOnTheWire() = emptyResult("chr", listOf("!empty", "!done"))

    @Test
    fun emptyResultIsOnlyDoneOnRouterOs6() = emptyResult("chr6", listOf("!done"))

    private fun emptyResult(prefix: String, expected: List<String>) = runBlocking {
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password")
        val stream = openTcp(lab.getValue("$prefix.host"), 8728)
        try {
            val reader = SentenceReader(stream::read)
            suspend fun exchange(vararg words: String): List<String> {
                stream.write(encodeSentence(words.map { it.encodeToByteArray() }))
                val types = mutableListOf<String>()
                do {
                    val type = reader.next().orEmpty().first().decodeToString()
                    types += type
                } while (type != "!done" && type != "!trap")
                return types
            }
            assertEquals(listOf("!done"), exchange("/login", "=name=${lab.getValue("$prefix.user")}", "=password=${lab.getValue("$prefix.password")}"))
            assertEquals(expected, exchange("/ip/firewall/address-list/print", "?list=rd-test-none"))
        } finally {
            stream.close()
        }
    }
}
