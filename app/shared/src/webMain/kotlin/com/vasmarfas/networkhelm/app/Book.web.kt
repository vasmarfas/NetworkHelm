package com.vasmarfas.networkhelm.app

actual val canSeal: Boolean = false

actual fun seal(plain: ByteArray, password: String): SealedBook = throw UnsupportedOperationException("no address book in the browser")

actual fun unseal(book: SealedBook, password: String): ByteArray? = throw UnsupportedOperationException("no address book in the browser")
