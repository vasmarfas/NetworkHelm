package com.vasmarfas.networkhelm.ui.editor

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextDocumentTest {

    @Test
    fun utf8TextComesBackAsItWas() {
        val bytes = "/log info \"привет\"\n:put 1\n".encodeToByteArray()
        val document = checkNotNull(decodeText(bytes))
        assertEquals("/log info \"привет\"\n:put 1\n", document.text)
        assertEquals(LineEnding.Lf, document.ending)
        assertFalse(document.cp1251)
        assertContentEquals(bytes, document.encode(document.text))
    }

    @Test
    fun aByteOrderMarkAndWindowsLineEndsAreKept() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "a\r\nb\r\n".encodeToByteArray()
        val document = checkNotNull(decodeText(bytes))
        assertEquals("a\nb\n", document.text)
        assertEquals(LineEnding.CrLf, document.ending)
        assertTrue(document.bom)
        assertEquals("UTF-8 BOM", document.charset)
        assertContentEquals(bytes, document.encode("a\nb\n"))
        assertContentEquals("a\nb\nc".encodeToByteArray(), document.encode("a\nb\nc", LineEnding.Lf, false).let { it.copyOfRange(3, it.size) })
    }

    @Test
    fun mixedLineEndsTakeTheCommonestAndSayTheyWereMixed() {
        val document = checkNotNull(decodeText("a\r\nb\r\nc\nd".encodeToByteArray()))
        assertEquals(LineEnding.CrLf, document.ending)
        assertTrue(document.mixed)
        assertEquals("a\nb\nc\nd", document.text)
        val old = checkNotNull(decodeText("a\rb\rc".encodeToByteArray()))
        assertEquals(LineEnding.Cr, old.ending)
        assertEquals("a\nb\nc", old.text)
    }

    @Test
    fun aFileThatIsNotUtf8IsReadAsCp1251AndWrittenBack() {
        val cp1251 = byteArrayOf(0xCF.toByte(), 0xF0.toByte(), 0xE8.toByte(), 0xE2.toByte(), 0xE5.toByte(), 0xF2.toByte(), 0x0A)
        val document = checkNotNull(decodeText(cp1251))
        assertEquals("Привет\n", document.text)
        assertTrue(document.cp1251)
        assertEquals("cp1251", document.charset)
        assertContentEquals(cp1251, document.encode("Привет\n"))
        assertFailsWith<IllegalArgumentException> { document.encode("emoji 😀") }
    }

    @Test
    fun bytesWithNulsAreNoText() {
        assertNull(decodeText(byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x00, 0x01)))
        assertNull(decodeText(ByteArray(100) { 0x01 }))
        assertNotNull(decodeText(ByteArray(0)))
        assertNotNull(decodeText("tab\tand\u001b[0m escape".encodeToByteArray()))
    }
}
