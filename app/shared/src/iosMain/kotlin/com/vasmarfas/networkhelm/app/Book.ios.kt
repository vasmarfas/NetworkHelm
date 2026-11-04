package com.vasmarfas.networkhelm.app

// AES-GCM is in CryptoKit, which Kotlin/Native does not reach without a Swift bridge
actual val canSeal: Boolean = false

actual fun seal(plain: ByteArray, password: String): SealedBook = throw UnsupportedOperationException("no AES-GCM on iOS yet")

actual fun unseal(book: SealedBook, password: String): ByteArray? = throw UnsupportedOperationException("no AES-GCM on iOS yet")
