package com.vasmarfas.networkhelm.server

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// the password is kept nowhere, so a copied disk does not give away the saved passwords of the routers
class Master(private val bytes: ByteArray) {
    val check: ByteArray get() = derive("networkhelm check")

    val sealing: SecretKeySpec get() = SecretKeySpec(derive("networkhelm secrets"), "AES")

    private fun derive(purpose: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(bytes, "HmacSHA256")) }.doFinal(purpose.encodeToByteArray())

    companion object {
        fun of(password: String, salt: ByteArray) = Master(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, 600_000, 256)).encoded,
        )
    }
}

private val random = SecureRandom()

fun randomBytes(count: Int) = ByteArray(count).also(random::nextBytes)

fun seal(key: SecretKeySpec, plain: ByteArray): String {
    val nonce = randomBytes(12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
    return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plain))
}

fun unseal(key: SecretKeySpec, sealed: String): ByteArray {
    val bytes = Base64.getDecoder().decode(sealed)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12)) }
    return cipher.doFinal(bytes, 12, bytes.size - 12)
}
