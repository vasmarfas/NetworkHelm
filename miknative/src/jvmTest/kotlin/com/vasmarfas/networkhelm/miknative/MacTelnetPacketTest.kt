package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacTelnetPacketTest {
    private val clientMac = MacAddress.parse("00:11:22:33:44:55")
    private val routerMac = MacAddress.parse("66:77:88:99:aa:bb")

    @Test
    fun encodesAndDecodesClientToServerHeader() {
        val encoded = encodeHeader(
            type = MacTelnetPacketType.SESSION_START,
            sourceMac = clientMac,
            destinationMac = routerMac,
            sessionKey = 0x1234,
            counter = 100L,
            fromServer = false,
        )
        assertEquals(22, encoded.size)
        assertEquals(1, encoded[0].toInt())
        assertEquals(MacTelnetPacketType.SESSION_START, encoded[1].toInt())

        assertEquals(0x12.toByte(), encoded[14])
        assertEquals(0x34.toByte(), encoded[15])
        assertEquals(0x00.toByte(), encoded[16])
        assertEquals(0x15.toByte(), encoded[17])

        assertEquals(100L, (encoded[21].toLong() and 0xFF))

        val decoded = decodeHeader(encoded, fromServer = false)
        assertEquals(1, decoded.version)
        assertEquals(MacTelnetPacketType.SESSION_START, decoded.type)
        assertEquals(clientMac, decoded.sourceMac)
        assertEquals(routerMac, decoded.destinationMac)
        assertEquals(0x1234, decoded.sessionKey)
        assertEquals(100L, decoded.counter)
    }

    @Test
    fun carriesTheClientTypeOfTheNativeStream() {
        val encoded = encodeHeader(MacTelnetPacketType.SESSION_START, clientMac, routerMac, 0x1234, 0L, clientType = MAC_NATIVE_CLIENT_TYPE)
        assertEquals(0x0F.toByte(), encoded[16])
        assertEquals(0x90.toByte(), encoded[17])
    }

    @Test
    fun encodesAndDecodesServerToClientHeader() {
        val encoded = encodeHeader(
            type = MacTelnetPacketType.ACK,
            sourceMac = routerMac,
            destinationMac = clientMac,
            sessionKey = 0x5678,
            counter = 42L,
            fromServer = true,
        )
        assertEquals(0x00.toByte(), encoded[14])
        assertEquals(0x15.toByte(), encoded[15])
        assertEquals(0x56.toByte(), encoded[16])
        assertEquals(0x78.toByte(), encoded[17])

        val decoded = decodeHeader(encoded, fromServer = true)
        assertEquals(MacTelnetPacketType.ACK, decoded.type)
        assertEquals(routerMac, decoded.sourceMac)
        assertEquals(clientMac, decoded.destinationMac)
        assertEquals(0x5678, decoded.sessionKey)
        assertEquals(42L, decoded.counter)
    }

    @Test
    fun encodesAndParsesControlBlocks() {
        val block1 = encodeControlBlock(MacTelnetControlType.BEGIN_AUTH)
        val block2 = encodeControlBlock(MacTelnetControlType.USERNAME, "admin".encodeToByteArray())
        val combined = block1 + block2

        val parsed = parseControlBlocks(combined)
        assertEquals(2, parsed.size)
        assertFalse(parsed[0].isPlainData)
        assertEquals(MacTelnetControlType.BEGIN_AUTH, parsed[0].type)
        assertEquals(0, parsed[0].value.size)

        assertFalse(parsed[1].isPlainData)
        assertEquals(MacTelnetControlType.USERNAME, parsed[1].type)
        assertEquals("admin", parsed[1].value.decodeToString())
    }

    @Test
    fun parsesPlainTerminalDataWhenNoMagic() {
        val rawTerminal = "RouterOS v7.25\r\n[admin@MikroTik] > ".encodeToByteArray()
        val parsed = parseControlBlocks(rawTerminal)
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].isPlainData)
        assertEquals(rawTerminal.decodeToString(), parsed[0].value.decodeToString())
    }

    @Test
    fun encodesTerminalDimensionsLittleEndian() {
        val width = encodeTerminalDimension(80)
        assertEquals(2, width.size)
        assertEquals(80.toByte(), width[0])
        assertEquals(0.toByte(), width[1])

        val height = encodeTerminalDimension(300)
        assertEquals((300 and 0xFF).toByte(), height[0])
        assertEquals((300 shr 8).toByte(), height[1])
    }

    @Test
    fun encodesAndDecodesPingAndPong() {
        val ping = encodeHeader(
            type = MacTelnetPacketType.PING,
            sourceMac = clientMac,
            destinationMac = routerMac,
            sessionKey = 0x1234,
            counter = 10L,
            fromServer = false,
        )
        val decodedPing = decodeHeader(ping, fromServer = false)
        assertEquals(MacTelnetPacketType.PING, decodedPing.type)
        assertEquals(10L, decodedPing.counter)

        val pong = encodeHeader(
            type = MacTelnetPacketType.PONG,
            sourceMac = routerMac,
            destinationMac = clientMac,
            sessionKey = 0x1234,
            counter = 10L,
            fromServer = true,
        )
        val decodedPong = decodeHeader(pong, fromServer = true)
        assertEquals(MacTelnetPacketType.PONG, decodedPong.type)
        assertEquals(10L, decodedPong.counter)
    }

    @Test
    fun rejectsPacketShorterThanHeader() {
        val ex = assertFailsWith<DeviceException> {
            decodeHeader(ByteArray(21), fromServer = true)
        }
        assertEquals(ErrorCategory.General, ex.category)
    }

    @Test
    fun rejectsControlBlockWithTruncatedValue() {
        val badBlock = MAC_TELNET_CONTROL_MAGIC + byteArrayOf(
            MacTelnetControlType.USERNAME.toByte(),
            0, 0, 0, 50,
            1, 2, 3,
        )
        val ex = assertFailsWith<DeviceException> {
            parseControlBlocks(badBlock)
        }
        assertEquals(ErrorCategory.General, ex.category)
    }

    @Test
    fun computesMd5PasswordHash() {
        val salt = ByteArray(16) { it.toByte() }
        val hash = macTelnetMd5("secret", salt)
        assertEquals(17, hash.size)
        assertEquals(0.toByte(), hash[0])
        val expectedHex = "00925d25da4b1ffe731237818c4e1fcd57"
        assertEquals(expectedHex, hash.joinToString("") { "%02x".format(it) })
    }
}

