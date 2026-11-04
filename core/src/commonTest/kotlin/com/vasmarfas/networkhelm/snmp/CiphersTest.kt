package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.routeros.api.md5
import kotlin.test.Test
import kotlin.test.assertEquals

class CiphersTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    @Test
    fun sha1GivesTheDigestsOfTheStandard() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hex(sha1(ByteArray(0))))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hex(sha1("abc".encodeToByteArray())))
        assertEquals("84983e441c3bd26ebaae4aa1f95129e5e54670f1", hex(sha1("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())))
    }

    @Test
    fun hmacGivesTheCodesOfRfc2202() {
        assertEquals("b617318655057264e28bc0b6fb378c8ef146be00", hex(hmac(::sha1, ByteArray(20) { 0x0b }, "Hi There".encodeToByteArray())))
        assertEquals("9294727a3638bb1c13f48ef8158bfc9d", hex(hmac(::md5, ByteArray(16) { 0x0b }, "Hi There".encodeToByteArray())))
        assertEquals("aa4ae5e15272d00e95705637ce8a3b55ed402112", hex(hmac(::sha1, ByteArray(80) { 0xaa.toByte() }, "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray())))
    }

    @Test
    fun desEncryptsAndDecryptsABlock() {
        val des = Des(bytes("133457799bbcdff1"))
        assertEquals("85e813540f0ab405", hex(des.encrypt(bytes("0123456789abcdef"))))
        assertEquals("0123456789abcdef", hex(des.decrypt(bytes("85e813540f0ab405"))))
        assertEquals("3fa40e8a984d4815", hex(Des(bytes("0123456789abcdef")).encrypt("Now is t".encodeToByteArray())))
        val iv = bytes("1234567890abcdef")
        val text = "Now is the time for all ".encodeToByteArray()
        val sealed = desCbc(bytes("0123456789abcdef"), iv, text, encrypt = true)
        assertEquals("e5c7cdde872bf27c43e934008c389c0f683788499a7c05f6", hex(sealed))
        assertEquals(text.decodeToString(), desCbc(bytes("0123456789abcdef"), iv, sealed, encrypt = false).decodeToString())
    }

    @Test
    fun aesDecryptsABlock() {
        val aes = Aes128(bytes("000102030405060708090a0b0c0d0e0f"))
        assertEquals("00112233445566778899aabbccddeeff", hex(aes.decrypt(bytes("69c4e0d86a7b0430d8cdb78070b4c55a"))))
        val key = bytes("2b7e151628aed2a6abf7158809cf4f3c")
        assertEquals("3ad77bb40d7a3660a89ecaf32466ef97", hex(Aes128(key).encrypt(bytes("6bc1bee22e409f96e93d7e117393172a"))))
        assertEquals("6bc1bee22e409f96e93d7e117393172a", hex(Aes128(key).decrypt(bytes("3ad77bb40d7a3660a89ecaf32466ef97"))))
    }

    @Test
    fun aesEncryptsABlockAndRunsInCfb() {
        assertEquals("69c4e0d86a7b0430d8cdb78070b4c55a", hex(Aes128(bytes("000102030405060708090a0b0c0d0e0f")).encrypt(bytes("00112233445566778899aabbccddeeff"))))
        val key = bytes("2b7e151628aed2a6abf7158809cf4f3c")
        val iv = bytes("000102030405060708090a0b0c0d0e0f")
        val text = bytes("6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51")
        val sealed = aesCfb(key, iv, text, encrypt = true)
        assertEquals("3b3fd92eb72dad20333449f8e83cfb4ac8a64537a0b3a93fcde3cdad9f1ce58b", hex(sealed))
        assertEquals(hex(text), hex(aesCfb(key, iv, sealed, encrypt = false)))
        assertEquals("3b3fd92eb72dad20333449f8e83cfb4ac8a6", hex(aesCfb(key, iv, text.copyOf(18), encrypt = true)))
    }
}
