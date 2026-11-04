package com.vasmarfas.networkhelm.server

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// each step passes once, so a code seen over a shoulder is no good a moment later
class Totp(private val secret: ByteArray, private val now: () -> Long = System::currentTimeMillis) {
    private var used = Long.MIN_VALUE

    fun code(step: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val number = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
        return (number % 1_000_000).toString().padStart(6, '0')
    }

    @Synchronized
    fun accept(code: String): Boolean {
        val step = now() / 30_000
        val typed = code.trim().encodeToByteArray()
        val matched = (step - 1..step + 1).firstOrNull { it > used && MessageDigest.isEqual(code(it).encodeToByteArray(), typed) } ?: return false
        used = matched
        return true
    }
}

private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

fun base32(bytes: ByteArray): String = buildString {
    var buffer = 0
    var bits = 0
    for (byte in bytes) {
        buffer = (buffer shl 8) or (byte.toInt() and 0xff)
        bits += 8
        while (bits >= 5) {
            append(BASE32[(buffer shr (bits - 5)) and 31])
            bits -= 5
        }
    }
    if (bits > 0) append(BASE32[(buffer shl (5 - bits)) and 31])
}

fun fromBase32(text: String): ByteArray {
    val out = mutableListOf<Byte>()
    var buffer = 0
    var bits = 0
    for (char in text.uppercase().filter { it != '=' && !it.isWhitespace() }) {
        val value = BASE32.indexOf(char)
        require(value >= 0) { "$char is not base32" }
        buffer = (buffer shl 5) or value
        bits += 5
        if (bits >= 8) {
            out += (buffer shr (bits - 8)).toByte()
            bits -= 8
        }
    }
    return out.toByteArray()
}
