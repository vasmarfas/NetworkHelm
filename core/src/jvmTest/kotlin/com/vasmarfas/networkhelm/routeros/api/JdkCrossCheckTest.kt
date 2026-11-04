package com.vasmarfas.networkhelm.routeros.api

import com.vasmarfas.networkhelm.keenetic.sha256
import com.vasmarfas.networkhelm.snmp.Aes128
import java.nio.charset.Charset
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class JdkCrossCheckTest {

    @Test
    fun cp1251TableMatchesTheJdk() {
        val jdk = Charset.forName("windows-1251")
        for (code in 0x80..0xFF) {
            if (code == 0x98) continue
            val byte = byteArrayOf(code.toByte())
            assertEquals(String(byte, jdk), ApiEncoding.Windows1251.decode(byte), "byte 0x${code.toString(16)}")
        }
    }

    @Test
    fun md5MatchesTheJdkAroundEveryPaddingBoundary() {
        val random = Random(1251)
        for (size in 0..300) {
            val data = random.nextBytes(size)
            assertContentEquals(MessageDigest.getInstance("MD5").digest(data), md5(data), "size $size")
        }
    }

    @Test
    fun sha256MatchesTheJdkAroundEveryPaddingBoundary() {
        val random = Random(256)
        for (size in 0..300) {
            val data = random.nextBytes(size)
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(data), sha256(data), "size $size")
        }
    }

    @Test
    fun aesMatchesTheJdkInBothDirections() {
        val random = Random(128)
        repeat(200) {
            val key = random.nextBytes(16)
            val block = random.nextBytes(16)
            val sealed = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }.doFinal(block)
            assertContentEquals(sealed, Aes128(key).encrypt(block))
            assertContentEquals(block, Aes128(key).decrypt(sealed))
        }
    }
}
