package com.vasmarfas.networkhelm.routeros.api

import kotlin.test.Test
import kotlin.test.assertEquals

class Md5Test {

    private fun hex(text: String) = md5(text.encodeToByteArray()).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun rfc1321Vectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hex(""))
        assertEquals("0cc175b9c0f1b6a831c399e269772661", hex("a"))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", hex("abc"))
        assertEquals("f96b697d7cb7938d525a2f31aaf161d0", hex("message digest"))
        assertEquals("c3fcd3d76192e4007dfb496cca67e13b", hex("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("d174ab98d277d9f5a5611c2c9f419d9f", hex("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"))
        assertEquals("57edf4a22be3c955ac49da2e2107b67a", hex("1234567890".repeat(8)))
    }
}
