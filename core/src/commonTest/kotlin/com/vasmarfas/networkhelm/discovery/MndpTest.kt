package com.vasmarfas.networkhelm.discovery

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MndpTest {

    private fun tlv(type: Int, value: ByteArray) = byteArrayOf((type shr 8).toByte(), type.toByte(), (value.size shr 8).toByte(), value.size.toByte()) + value

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private val packet = bytes(0xA7, 0x46, 0x00, 0x00) +
        tlv(1, bytes(0x02, 0x00, 0x5E, 0x10, 0x00, 0x01)) +
        tlv(5, "lab-router".encodeToByteArray()) +
        tlv(7, "7.24.5 (stable) 2026-09-29 14:21:27".encodeToByteArray()) +
        tlv(8, "MikroTik".encodeToByteArray()) +
        tlv(10, bytes(0x50, 0x00, 0x0E, 0x00)) +
        tlv(11, "ABCD-1234".encodeToByteArray()) +
        tlv(12, "C53UiG+5HPaxD2HPaxD".encodeToByteArray()) +
        tlv(14, bytes(0)) +
        tlv(16, "bridge/ether1".encodeToByteArray()) +
        tlv(18, bytes(0)) +
        tlv(19, bytes(0x79)) +
        tlv(17, bytes(192, 0, 2, 1)) +
        tlv(15, bytes(0xFE, 0x80, 0, 0, 0, 0, 0, 0, 0x02, 0x00, 0x5E, 0xFF, 0xFE, 0x10, 0x00, 0x01))

    @Test
    fun parsesEveryKnownField() {
        val a = parseMndp(packet)
        requireNotNull(a)
        assertEquals("02:00:5E:10:00:01", a.mac)
        assertEquals("lab-router", a.identity)
        assertEquals("7.24.5 (stable) 2026-09-29 14:21:27", a.version)
        assertEquals("MikroTik", a.platform)
        assertEquals(917_584L, a.uptimeSeconds)
        assertEquals("ABCD-1234", a.softwareId)
        assertEquals("C53UiG+5HPaxD2HPaxD", a.board)
        assertEquals("bridge/ether1", a.interfaceName)
        assertEquals("192.0.2.1", a.ipv4)
        assertEquals("fe80::200:5eff:fe10:1", a.ipv6)
        assertEquals(setOf(18, 19), a.other.keys)
        assertContentEquals(bytes(0x79), a.other[19])
    }

    @Test
    fun truncatedFieldMakesThePacketInvalid() {
        assertNull(parseMndp(packet.copyOf(packet.size - 3)))
        assertNull(parseMndp(bytes(0, 0)))
    }

    @Test
    fun theRefreshRequestIsNoAnnouncement() {
        assertNull(parseMndp(ByteArray(4)))
    }

    @Test
    fun ipv6IsWrittenInTheShortForm() {
        fun ip(vararg groups: Int) = ByteArray(16) { i -> (groups[i / 2] shr (if (i % 2 == 0) 8 else 0)).toByte() }
        assertEquals("::1", formatIpv6(ip(0, 0, 0, 0, 0, 0, 0, 1)))
        assertEquals("2001:db8::", formatIpv6(ip(0x2001, 0xdb8, 0, 0, 0, 0, 0, 0)))
        assertEquals("2001:db8:0:1::1", formatIpv6(ip(0x2001, 0xdb8, 0, 1, 0, 0, 0, 1)))
        assertEquals("2001:db8:1:2:3:4:5:6", formatIpv6(ip(0x2001, 0xdb8, 1, 2, 3, 4, 5, 6)))
        assertEquals("2001:0:1:2:3:4:5:6", formatIpv6(ip(0x2001, 0, 1, 2, 3, 4, 5, 6)))
    }
}
