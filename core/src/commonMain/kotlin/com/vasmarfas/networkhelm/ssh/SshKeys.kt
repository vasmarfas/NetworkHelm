package com.vasmarfas.networkhelm.ssh

import kotlin.io.encoding.Base64

fun sshKeyEncrypted(key: ByteArray): Boolean {
    val text = key.decodeToString()
    return when {
        OPENSSH in text -> openSshCipher(text).let { it != null && it != "none" }
        "-----BEGIN ENCRYPTED PRIVATE KEY-----" in text || "Proc-Type: 4,ENCRYPTED" in text -> true
        text.startsWith("PuTTY-User-Key-File-") -> text.lines().any { it.startsWith("Encryption:") && it.substringAfter(':').trim() != "none" }
        else -> false
    }
}

fun publicKeyBits(type: String, key: String): Int? = when {
    type.endsWith("ed25519") -> 256
    type.startsWith("ecdsa-") -> type.takeLastWhile { it.isDigit() }.toIntOrNull()
    type == "ssh-rsa" -> {
        val bytes = try {
            Base64.decode(key)
        } catch (e: IllegalArgumentException) {
            null
        }
        bytes?.let { rsaModulus(it) }?.let { modulus ->
            val significant = modulus.dropWhile { it == 0.toByte() }
            significant.firstOrNull()?.let { first -> (significant.size - 1) * 8 + (32 - (first.toInt() and 0xFF).countLeadingZeroBits()) }
        }
    }
    else -> null
}

private fun rsaModulus(bytes: ByteArray): ByteArray? {
    var at = 0
    repeat(3) { index ->
        if (at + 4 > bytes.size) return null
        val length = (at until at + 4).fold(0) { sum, i -> (sum shl 8) or (bytes[i].toInt() and 0xFF) }
        at += 4
        if (length < 0 || at + length > bytes.size) return null
        if (index == 2) return bytes.copyOfRange(at, at + length)
        at += length
    }
    return null
}

private const val OPENSSH = "-----BEGIN OPENSSH PRIVATE KEY-----"

private fun openSshCipher(text: String): String? {
    val body = text.substringAfter(OPENSSH).substringBefore("-----END").filterNot(Char::isWhitespace)
    val bytes = try {
        Base64.decode(body)
    } catch (e: IllegalArgumentException) {
        return null
    }
    val magic = "openssh-key-v1".encodeToByteArray() + 0
    val start = magic.size + 4
    if (bytes.size < start || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) return null
    val length = (magic.size until start).fold(0) { sum, i -> (sum shl 8) or (bytes[i].toInt() and 0xFF) }
    if (length < 0 || start + length > bytes.size) return null
    return bytes.copyOfRange(start, start + length).decodeToString()
}
