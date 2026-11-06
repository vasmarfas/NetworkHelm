package com.vasmarfas.networkhelm.nettools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SubnetsTest {

    private fun network(text: String) = assertNotNull(Subnets.network(text), text)

    private fun plan(first: String, last: String, vararg needs: Int) =
        Subnets.allocate(assertNotNull(Ipv4.parse(first)), assertNotNull(Ipv4.parse(last)), needs.toList())

    private fun summary(text: String) = Subnets.summarize(Subnets.items(text).map { assertNotNull(Ipv4.parseRange(it), it) }).map { it.cidr }

    @Test
    fun aNetworkComesFromEveryFormOfTheCalculatorAndFromARangeThatIsOneBlock() {
        assertEquals("192.168.1.0/24", network("192.168.1.77/24").cidr)
        assertEquals("192.168.1.0/24", network("192.168.1.77 255.255.255.0").cidr)
        assertEquals("10.0.0.0/8", network("10.1.2.3").cidr)
        assertEquals("192.168.1.0/24", network("192.168.1.0-192.168.1.255").cidr)
        assertEquals("192.168.1.64/26", network("192.168.1.64-127").cidr)
        assertNull(Subnets.network("192.168.1.10-100"))
        assertNull(Subnets.network("nonsense"))
    }

    @Test
    fun aSpanIsAnyNetworkOrRange() {
        assertEquals("192.168.1.10-192.168.1.100", assertNotNull(Subnets.span("192.168.1.10-100")).let { (a, b) -> "${Ipv4.format(a)}-${Ipv4.format(b)}" })
        assertEquals("10.0.0.0-10.255.255.255", assertNotNull(Subnets.span("10.1.2.3")).let { (a, b) -> "${Ipv4.format(a)}-${Ipv4.format(b)}" })
        assertNull(Subnets.span("192.168.1.10-5"))
    }

    @Test
    fun aNetworkIsCutIntoPartsByTheirNumberOrByAPrefix() {
        val whole = network("192.168.0.0/24")
        assertEquals(24, Subnets.prefixFor(whole, 1))
        assertEquals(25, Subnets.prefixFor(whole, 2))
        assertEquals(26, Subnets.prefixFor(whole, 3))
        assertEquals(26, Subnets.prefixFor(whole, 4))
        assertEquals(27, Subnets.prefixFor(whole, 5))
        assertEquals(32, Subnets.prefixFor(whole, 256))
        assertNull(Subnets.prefixFor(whole, 257))
        assertNull(Subnets.prefixFor(whole, 0))
        assertNull(Subnets.prefixFor(whole, -3))
        assertEquals(listOf("192.168.0.0/26", "192.168.0.64/26", "192.168.0.128/26", "192.168.0.192/26"), Subnets.split(whole, 26).map { it.cidr }.toList())
        assertEquals(listOf("192.168.0.0/24"), Subnets.split(whole, 24).map { it.cidr }.toList())
        assertEquals(256, Subnets.split(whole, 32).count())
        assertEquals(emptyList(), Subnets.split(whole, 23).toList())
        assertEquals(emptyList(), Subnets.split(whole, 33).toList())
    }

    @Test
    fun aBigNetworkIsCutOnlyAsFarAsTheRowsAreTaken() {
        val parts = Subnets.split(network("10.0.0.0/8"), 32).take(3).map { it.cidr }.toList()
        assertEquals(listOf("10.0.0.0/32", "10.0.0.1/32", "10.0.0.2/32"), parts)
        assertEquals("10.0.0.3/32", Subnets.split(network("10.0.0.0/30"), 32).last().cidr)
    }

    @Test
    fun everyHostCountGetsItsBlockBiggestFirstFromTheStartOfTheNetwork() {
        val blocks = assertNotNull(plan("192.168.0.0", "192.168.0.255", 2, 100, 20, 50))
        assertEquals(listOf(100, 50, 20, 2), blocks.map { it.need })
        assertEquals(listOf("192.168.0.0/25", "192.168.0.128/26", "192.168.0.192/27", "192.168.0.224/30"), blocks.map { it.subnet.cidr })
        assertEquals(listOf("192.168.0.0/29", "192.168.0.8/30", "192.168.0.12/30"), assertNotNull(plan("192.168.0.0", "192.168.0.255", 1, 2, 3)).map { it.subnet.cidr })
    }

    @Test
    fun hostsThatDoNotFitGiveNoPlan() {
        assertNull(plan("192.168.0.0", "192.168.0.15", 100))
        assertNotNull(plan("192.168.0.0", "192.168.0.255", 126, 126))
        assertNull(plan("192.168.0.0", "192.168.0.255", 126, 126, 1))
        assertNull(plan("192.168.0.0", "192.168.0.255", Int.MAX_VALUE))
    }

    @Test
    fun aRangeThatIsNotANetworkGetsBlocksOnMultiplesOfTheirSize() {
        assertEquals(listOf("10.0.0.64/26", "10.0.0.128/27"), assertNotNull(plan("10.0.0.10", "10.0.0.250", 50, 20)).map { it.subnet.cidr })
        assertNull(plan("10.0.0.10", "10.0.0.60", 50))
        assertEquals(listOf("10.0.0.64/26"), assertNotNull(plan("10.0.0.10", "10.0.0.127", 50)).map { it.subnet.cidr })
    }

    @Test
    fun entriesThatOverlapOrTouchMeltIntoTheFewestBlocks() {
        assertEquals(listOf("10.0.0.0/22"), summary("10.0.0.0/24\n10.0.1.0/24\n10.0.2.0/23"))
        assertEquals(listOf("8.8.4.4/32", "8.8.8.8/32"), summary("8.8.8.8, 8.8.4.4"))
        assertEquals(listOf("10.0.0.0/24"), summary("10.0.0.0/24; 10.0.0.128/25; 10.0.0.7"))
        assertEquals(listOf("10.0.0.0/24", "10.0.2.0/24"), summary("10.0.2.0/24, 10.0.0.0-10.0.0.255"))
        assertEquals(listOf("10.0.0.0/23"), summary("10.0.0.0-10.0.0.200, 10.0.0.201-10.0.1.255"))
        assertEquals(listOf("0.0.0.0/0"), summary("0.0.0.0/1, 128.0.0.0/1"))
        assertEquals(emptyList(), Subnets.summarize(emptyList()))
    }

    @Test
    fun aListIsSplitAtCommasSemicolonsAndLines() {
        assertEquals(
            listOf("10.0.0.0/24", "10.0.1.0/24", "10.0.2.0 255.255.255.0", "10.0.3.1 - 9"),
            Subnets.items("10.0.0.0/24, 10.0.1.0/24;\r\n10.0.2.0 255.255.255.0\n\n10.0.3.1 - 9,"),
        )
        assertEquals(emptyList(), Subnets.items(" \n ,; "))
    }

    @Test
    fun hostCountsAreWholeNumbersAboveZero() {
        assertEquals(listOf(100, 50, 20, 2), Subnets.hostCounts("100, 50;20 2"))
        assertNull(Subnets.hostCounts(""))
        assertNull(Subnets.hostCounts("10, x"))
        assertNull(Subnets.hostCounts("10, 0"))
        assertNull(Subnets.hostCounts("-5"))
        assertNull(Subnets.hostCounts("5.5"))
    }
}
