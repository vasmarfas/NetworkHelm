package com.vasmarfas.networkhelm.app

import kotlinx.serialization.Serializable

@Serializable
class SealedBook(val format: String, val rounds: Int, val salt: String, val iv: String, val data: String)

@Serializable
class Book(val routers: List<SavedRouter>, val passwords: Map<String, String>)

const val BOOK_FORMAT = "networkhelm-book-1"

class BookFormatException : Exception("invalid encrypted address book")

// PBKDF2-HMAC-SHA256 and AES-256-GCM where the platform has them: the desktop and Android 8 and later
expect val canSeal: Boolean

expect fun seal(plain: ByteArray, password: String): SealedBook

expect fun unseal(book: SealedBook, password: String): ByteArray?
