package com.vasmarfas.networkhelm.miknative

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class M2Test {
    private fun hex(text: String) = text.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun sixThousandNestedMessagesAreRejectedBeforeTheStackRunsOut() {
        assertFailsWith<M2FormatException> { M2Message.decode(deepMessage()) }
    }

    @Test
    fun theElementBudgetIsSharedByAllArrays() {
        val values = M2Value.U32s(LongArray(60_000))
        assertFailsWith<M2FormatException> { M2Message.decode(M2Message.of(1 to values, 2 to values).encode()) }
    }

    @Test
    fun theInitialRequestHasTheBytesOfTheDescription() {
        val message = M2Message.of(
            M2Key.To to M2Value.U32s(longArrayOf(13, 4)),
            M2Key.From to M2Value.U32s(longArrayOf(0, 1)),
            M2Key.Request to M2Value.Bool(true),
            M2Key.RequestId to M2Value.U8(0),
            M2Key.Command to M2Value.U8(7),
        )
        val expected = hex("4d32 0100ff88 0200 0d000000 04000000 0200ff88 0200 00000000 01000000 0500ff01 0600ff0900 0700ff0907")
        assertContentEquals(expected, message.encode())
    }

    @Test
    fun aKeystrokeHasTheBytesOfTheDescription() {
        val message = M2Message.of(
            M2Key.To to M2Value.U32s(longArrayOf(76)),
            M2Key.From to M2Value.U32s(longArrayOf(0, 429)),
            3 to M2Value.U32(664),
            M2Key.SessionId to M2Value.U8(28),
            M2Key.Command to M2Value.U32(0x0A0067),
            2 to M2Value.Raw(byteArrayOf(0x74)),
        )
        val expected = hex("4d32 0100ff88 0100 4c000000 0200ff88 0200 00000000 ad010000 03000008 98020000 0100fe09 1c 0700ff08 67000a00 02000031 0174")
        assertContentEquals(expected, message.encode())
        assertEquals(664, M2Message.decode(expected).number(3))
    }

    @Test
    fun aStringArrayIsReadAsTheRouterSendsIt() {
        val bytes = hex("4d32 1c00ffa0 0100 1000") + "msg-proxy-7.18.2".encodeToByteArray()
        assertEquals(listOf("msg-proxy-7.18.2"), (M2Message.decode(bytes)[0xFF001C] as M2Value.Texts).value)
    }

    // the reply of a target through a RoMON agent carries a 16-byte address under the system key 0x13
    @Test
    fun aSixteenByteAddressIsReadAndSoAreTheFieldsAfterIt() {
        val address = ByteArray(16) { (it + 1).toByte() }
        val bytes = hex("4d32 1300ff18") + address + hex("11000021 04") + "i386".encodeToByteArray()
        val message = M2Message.decode(bytes)
        assertContentEquals(address, (message[0xFF0013] as M2Value.Address6).value)
        assertEquals("i386", message.text(0x11))
        assertContentEquals(bytes, message.encode())
    }

    @Test
    fun arraysOfTheWiderTypesAreReadAsTheRouterSendsThem() {
        val address = ByteArray(16) { (it + 1).toByte() }
        val bytes = hex("4d32 01000090 0200 0100000000000000 0000000001000000 02000098 0100") + address + hex("030000b0 0200 0300 010203 0000 04000080 0300 010001")
        val message = M2Message.decode(bytes)
        assertContentEquals(booleanArrayOf(true, false, true), (message[4] as M2Value.Bools).value)
        assertEquals(listOf(1L, 4_294_967_296), (message[1] as M2Value.U64s).value.toList())
        assertContentEquals(address, (message[2] as M2Value.Address6s).value.single())
        assertEquals(listOf(3, 0), (message[3] as M2Value.Raws).value.map { it.size })
        assertContentEquals(bytes, message.encode())
    }

    @Test
    fun everyKindOfValueSurvivesTheRoundTrip() {
        val inner = M2Message.of(1 to M2Value.U8(0), 0xFE0010 to M2Value.Text("dir/sub"))
        val message = M2Message.of(
            1 to M2Value.Bool(false),
            2 to M2Value.Bool(true),
            3 to M2Value.U8(255),
            4 to M2Value.U32(4_000_000_000),
            5 to M2Value.U64(5_000_000_000_000),
            6 to M2Value.Text("short"),
            7 to M2Value.Text("long ".repeat(100)),
            8 to M2Value.Raw(ByteArray(300) { it.toByte() }),
            9 to M2Value.Message(inner),
            10 to M2Value.U32s(longArrayOf(1, 2, 4_000_000_000)),
            11 to M2Value.Texts(listOf("a", "bc", "")),
            12 to M2Value.Messages(listOf(inner, inner)),
            13 to M2Value.U64s(longArrayOf(1, 5_000_000_000_000)),
            14 to M2Value.Address6s(listOf(ByteArray(16) { it.toByte() })),
            15 to M2Value.Raws(listOf(byteArrayOf(1, 2, 3), ByteArray(0))),
            16 to M2Value.Bools(booleanArrayOf(true, false)),
            0xFE0003 to M2Value.U8(1),
        )
        val back = M2Message.decode(message.encode())
        assertEquals(message.fields.map { it.key }, back.fields.map { it.key })
        assertEquals(4_000_000_000, back.number(4))
        assertEquals(5_000_000_000_000, back.number(5))
        assertEquals("long ".repeat(100), back.text(7))
        assertContentEquals(ByteArray(300) { it.toByte() }, back.raw(8))
        assertEquals("dir/sub", back.message(9)?.text(0xFE0010))
        assertEquals(listOf(1L, 2L, 4_000_000_000), back.numbers(10))
        assertEquals(listOf("a", "bc", ""), (back[11] as M2Value.Texts).value)
        assertEquals(2, back.messages(12)?.size)
        assertEquals(listOf(1L, 5_000_000_000_000), (back[13] as M2Value.U64s).value.toList())
        assertContentEquals(ByteArray(16) { it.toByte() }, (back[14] as M2Value.Address6s).value.single())
        assertEquals(listOf(3, 0), (back[15] as M2Value.Raws).value.map { it.size })
        assertContentEquals(booleanArrayOf(true, false), (back[16] as M2Value.Bools).value)
        assertEquals(1, back.number(0xFE0003))
    }

    @Test
    fun anEmptyEmbeddedMessageIsZeroBytes() {
        val message = M2Message.of(0x33 to M2Value.Message(M2Message(emptyList())))
        assertContentEquals(hex("4d32 33000029 00"), message.encode())
        assertEquals(0, M2Message.decode(message.encode()).message(0x33)?.fields?.size)
    }

    @Test
    fun aKeyWithoutTheValueIsNotThere() {
        val message = M2Message.of(1 to M2Value.Text("x"))
        assertNull(message.number(1))
        assertNull(message.text(2))
    }

    @Test
    fun brokenMessagesAreRefused() {
        assertFailsWith<M2FormatException> { M2Message.decode(hex("4d33")) }
        assertFailsWith<M2FormatException> { M2Message.decode(hex("4d32 0100ff08 0100")) }
        assertFailsWith<M2FormatException> { M2Message.decode(hex("4d32 0100ff21 05 6162")) }
        assertFailsWith<M2FormatException> { M2Message.decode(hex("4d32 0100ff77")) }
    }
}

internal fun deepMessage(): ByteArray {
    var bytes = byteArrayOf(0x4D, 0x32)
    repeat(6_000) {
        bytes = byteArrayOf(0x4D, 0x32, 1, 0, 0, 0x28, bytes.size.toByte(), (bytes.size shr 8).toByte()) + bytes
    }
    return bytes
}
