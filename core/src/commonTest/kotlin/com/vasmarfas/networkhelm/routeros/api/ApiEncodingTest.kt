package com.vasmarfas.networkhelm.routeros.api

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiEncodingTest {

    private val privetCp1251 = byteArrayOf(0xCF.toByte(), 0xF0.toByte(), 0xE8.toByte(), 0xE2.toByte(), 0xE5.toByte(), 0xF2.toByte())

    @Test
    fun utf8IsReadAsUtf8() {
        assertEquals("Привет", ApiEncoding.Utf8.decode("Привет".encodeToByteArray()))
    }

    @Test
    fun invalidUtf8FallsBackToCp1251() {
        assertEquals("Привет", ApiEncoding.Utf8.decode(privetCp1251))
    }

    @Test
    fun cp1251RoundTrip() {
        assertContentEquals(privetCp1251, ApiEncoding.Windows1251.encode("Привет"))
        val everyByte = ByteArray(256) { it.toByte() }
        assertContentEquals(everyByte, ApiEncoding.Windows1251.encode(ApiEncoding.Windows1251.decode(everyByte)))
        assertEquals("Ёё№€", ApiEncoding.Windows1251.decode(ApiEncoding.Windows1251.encode("Ёё№€")))
    }

    @Test
    fun characterWithoutCp1251CodeIsNotSilentlyReplaced() {
        assertFailsWith<IllegalArgumentException> { ApiEncoding.Windows1251.encode("ok 😀") }
    }
}
