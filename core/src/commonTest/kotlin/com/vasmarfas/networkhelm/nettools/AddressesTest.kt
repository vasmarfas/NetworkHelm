package com.vasmarfas.networkhelm.nettools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AddressesTest {

    @Test
    fun anIpv4AddressIsReadAndWrittenBack() {
        val ip = assertNotNull(Ipv4.parse("192.168.1.10"))
        assertEquals("192.168.1.10", Ipv4.format(ip))
        assertEquals("11000000.10101000.00000001.00001010", Ipv4.binary(ip))
        assertNull(Ipv4.parse("256.1.1.1"))
        assertNull(Ipv4.parse("1.2.3"))
    }

    @Test
    fun aSubnetGivesItsNetworkMaskBroadcastAndHosts() {
        val s = assertNotNull(Ipv4.parseSubnet("192.168.1.10/24"))
        assertEquals("192.168.1.0", Ipv4.format(s.network))
        assertEquals("192.168.1.255", Ipv4.format(s.broadcast))
        assertEquals("255.255.255.0", Ipv4.format(s.mask))
        assertEquals("0.0.0.255", Ipv4.format(s.wildcard))
        assertEquals(254L, s.usableHosts)
        assertEquals(256L, s.totalAddresses)
        assertEquals("192.168.1.1", Ipv4.format(s.firstHost))
        assertEquals("192.168.1.254", Ipv4.format(s.lastHost))
        assertEquals('C', s.ipClass)
    }

    @Test
    fun thePrefixComesAsANumberAMaskOrTheClassOfABareAddress() {
        assertEquals(20, Ipv4.parseSubnet("172.16.5.4 255.255.240.0")?.prefix)
        assertEquals(20, Ipv4.parseSubnet("172.16.5.4/255.255.240.0")?.prefix)
        assertEquals(8, Ipv4.parseSubnet("10.1.2.3")?.prefix)
        assertNull(Ipv4.maskToPrefix(assertNotNull(Ipv4.parse("255.0.255.0"))))
        assertNull(Ipv4.parseSubnet("10.0.0.0/33"))
        assertEquals(2L, Ipv4.parseSubnet("10.0.0.0/31")?.usableHosts)
        assertEquals(1L, Ipv4.parseSubnet("10.0.0.7/32")?.usableHosts)
    }

    @Test
    fun anIpv6AddressIsCompressedAndExpanded() {
        val a = assertNotNull(Ipv6Address.parse("2001:0db8:0000:0000:0000:0000:0000:0001"))
        assertEquals("2001:db8::1", a.compressed())
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", a.expanded())
        assertEquals("::", Ipv6Address.parse("::")?.compressed())
        assertEquals("::1", Ipv6Address.parse("0:0:0:0:0:0:0:1")?.compressed())
        assertEquals("fe80::1:0:0:1", Ipv6Address.parse("fe80:0:0:0:1:0:0:1")?.compressed())
        assertEquals("::ffff:c000:280", Ipv6Address.parse("::ffff:192.0.2.128")?.compressed())
    }

    @Test
    fun aBrokenIpv6AddressIsRefused() {
        assertNull(Ipv6Address.parse("2001::db8::1"))
        assertNull(Ipv6Address.parse("12345::1"))
        assertNull(Ipv6Address.parse("1:2:3:4:5:6:7:8:9"))
        assertNull(Ipv6Address.parseWithPrefix("2001:db8::/129"))
    }

    @Test
    fun anIpv6PrefixSpansItsFirstAndLastAddress() {
        val (a, p) = assertNotNull(Ipv6Address.parseWithPrefix("2001:db8:abcd:1234::5/64"))
        assertEquals(64, p)
        assertEquals("2001:db8:abcd:1234::", a.withPrefix(p).compressed())
        assertEquals("2001:db8:abcd:1234:ffff:ffff:ffff:ffff", a.lastInPrefix(p).compressed())
        assertEquals(64, Ipv6Address.parseWithPrefix("fe80::1")?.second)
    }

    @Test
    fun anAddressTurnsIntoTheNameOfItsReverseLookup() {
        assertEquals("4.3.2.1.in-addr.arpa", reverseName("1.2.3.4"))
        assertEquals("1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa", reverseName("2001:db8::1"))
        assertNull(reverseName("example.com"))
    }

    @Test
    fun aSubnetWritesItselfAsItsNetworkAndPrefix() {
        assertEquals("192.168.1.0/24", assertNotNull(Ipv4.parseSubnet("192.168.1.10/24")).cidr)
        assertEquals("10.0.0.7/32", assertNotNull(Ipv4.parseSubnet("10.0.0.7/32")).cidr)
    }

    @Test
    fun aRangeIsReadInEveryForm() {
        fun range(text: String) = Ipv4.parseRange(text)?.let { (first, last) -> "${Ipv4.format(first)}-${Ipv4.format(last)}" }
        assertEquals("192.168.1.10-192.168.5.110", range("192.168.1.10-192.168.5.110"))
        assertEquals("192.168.1.10-192.168.1.100", range("192.168.1.10-100"))
        assertEquals("192.168.1.10-192.168.1.100", range(" 192.168.1.10 - 100 "))
        assertEquals("192.168.1.10-192.168.1.100", range("192.168.1.10 - 192.168.1.100"))
        assertEquals("10.0.0.5-10.0.0.5", range("10.0.0.5-5"))
        assertEquals("192.168.1.0-192.168.1.255", range("192.168.1.77/24"))
        assertEquals("192.168.1.0-192.168.1.255", range("192.168.1.77 255.255.255.0"))
        assertEquals("192.168.1.0-192.168.1.255", range("192.168.1.77/255.255.255.0"))
        assertEquals("10.1.2.3-10.1.2.3", range("10.1.2.3"))
        assertEquals("0.0.0.0-255.255.255.255", range("0.0.0.0/0"))
    }

    @Test
    fun aBrokenRangeIsRefused() {
        val broken = listOf(
            "", "-", "10.0.0.1-", "-5", "10.0.0.9-10.0.0.1", "10.0.0.9-5", "10.0.0.1-256", "10.0.0.1-1000", "10.0.0.1--5", "10.0.0.1-5-9",
            "10.0.0.1/8-9", "10.0.0.1-+5", "10.0.0.1-5.5", "10.0.0.1-x", "10.0.0.256-10.0.0.9", "nonsense", "2001:db8::1-2001:db8::9",
        )
        for (text in broken) assertNull(Ipv4.parseRange(text), text)
    }

    @Test
    fun aRangeIsCoveredByTheFewestBlocks() {
        fun cover(first: String, last: String) = Ipv4.cover(assertNotNull(Ipv4.parse(first)), assertNotNull(Ipv4.parse(last))).map { it.cidr }
        assertEquals(
            listOf(
                "192.168.1.10/31", "192.168.1.12/30", "192.168.1.16/28", "192.168.1.32/27", "192.168.1.64/26", "192.168.1.128/26",
                "192.168.1.192/27", "192.168.1.224/28", "192.168.1.240/29", "192.168.1.248/31", "192.168.1.250/32",
            ),
            cover("192.168.1.10", "192.168.1.250"),
        )
        assertEquals(listOf("192.168.0.0/16"), cover("192.168.0.0", "192.168.255.255"))
        assertEquals(listOf("10.0.0.5/32"), cover("10.0.0.5", "10.0.0.5"))
        assertEquals(listOf("0.0.0.0/0"), cover("0.0.0.0", "255.255.255.255"))
        assertEquals(listOf("255.255.255.254/31"), cover("255.255.255.254", "255.255.255.255"))
        assertEquals(listOf("10.0.0.255/32", "10.0.1.0/32"), cover("10.0.0.255", "10.0.1.0"))
        assertEquals(emptyList(), cover("10.0.0.9", "10.0.0.1"))
    }

    @Test
    fun theBlocksOfARangeAddUpToItAndFollowOneAnother() {
        val (first, last) = assertNotNull(Ipv4.parseRange("172.16.3.7-172.16.200.99"))
        val blocks = Ipv4.cover(first, last)
        assertEquals(last - first + 1, blocks.sumOf { it.totalAddresses })
        assertEquals(first, blocks.first().network)
        assertEquals(last, blocks.last().broadcast)
        blocks.zipWithNext().forEach { (a, b) -> assertEquals(a.broadcast + 1, b.network) }
    }

    @Test
    fun theSmallestNetworkHoldsBothEndsOfARange() {
        fun enclosing(first: String, last: String) = Ipv4.enclosing(assertNotNull(Ipv4.parse(first)), assertNotNull(Ipv4.parse(last)))
        assertEquals("192.168.0.0/21", enclosing("192.168.1.10", "192.168.5.110").cidr)
        assertEquals("192.168.1.0/25", enclosing("192.168.1.10", "192.168.1.100").cidr)
        assertEquals("10.0.0.5/32", enclosing("10.0.0.5", "10.0.0.5").cidr)
        assertEquals("0.0.0.0/0", enclosing("0.0.0.0", "255.255.255.255").cidr)
        assertEquals("10.0.0.0/23", enclosing("10.0.0.255", "10.0.1.0").cidr)
        val wide = enclosing("192.168.1.10", "192.168.5.110")
        assertEquals("255.255.248.0", Ipv4.format(wide.mask))
        assertEquals("192.168.7.255", Ipv4.format(wide.broadcast))
    }
}
