package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.CannedStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhoisTest {

    private val iana = "% IANA WHOIS server\n\ndomain:       COM\n\norganisation: VeriSign Global Registry Services\n\nwhois:        whois.verisign-grs.com\n\nstatus:       ACTIVE\n"
    private val registry = "   Domain Name: EXAMPLE.COM\n   Registry Domain ID: 2336799_DOMAIN_COM-VRSN\n   Registrar WHOIS Server: whois.example-registrar.test\n"

    // sends its text and then neither ends nor closes, as a server that forgot to hang up; only close() wakes the read, with a failure
    private class Lingering(text: String) : ByteStream {
        private val bytes = text.encodeToByteArray()
        private val gone = CompletableDeferred<Unit>()
        private var sent = false
        var closed = false
            private set

        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!sent) {
                sent = true
                bytes.copyInto(buffer, offset)
                return bytes.size
            }
            gone.await()
            error("the connection was closed")
        }

        override suspend fun write(bytes: ByteArray) = Unit

        override fun close() {
            closed = true
            gone.complete(Unit)
        }
    }

    @Test
    fun ianaNamesTheRegistryAndTheRegistryNamesTheRegistrar() {
        assertEquals("whois.verisign-grs.com", whoisReferral(iana))
        assertEquals("whois.ripe.net", whoisReferral("inetnum:      193.0.0.0 - 193.255.255.255\nrefer:        whois.ripe.net\n"))
        assertNull(whoisReferral("% no such object\n"))
        assertEquals("whois.example-registrar.test", registrarWhois(registry))
        assertNull(registrarWhois("Domain Name: EXAMPLE.COM\n"))
    }

    @Test
    fun aRegistrarFieldWithNoServerIsNotTakenForTheLineBelowIt() {
        assertNull(registrarWhois("   Registrar WHOIS Server:\n   Registrar URL: http://registrar.test\n"))
        assertNull(registrarWhois("   Registrar WHOIS Server: https://registrar.test/whois\n"))
        assertNull(whoisReferral("refer:\nwhois:  \n"))
        assertEquals("whois.example-registrar.test", registrarWhois("Registrar WHOIS Server: whois.example-registrar.test.\r\n"))
    }

    @Test
    fun theQueryFollowsTheReferralsAndKeepsEveryAnswer() = runTest {
        val asked = mutableListOf<String>()
        val streams = mutableListOf<CannedStream>()
        val replies = whois(" example.com ") { host, port, _ ->
            asked += "$host:$port"
            CannedStream(
                when (host) {
                    "whois.iana.org" -> iana
                    "whois.verisign-grs.com" -> registry
                    else -> "Registrant Organization: Example\n"
                },
            ).also { streams += it }
        }
        assertEquals(listOf("whois.iana.org:43", "whois.verisign-grs.com:43", "whois.example-registrar.test:43"), asked)
        assertTrue(streams.all { it.written.toString() == "example.com\r\n" && it.closed })
        assertEquals(List(3) { WhoisEnd.Complete }, replies.map { it.end })
        val text = whoisText(replies)
        assertTrue(text.startsWith("% whois.verisign-grs.com\n   Domain Name: EXAMPLE.COM"), text)
        assertTrue("\n\n% whois.example-registrar.test\nRegistrant Organization: Example" in text, text)
        assertTrue("IANA" !in text, "the referral of IANA is dropped once the registry answered")
    }

    @Test
    fun anAnswerWithoutAReferralIsIanasOwn() = runTest {
        val replies = whois("nonexistent.zzz") { _, _, _ -> CannedStream("% This query returned 0 objects.\n") }
        assertEquals("% whois.iana.org\n% This query returned 0 objects.", whoisText(replies))
    }

    @Test
    fun aRegistrarThatCannotBeReachedLeavesTheRegistryAnswerAndIsNamed() = runTest {
        val replies = whois("example.com") { host, _, _ ->
            when (host) {
                "whois.iana.org" -> CannedStream(iana)
                "whois.verisign-grs.com" -> CannedStream(registry)
                else -> error("connection refused")
            }
        }
        assertEquals(listOf(WhoisEnd.Complete, WhoisEnd.Complete, WhoisEnd.Unreachable), replies.map { it.end })
        assertEquals("whois.example-registrar.test", replies.last().server)
        assertEquals("% whois.verisign-grs.com\n${registry.trimEnd()}", whoisText(replies))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aRegistrarThatNeverHangsUpKeepsWhatItSentAfterItsOwnTimeout() = runTest {
        val lingering = Lingering("Registrant Organization: Example\n")
        val replies = whois("example.com", timeoutMillis = 5_000) { host, _, _ ->
            when (host) {
                "whois.iana.org" -> CannedStream(iana)
                "whois.verisign-grs.com" -> CannedStream(registry)
                else -> lingering
            }
        }
        val registrar = replies.last()
        assertEquals(WhoisEnd.Cut, registrar.end)
        assertEquals("Registrant Organization: Example\n", registrar.text)
        assertTrue(lingering.closed)
        assertEquals(5_000L, currentTime, "the others answered at once, the registrar was waited for its own timeout")
        assertTrue("% whois.example-registrar.test\nRegistrant Organization: Example" in whoisText(replies))
    }

    @Test
    fun aRegistryThatStallsAfterItsAnswerStillSendsUsOnToTheRegistrar() = runTest {
        val stalling = Lingering(registry)
        val replies = whois("example.com", timeoutMillis = 5_000) { host, _, _ ->
            when (host) {
                "whois.iana.org" -> CannedStream(iana)
                "whois.verisign-grs.com" -> stalling
                else -> CannedStream("Registrant Organization: Example\n")
            }
        }
        assertEquals(listOf(WhoisEnd.Complete, WhoisEnd.Cut, WhoisEnd.Complete), replies.map { it.end })
        assertEquals("whois.example-registrar.test", replies.last().server)
    }

    @Test
    fun aServerThatClosesWithNoDataIsSilentAndEndsTheChain() = runTest {
        val replies = whois("example.com") { host, _, _ -> if (host == "whois.iana.org") CannedStream(iana) else CannedStream("") }
        assertEquals(listOf("whois.iana.org", "whois.verisign-grs.com"), replies.map { it.server })
        assertEquals(WhoisEnd.Silent, replies.last().end)
        assertEquals("% whois.iana.org\n${iana.trimEnd()}", whoisText(replies))
    }

    @Test
    fun ianaThatCannotBeReachedGivesOnlyThatNote() = runTest {
        val replies = whois("example.com") { _, _, _ -> error("no route") }
        assertEquals(listOf(WhoisEnd.Unreachable), replies.map { it.end })
        assertEquals("", whoisText(replies))
    }

    @Test
    fun aNameInLettersOutsideAsciiIsAskedInPunycode() = runTest {
        val streams = mutableListOf<CannedStream>()
        whois("Пример.РФ") { _, _, _ -> CannedStream("% nothing\n").also { streams += it } }
        assertEquals("xn--e1afmkfd.xn--p1ai\r\n", streams.single().written.toString())
    }
}
