package com.vasmarfas.networkhelm.app

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

// the OWASP figure for PBKDF2-HMAC-SHA256 as of 2023
private const val ROUNDS = 600_000
private const val MAX_ROUNDS = 1_200_000
private const val KDF = "PBKDF2WithHmacSHA256"
private const val CIPHER = "AES/GCM/NoPadding"

// Android before 8.0 has no PBKDF2 with SHA-256
actual val canSeal: Boolean = try {
    SecretKeyFactory.getInstance(KDF)
    true
} catch (e: NoSuchAlgorithmException) {
    false
}

actual fun seal(plain: ByteArray, password: String): SealedBook {
    val random = SecureRandom()
    val salt = ByteArray(16).also(random::nextBytes)
    val iv = ByteArray(12).also(random::nextBytes)
    val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key(password, salt, ROUNDS), GCMParameterSpec(128, iv)) }
    return SealedBook(BOOK_FORMAT, ROUNDS, Base64.encode(salt), Base64.encode(iv), Base64.encode(cipher.doFinal(plain)))
}

actual fun unseal(book: SealedBook, password: String): ByteArray? {
    if (book.format != BOOK_FORMAT || book.rounds !in ROUNDS..MAX_ROUNDS) throw BookFormatException()
    val salt: ByteArray
    val iv: ByteArray
    val data: ByteArray
    try {
        salt = Base64.decode(book.salt)
        iv = Base64.decode(book.iv)
        data = Base64.decode(book.data)
    } catch (e: IllegalArgumentException) {
        throw BookFormatException()
    }
    if (salt.size != 16 || iv.size != 12 || data.size < 16) throw BookFormatException()
    val cipher = Cipher.getInstance(CIPHER)
    cipher.init(Cipher.DECRYPT_MODE, key(password, salt, book.rounds), GCMParameterSpec(128, iv))
    return try {
        cipher.doFinal(data)
    } catch (e: AEADBadTagException) {
        null
    }
}

private fun key(password: String, salt: ByteArray, rounds: Int) =
    SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(PBEKeySpec(password.toCharArray(), salt, rounds, 256)).encoded, "AES")
