package com.vasmarfas.networkhelm.app

import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.ui.connect.ConnectState
import com.vasmarfas.networkhelm.ui.connect.Problem
import com.vasmarfas.networkhelm.ui.connect.TrustQuestions
import com.vasmarfas.networkhelm.ui.files.LocalFile
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull

class BookTest {
    private fun book(rounds: Int = 600_000, salt: String = Base64.encode(ByteArray(16)), iv: String = Base64.encode(ByteArray(12)), data: String = Base64.encode(ByteArray(16))) =
        SealedBook(BOOK_FORMAT, rounds, salt, iv, data)

    @Test
    fun zeroRoundsAreRejectedAsAFormatErrorBeforeTheKeyIsDerived() {
        assertEquals("BookFormatException", assertFails { unseal(book(rounds = 0), "password") }::class.simpleName)
    }

    @Test
    fun excessiveRoundsAreRejectedBeforeTheKeyIsDerived() {
        assertIs<BookFormatException>(assertFails { unseal(book(rounds = Int.MAX_VALUE), "password") })
    }

    @Test
    fun malformedBase64IsAFormatError() {
        for (field in listOf(book(salt = "?"), book(iv = "?"), book(data = "?"))) {
            assertEquals("BookFormatException", assertFails { unseal(field, "password") }::class.simpleName)
        }
    }

    @Test
    fun shortSaltIvAndCiphertextAreFormatErrors() {
        val short = Base64.encode(ByteArray(1))
        for (field in listOf(book(salt = short), book(iv = short), book(data = short))) {
            assertEquals("BookFormatException", assertFails { unseal(field, "password") }::class.simpleName)
        }
    }

    @Test
    fun aWrongPasswordRemainsDistinctFromAnInvalidFile() {
        val plain = "address book".encodeToByteArray()
        val sealed = seal(plain, "password")
        assertNull(unseal(sealed, "wrong"))
        assertContentEquals(plain, unseal(sealed, "password"))
    }

    @Test
    fun anInvalidBookIsReportedWithoutImportingAnyRouters() = runTest {
        val storage = MemoryStorage()
        val saved = SavedRouters(storage)
        val state = ConnectState(Services(storage, MemoryTrustStore(), null), saved, backgroundScope, TrustQuestions())
        state.open(book(rounds = 0), "password")
        assertIs<Problem.BookInvalid>(state.problem)
        assertEquals(emptyList(), saved.routers.value)
    }

    @Test
    fun aTruncatedEncryptedBookIsReportedInsteadOfBeingReadAsCsv() = runTest {
        val storage = MemoryStorage()
        val state = ConnectState(Services(storage, MemoryTrustStore(), null), SavedRouters(storage), backgroundScope, TrustQuestions())
        state.import(listOf(LocalFile("book.json") { "{\"format\":\"networkhelm-book-1\"".encodeToByteArray() }))
        assertIs<Problem.BookInvalid>(state.problem)
    }
}
