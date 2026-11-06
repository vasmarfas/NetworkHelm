package com.vasmarfas.networkhelm.nettools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacTest {

    private val registry = MacRegistry.parse(
        """
        #2026-09-25
        4C5E0C	Routerboard.com	LV
        70B3D5	IEEE Registration Authority	US
        70B3D5F2F	TELEPLATFORMS	RU
        C85CE2	IEEE Registration Authority	US
        C85CE27	SYNERGY SYSTEMS AND SOLUTIONS	IN
        741AE09	Private
        """.trimIndent(),
    )

    @Test
    fun theLongestAssignedPrefixNamesTheVendor() {
        assertEquals("2026-09-25", registry.date)
        assertEquals("TELEPLATFORMS", registry.find("70B3D5F2F123")?.vendor)
        assertEquals("MA-S", registry.find("70B3D5F2F123")?.type)
        assertEquals("IEEE Registration Authority", registry.find("70B3D5000000")?.vendor)
        assertEquals("SYNERGY SYSTEMS AND SOLUTIONS", registry.find("C85CE27ABCDE")?.vendor)
        assertEquals("MA-M", registry.find("C85CE27")?.type)
        assertEquals("LV", registry.find("4C5E0C")?.country)
        assertNull(registry.find("741AE09")?.country)
        assertNull(registry.find("001122334455"))
    }

    @Test
    fun aBlockSpansItsFreeBits() {
        val block = assertNotNull(registry.find("C85CE27ABCDE"))
        assertEquals("C85CE2700000", block.first)
        assertEquals("C85CE27FFFFF", block.last)
    }

    @Test
    fun aVendorNeedsAtLeastTheOui() {
        assertEquals("4C5E0C", MacAddress.prefix("4c-5e-0c"))
        assertEquals("4C5E0C123456", MacAddress.prefix("4C:5E:0C:12:34:56"))
        assertEquals("001A2B3C4D5E", MacAddress.normalize("001a.2b3c.4d5e"))
        assertNull(MacAddress.prefix("4C:5E"))
        assertNull(MacAddress.normalize("00:1a:2b"))
    }

    @Test
    fun groupsWithoutLeadingZerosArePadded() {
        assertEquals("3C0754123456", MacAddress.normalize("3c:7:54:12:34:56"))
        assertEquals("001A2B3", MacAddress.prefix("00:1a:2b:3"))
        assertEquals("001A2B", MacAddress.prefix("00:1A:2B:"))
        assertNull(MacAddress.prefix("4C:5E:0C:12:34:5G"))
    }

    @Test
    fun aGroupAddressBelongsToTheOwnerOfItsOui() {
        assertEquals("00005E000001", MacAddress.individual("01005E000001"))
        assertEquals("ICANN", MacRegistry.parse("#2026-09-25\n00005E\tICANN\tUS").find(MacAddress.individual("01005E"))?.vendor)
        assertTrue(MacAddress.isLocal("02005E000001"))
    }

    @Test
    fun theMagicPacketIsSixFfAndTheAddressSixteenTimes() {
        val packet = assertNotNull(magicPacket("00-1a-2b-3c-4d-5e"))
        assertEquals(102, packet.size)
        assertTrue(packet.take(6).all { it == 0xFF.toByte() })
        val address = listOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E).map { it.toByte() }
        for (copy in 0 until 16) assertEquals(address, packet.slice(6 + copy * 6 until 12 + copy * 6), "copy $copy")
        assertNull(magicPacket("00:1a:2b"))
    }
}
