package com.vasmarfas.networkhelm.nettools

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IpInfoTest {

    private val ipwho = """{"ip":"193.0.7.1","success":true,"type":"IPv4","country":"Netherlands","country_code":"NL","region":"North Holland","city":"Amsterdam","latitude":52.3740,"longitude":4.8897,"is_eu":true,"postal":"1012","calling_code":"31","connection":{"asn":3333,"org":"Example Org","isp":"Example ISP","domain":"example.test"},"timezone":{"id":"Europe/Amsterdam","utc":"+01:00","current_time":"2026-11-06T10:00:00+01:00"}}"""
    private val ipapi = """{"ip":"193.0.7.1","version":"IPv4","city":"Amsterdam","region":"North Holland","country_name":"Netherlands","country_code":"NL","postal":"1012","latitude":52.374,"longitude":4.8897,"timezone":"Europe/Amsterdam","utc_offset":"+0100","org":"Example Org","asn":"AS3333"}"""

    private class Names(private val addresses: List<String>) : HostOps {
        override suspend fun ping(host: String, timeoutMillis: Int, ttl: Int?, size: Int?, dontFragment: Boolean) = PingReply(null, null, null)

        override suspend fun wake(packet: ByteArray, broadcast: String, port: Int) = Unit

        override suspend fun resolve(host: String) = addresses
    }

    // the records the resolver knows as (name, type, data); any other question has an answer with no records in it
    private fun dnsOf(vararg known: Triple<String, Int, String>): suspend (String, String, Int) -> DnsResponse? = { _, name, type ->
        DnsResponse(0, 0, false, known.filter { it.first == name && it.second == type }.map { DnsRecord(name, type, 60, it.third) }, emptyList())
    }

    // the IANA files and the routes given; any other address is a connection that failed
    private fun internet(vararg routes: Pair<String, String>) = FakeHttp { url ->
        val known = routes.toMap()
        (known[url] ?: RdapSamples.files()[url])?.let { cannedReply(body = it) } ?: error("no route to $url")
    }

    private fun IpSection?.facts() = assertNotNull(this).rows.map { "${it.key}=${it.value}" }

    private val network = "https://rdap.ripe.test/ip/193.0.7.1" to RdapSamples.network

    @Test
    fun anAddressGetsEveryPartFromItsOwnSource() = runTest {
        val http = internet(network, "https://ipwho.is/193.0.7.1" to ipwho)
        val dns = dnsOf(
            Triple("1.7.0.193.in-addr.arpa", 12, "host.example.test."),
            Triple("1.7.0.193.origin.asn.cymru.com", 16, "3333 | 193.0.0.0/21 | NL | ripencc | 1993-09-01"),
            Triple("AS3333.asn.cymru.com", 16, "3333 | NL | ripencc | 1993-09-01 | EXAMPLE-AS, NL"),
        )
        val sections = ipInfo(http, null, " 193.0.7.1 ", bootstrap = RdapBootstrap(), dns = dns).toList().associateBy { it.part }
        assertNull(sections[IpPart.Address], "the address was typed, there is nothing to say of where it came from")
        assertEquals(listOf("1.1.1.1"), sections[IpPart.Reverse]?.hosts)
        assertEquals(listOf("name=host.example.test"), sections[IpPart.Reverse].facts())
        assertEquals(listOf("asn.cymru.com"), sections[IpPart.Routing]?.hosts)
        assertEquals(
            listOf("asn=AS3333", "prefix=193.0.0.0/21", "country=NL", "registry=RIPENCC", "allocated=1993-09-01", "as-name=EXAMPLE-AS, NL"),
            sections[IpPart.Routing].facts(),
        )
        assertEquals(listOf("rdap.ripe.test"), sections[IpPart.Network]?.hosts)
        assertEquals(networkOwner(assertNotNull(parseObject(RdapSamples.network))).map { "${it.key}=${it.value}" }, sections[IpPart.Network].facts())
        assertEquals(listOf("ipwho.is"), sections[IpPart.Location]?.hosts)
        assertEquals(
            listOf(
                "country=Netherlands (NL)", "region=North Holland", "city=Amsterdam", "postal-code=1012", "coordinates=52.3740, 4.8897",
                "timezone=Europe/Amsterdam (+01:00)", "local-time=2026-11-06T10:00:00+01:00", "calling-code=+31", "eu=true",
            ),
            sections[IpPart.Location].facts(),
        )
        assertEquals(listOf("isp=Example ISP", "organisation=Example Org", "asn=AS3333", "domain=example.test"), sections[IpPart.Connection].facts())
    }

    @Test
    fun theOwnerOfABlockIsItsRegistrantWithTheAbuseAndAdminContactsAndDates() {
        assertEquals(
            listOf(
                "network-name=EXAMPLE-NET",
                "description=Example network\nCity, Country",
                "organisation=Example Network Coordination Centre",
                "postal=Example Street 1, Example City, EXAMPLELAND",
                "abuse-email=abuse@example.test",
                "abuse-phone=+31.20.5550100",
                "admin-email=ops@example.test",
                "range=193.0.0.0 – 193.0.7.255",
                "cidr=193.0.0.0/21",
                "country=NL",
                "registry=RIPE NCC",
                "registered=2004-03-17",
                "changed=2018-11-23",
            ),
            networkOwner(assertNotNull(parseObject(RdapSamples.network))).map { "${it.key}=${it.value}" },
        )
    }

    @Test
    fun aNameIsResolvedByTheSystemAndItsFirstIpv4IsTheSubject() = runTest {
        val http = internet(
            "https://rdap.ripe.test/ip/193.0.7.9" to RdapSamples.network,
            "https://ipwho.is/193.0.7.9" to ipwho,
        )
        val sections = ipInfo(http, Names(listOf("2001:db8::1%eth0", "193.0.7.9")), "example.test", bootstrap = RdapBootstrap(), dns = dnsOf()).toList().associateBy { it.part }
        assertEquals(emptyList(), sections[IpPart.Address]?.hosts, "the system resolver has no host to name")
        assertEquals(listOf("host=example.test", "ipv6=2001:db8::1", "ipv4=193.0.7.9"), sections[IpPart.Address].facts())
        assertTrue("https://ipwho.is/193.0.7.9" in http.asked)
        assertEquals("rdap.ripe.test", sections[IpPart.Network]?.hosts?.single())
    }

    @Test
    fun withoutTheSystemANameIsResolvedOverDns() = runTest {
        val http = internet("https://ipwho.is/193.0.7.9" to ipwho)
        val dns = dnsOf(Triple("example.test", 1, "193.0.7.9"), Triple("example.test", 28, "2001:db8::9"))
        val sections = ipInfo(http, null, "https://example.test/page", bootstrap = RdapBootstrap(), dns = dns).toList().associateBy { it.part }
        assertEquals(listOf("1.1.1.1"), sections[IpPart.Address]?.hosts)
        assertEquals(listOf("host=example.test", "ipv4=193.0.7.9", "ipv6=2001:db8::9"), sections[IpPart.Address].facts())
        assertTrue("https://ipwho.is/193.0.7.9" in http.asked)
    }

    @Test
    fun aNameThatNobodyResolvesStopsAfterTheAddressPart() = runTest {
        val http = internet()
        val silent = ipInfo(http, null, "nowhere.test", bootstrap = RdapBootstrap(), dns = { _, _, _ -> null }).toList()
        assertEquals(listOf(IpPart.Address), silent.map { it.part })
        assertTrue(silent.single().failed)
        val unknown = ipInfo(http, Names(emptyList()), "nowhere.test", bootstrap = RdapBootstrap(), dns = dnsOf()).toList()
        assertEquals(listOf(IpPart.Address), unknown.map { it.part })
        assertFalse(unknown.single().failed)
        assertTrue(unknown.single().rows.isEmpty())
        assertEquals(emptyList(), http.asked, "nothing is asked about an address that is not there")
    }

    @Test
    fun anEmptyQueryAsksForTheOwnAddressAndTheOtherFamily() = runTest {
        val own = ipwho.replace("193.0.7.1", "193.0.7.50")
        val http = internet("https://ipwho.is/" to own, "https://api6.ipify.org" to "2001:db8::50\n")
        val sections = ipInfo(http, null, "", bootstrap = RdapBootstrap(), dns = dnsOf()).toList().associateBy { it.part }
        assertEquals(listOf("ipwho.is", "api6.ipify.org"), sections[IpPart.Address]?.hosts)
        assertEquals(listOf("ipv4=193.0.7.50", "ipv6=2001:db8::50"), sections[IpPart.Address].facts())
        assertEquals("country=Netherlands (NL)", sections[IpPart.Location].facts().first())
        assertEquals(1, http.asked.count { it.startsWith("https://ipwho.is/") }, "the answer about the own address is not asked for again")
        assertTrue("https://rdap.ripe.test/ip/193.0.7.50" in http.asked)
    }

    @Test
    fun anOwnAddressWithoutAnotherFamilyIsJustTheOne() = runTest {
        val http = internet("https://ipwho.is/" to ipwho)
        val sections = ipInfo(http, null, "", bootstrap = RdapBootstrap(), dns = dnsOf()).toList().associateBy { it.part }
        assertEquals(listOf("ipwho.is"), sections[IpPart.Address]?.hosts)
        assertEquals(listOf("ipv4=193.0.7.1"), sections[IpPart.Address].facts())
    }

    @Test
    fun everySourceFailsAlone() = runTest {
        val sections = ipInfo(internet(), null, "193.0.7.1", bootstrap = RdapBootstrap(), dns = { _, _, _ -> null }).toList().associateBy { it.part }
        assertEquals(setOf(IpPart.Reverse, IpPart.Routing, IpPart.Network, IpPart.Location), sections.keys)
        assertTrue(sections.values.all { it.failed && it.rows.isEmpty() })
        assertEquals(listOf("ipwho.is", "ipapi.co"), sections[IpPart.Location]?.hosts)
        assertEquals(listOf("rdap.ripe.test"), sections[IpPart.Network]?.hosts, "the server that IANA names and that did not answer")
    }

    @Test
    fun oneThatFailsLeavesTheOthersTheirRows() = runTest {
        val http = internet("https://ipwho.is/193.0.7.1" to ipwho)
        val dns = dnsOf(Triple("1.7.0.193.in-addr.arpa", 12, "host.example.test."))
        val sections = ipInfo(http, null, "193.0.7.1", bootstrap = RdapBootstrap(), dns = dns).toList().associateBy { it.part }
        assertFalse(sections.getValue(IpPart.Reverse).failed)
        assertTrue(sections.getValue(IpPart.Network).failed)
        assertTrue(sections.getValue(IpPart.Location).rows.isNotEmpty())
        assertTrue(sections.getValue(IpPart.Routing).rows.isEmpty() && !sections.getValue(IpPart.Routing).failed, "an answer with nothing in it is not a failure")
    }

    @Test
    fun ipapiStandsInWhenIpwhoRefuses() = runTest {
        val http = internet(
            "https://ipwho.is/193.0.7.1" to """{"success":false,"message":"Invalid IP address"}""",
            "https://ipapi.co/193.0.7.1/json/" to ipapi,
        )
        val sections = ipInfo(http, null, "193.0.7.1", bootstrap = RdapBootstrap(), dns = dnsOf()).toList().associateBy { it.part }
        assertEquals(listOf("ipapi.co"), sections[IpPart.Location]?.hosts)
        assertEquals(
            listOf("country=Netherlands (NL)", "region=North Holland", "city=Amsterdam", "postal-code=1012", "coordinates=52.374, 4.8897", "timezone=Europe/Amsterdam (+0100)"),
            sections[IpPart.Location].facts(),
        )
        assertEquals(listOf("organisation=Example Org", "asn=AS3333"), sections[IpPart.Connection].facts())
    }

    @Test
    fun anIpv6AddressIsLookedUpInTheCymruZoneOfItsFamily() = runTest {
        val pointer = assertNotNull(reverseName("2001:db8::1"))
        val dns = dnsOf(Triple(pointer.removeSuffix(".ip6.arpa") + ".origin6.asn.cymru.com", 16, "64496 | 2001:db8::/32 | ZZ | example | 2000-01-01"))
        val sections = ipInfo(internet(), null, "2001:0DB8::1", bootstrap = RdapBootstrap(), dns = dns).toList().associateBy { it.part }
        assertEquals(listOf("asn=AS64496", "prefix=2001:db8::/32", "country=ZZ", "registry=EXAMPLE", "allocated=2000-01-01"), sections[IpPart.Routing].facts())
    }

    @Test
    fun cymruAnswersAreFieldsSeparatedByBars() {
        assertEquals(listOf("15169", "8.8.8.0/24", "US", "arin", "1992-12-01"), cymruFields("15169 | 8.8.8.0/24 | US | arin | 1992-12-01"))
        assertEquals(listOf("15169", "US", "arin", "2000-03-30", "GOOGLE, US"), cymruFields("15169 | US | arin | 2000-03-30 | GOOGLE, US"))
    }

    @Test
    fun severalOriginsAreAllNamedAndTheFirstGivesTheName() = runTest {
        val dns = dnsOf(
            Triple("1.7.0.193.origin.asn.cymru.com", 16, "3333 1234 | 193.0.0.0/21 | NL | ripencc | 1993-09-01"),
            Triple("AS3333.asn.cymru.com", 16, "3333 | NL | ripencc | 1993-09-01 | EXAMPLE-AS, NL"),
        )
        val sections = ipInfo(internet(), null, "193.0.7.1", bootstrap = RdapBootstrap(), dns = dns).toList().associateBy { it.part }
        assertEquals("asn=AS3333, AS1234", sections[IpPart.Routing].facts().first())
        assertEquals("as-name=EXAMPLE-AS, NL", sections[IpPart.Routing].facts().last())
    }
}
