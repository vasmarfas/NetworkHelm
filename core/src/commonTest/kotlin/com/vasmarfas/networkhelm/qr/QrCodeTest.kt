package com.vasmarfas.networkhelm.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QrCodeTest {

    @Test
    fun reedSolomonGivesTheCodewordsOfTheWorkedExampleOfTheStandard() {
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
        assertEquals(listOf(196, 35, 39, 119, 235, 215, 231, 226, 93, 23), QrCode.remainder(data, 10).toList())
    }

    @Test
    fun formatAndVersionInformationMatchTheTablesOfTheStandard() {
        val levelM = listOf(
            "101010000010010", "101000100100101", "101111001111100", "101101101001011",
            "100010111111001", "100000011001110", "100111110010111", "100101010100000",
        )
        assertEquals(levelM, (0 until 8).map { QrCode.formatBits(it).toString(2).padStart(15, '0') })
        assertEquals(
            listOf(0x07C94, 0x085BC, 0x09A99, 0x0A4D3, 0x0BBF6, 0x0C762, 0x0D847, 0x0E60D, 0x0F928, 0x10B78, 0x1145D, 0x12A17, 0x13532, 0x149A6),
            (7..20).map(QrCode::versionBits),
        )
    }

    @Test
    fun theSmallestVersionThatHoldsTheBytesIsTaken() {
        assertEquals(1, QrCode.encode(ByteArray(14)).version)
        assertEquals(2, QrCode.encode(ByteArray(15)).version)
        assertEquals(6, QrCode.encode(ByteArray(106)).version)
        assertEquals(7, QrCode.encode(ByteArray(107)).version)
        assertEquals(10, QrCode.encode(ByteArray(213)).version)
        assertEquals(11, QrCode.encode(ByteArray(214)).version)
        assertEquals(15, QrCode.encode(ByteArray(412)).version)
        assertEquals(16, QrCode.encode(ByteArray(413)).version)
        assertEquals(20, QrCode.encode(ByteArray(666)).version)
        assertFailsWith<IllegalArgumentException> { QrCode.encode(ByteArray(667)) }
    }

    @Test
    fun theAddressOfAnAuthenticatorAppMakesAVersion6CodeWithItsFunctionPatterns() {
        val code = QrCode.encode("otpauth://totp/NetworkHelm:admin?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=NetworkHelm")
        assertEquals(6, code.version)
        assertEquals(41, code.size)
        for ((top, left) in listOf(0 to 0, 0 to 34, 34 to 0)) {
            val finder = (0 until 7).map { row -> (0 until 7).joinToString("") { column -> if (code[top + row, left + column]) "#" else "." } }
            assertEquals(listOf("#######", "#.....#", "#.###.#", "#.###.#", "#.###.#", "#.....#", "#######"), finder)
        }
        assertEquals((8..32).map { it % 2 == 0 }, (8..32).map { code[6, it] })
        assertEquals((8..32).map { it % 2 == 0 }, (8..32).map { code[it, 6] })
        assertTrue(code[33, 8])
        assertEquals(listOf(true, false, true, false, true), (32..36).map { code[34, it] })
        val first = (0..5).map { code[it, 8] } + listOf(code[7, 8], code[8, 8], code[8, 7]) + (9..14).map { code[8, 14 - it] }
        val second = (0..7).map { code[8, 40 - it] } + (8..14).map { code[41 - 15 + it, 8] }
        assertEquals(first, second)
        assertEquals(QrCode.formatBits(code.mask), first.withIndex().sumOf { (i, dark) -> if (dark) 1 shl i else 0 })
    }
}
