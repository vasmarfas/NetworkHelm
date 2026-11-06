package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RdapTest {

    private fun target(query: String) = assertNotNull(rdapTarget(query), query)

    private fun json(text: String) = assertNotNull(parseObject(text))

    private fun rows(text: String) = rdapFacts(json(text)).map { "${it.key}=${it.value}" }

    // the IANA files and whatever else the test names; any other address is a connection that failed
    private fun registries(vararg more: Pair<String, HttpResponse>) = FakeHttp { url ->
        val known = more.toMap()
        known[url] ?: RdapSamples.files()[url]?.let { cannedReply(body = it) } ?: error("no route to $url")
    }

    @Test
    fun aQueryIsADomainAnAddressABlockOrAnAsNumber() {
        assertEquals(RdapKind.Domain, target(" Example.COM. ").kind)
        assertEquals("example.com", target(" Example.COM. ").name)
        assertEquals("www.example.com", target("https://www.example.com/path?x=1").name)
        assertEquals(RdapKind.Ip, target("8.8.8.8").kind)
        assertEquals("2001:db8::1", target("[2001:DB8:0:0:0:0:0:1]").name)
        assertEquals("8.8.8.0/24", target("8.8.8.0/24").name)
        assertEquals("2001:db8::/32", target("2001:db8::/32").name)
        for (written in listOf("AS15169", "as15169", "AS 15169", "15169")) {
            assertEquals(RdapKind.Autnum, target(written).kind, written)
            assertEquals("15169", target(written).name, written)
        }
        for (wrong in listOf("", "exa mple.com", "8.8.8.8/33", "2001:db8::/129", "99999999999", "-example.com/x")) assertNull(rdapTarget(wrong), wrong)
    }

    @Test
    fun aNameWithLettersOutsideAsciiGoesInPunycode() {
        assertEquals("xn--e1afmkfd.xn--p1ai", target("Пример.РФ").name)
        assertEquals("xn--bcher-kva.example", target("bücher.example").name)
        assertEquals("xn--p1ai", asciiDomain("рф"))
        assertEquals("Example.com", asciiDomain("Example.com"))
    }

    @Test
    fun theBootstrapPicksTheServerByTldBlockAndNumber() = runTest {
        val http = registries()
        val bootstrap = RdapBootstrap()
        suspend fun server(query: String) = bootstrap.serverFor(http, target(query))
        assertEquals("https://rdap.registry.test/com/v1/", server("example.com"))
        assertEquals("https://rdap.cctld.test/", server("пример.рф"))
        assertEquals("https://rdap.org.test/", server("example.org"))
        assertNull(server("example.dev"), "a server without HTTPS is not used")
        assertNull(server("example.zzz"))
        assertEquals("https://rdap.arin.test/registry/", server("8.8.8.8"))
        assertEquals("https://rdap.ripe.test/", server("193.0.7.1/24"))
        assertNull(server("10.0.0.1"))
        assertEquals("https://rdap.apnic.test/", server("2001:0201::1"))
        assertEquals("https://rdap.ripe.test/", server("2a00:1450::/32"))
        assertNull(server("2002::1"))
        assertEquals("https://rdap.documentation.test/", server("AS64500"))
        assertEquals("https://rdap.arin.test/registry/", server("AS15169"))
        assertEquals("https://rdap.arin.test/registry/", server("13336"))
        assertEquals("https://rdap.private.test/", server("4200000001"))
        assertNull(server("65000"))
        assertEquals(1, http.asked.count { it.endsWith("/dns.json") }, "a file is read once")
    }

    @Test
    fun aDomainIsAskedAtTheServerOfItsRegistry() = runTest {
        val http = registries("https://rdap.registry.test/com/v1/domain/example.com" to cannedReply(body = RdapSamples.domain))
        val found = assertIs<RdapResult.Found>(rdapLookup(http, target("example.com"), RdapBootstrap()))
        assertEquals("rdap.registry.test", found.server)
        assertEquals(listOf("https://data.iana.org/rdap/dns.json", "https://rdap.registry.test/com/v1/domain/example.com"), http.asked)
        assertEquals("EXAMPLE.COM", rdapFacts(found.json).first().value)
    }

    @Test
    fun withNoServerInTheFilesRdapOrgStandsInAndItsRedirectIsFollowed() = runTest {
        val http = registries(
            "https://rdap.org/domain/example.zzz" to cannedReply(302, "", "location" to "https://rdap.elsewhere.test/domain/example.zzz"),
            "https://rdap.elsewhere.test/domain/example.zzz" to cannedReply(body = RdapSamples.domain),
        )
        val found = assertIs<RdapResult.Found>(rdapLookup(http, target("example.zzz"), RdapBootstrap()))
        assertEquals("rdap.elsewhere.test", found.server)
    }

    @Test
    fun withoutTheFilesAtAllRdapOrgIsAsked() = runTest {
        val http = FakeHttp { url -> if (url == "https://rdap.org/ip/8.8.8.8") cannedReply(body = RdapSamples.network) else error("no route to $url") }
        assertIs<RdapResult.Found>(rdapLookup(http, target("8.8.8.8"), RdapBootstrap()))
        assertEquals(listOf("https://data.iana.org/rdap/ipv4.json", "https://rdap.org/ip/8.8.8.8"), http.asked)
    }

    @Test
    fun aMissingRecordARefusalAnUnreadableAnswerAndSilenceAreTold() = runTest {
        val url = "https://rdap.registry.test/com/v1/domain/example.com"
        suspend fun outcome(answer: HttpResponse) = rdapLookup(registries(url to answer), target("example.com"), RdapBootstrap())
        assertEquals("rdap.registry.test", assertIs<RdapResult.Missing>(outcome(cannedReply(404, """{"errorCode":404,"title":"Not Found"}"""))).server)
        assertEquals(429, assertIs<RdapResult.Refused>(outcome(cannedReply(429))).status)
        assertIs<RdapResult.Unreadable>(outcome(cannedReply(200, "<html><body>maintenance</body></html>")))
        assertIs<RdapResult.Unreadable>(outcome(cannedReply(200, "[1, 2]")))
        assertEquals("rdap.registry.test", assertIs<RdapResult.Silent>(rdapLookup(registries(), target("example.com"), RdapBootstrap())).server)
    }

    @Test
    fun redirectsStopAfterThreeAndNeverLeaveHttps() = runTest {
        val loop = registries("https://rdap.registry.test/com/v1/domain/example.com" to cannedReply(302, "", "location" to "https://rdap.registry.test/com/v1/domain/example.com"))
        val refused = assertIs<RdapResult.Refused>(rdapLookup(loop, target("example.com"), RdapBootstrap()))
        assertEquals(302, refused.status)
        assertEquals(4, loop.asked.count { it.startsWith("https://rdap.registry.test/") }, "the first request and three redirects")
        val downgrade = registries("https://rdap.registry.test/com/v1/domain/example.com" to cannedReply(302, "", "location" to "http://rdap.registry.test/x"))
        assertIs<RdapResult.Refused>(rdapLookup(downgrade, target("example.com"), RdapBootstrap()))
        val relative = registries(
            "https://rdap.registry.test/com/v1/domain/example.com" to cannedReply(307, "", "location" to "/other/domain/example.com"),
            "https://rdap.registry.test/other/domain/example.com" to cannedReply(body = RdapSamples.domain),
        )
        assertIs<RdapResult.Found>(rdapLookup(relative, target("example.com"), RdapBootstrap()))
    }

    @Test
    fun theFactsOfADomainFollowTheOrderTheyAreShownIn() {
        assertEquals(
            listOf(
                "domain=EXAMPLE.COM",
                "handle=2336799_DOMAIN_COM-VRSN",
                "status=client delete prohibited, client transfer prohibited, client update prohibited",
                "registered=1995-08-14T04:00:00Z",
                "expires=2026-08-13T04:00:00Z",
                "changed=2025-08-14T07:01:44Z",
                "rdap-updated=2026-11-06T09:00:00Z",
                "nameservers=A.IANA-SERVERS.NET\nB.IANA-SERVERS.NET",
                "dnssec=true",
                "registrar=Example Registrar, Inc.",
                "IANA Registrar ID=9999",
                "abuse=abuse@registrar.test",
                "registrant=REDACTED FOR PRIVACY",
                "whois-server=whois.registry.test",
            ),
            rows(RdapSamples.domain),
        )
    }

    @Test
    fun theFactsOfAnAddressBlock() {
        assertEquals(
            listOf(
                "handle=193.0.0.0 - 193.0.7.255",
                "name=EXAMPLE-NET",
                "type=ASSIGNED PA",
                "range=193.0.0.0 – 193.0.7.255",
                "cidr=193.0.0.0/21",
                "parent=193.0.0.0 - 193.255.255.255",
                "country=NL",
                "status=active",
                "registered=2004-03-17T11:19:12Z",
                "changed=2018-11-23T10:30:36Z",
                "registrant=Example Network Coordination Centre",
                "Technical, administrative=Operations Department · ops@example.test",
                "abuse=Abuse Contact · abuse@example.test",
                "whois-server=whois.ripe.net",
            ),
            rows(RdapSamples.network),
        )
    }

    @Test
    fun theFactsOfAnAutonomousSystemHaveARangeOnlyWhenItIsOne() {
        assertEquals(
            listOf("handle=AS64496", "name=EXAMPLE-AS", "type=DIRECT ALLOCATION", "country=ZZ", "status=active", "changed=2020-01-01T00:00:00Z"),
            rows(RdapSamples.autnum),
        )
        val block = rows("""{"handle":"AS64496-AS64511","startAutnum":64496,"endAutnum":64511,"name":"EXAMPLE-BLOCK"}""")
        assertTrue("as-range=64496 – 64511" in block, block.toString())
    }

    @Test
    fun anAnswerWithNothingKnownGivesNoRows() {
        assertEquals(emptyList(), rdapFacts(JsonObject(emptyMap())))
        assertEquals(emptyList(), rows("""{"entities":[{"roles":["registrar"],"vcardArray":["vcard",[["fn",{},"text",""]]]}],"events":[{"eventDate":"2020-01-01T00:00:00Z"}]}"""))
    }

    @Test
    fun theRawAnswerIsIndented() {
        val raw = rdapRaw(json(RdapSamples.autnum))
        assertTrue(raw.startsWith("{\n    \"objectClassName\": \"autnum\","), raw)
        assertTrue("\n    \"handle\": \"AS64496\"," in raw, raw)
    }
}
