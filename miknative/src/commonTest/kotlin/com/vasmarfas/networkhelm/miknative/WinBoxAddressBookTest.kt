package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.winbox.WinBoxBookException
import com.vasmarfas.networkhelm.winbox.WinBoxSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinBoxAddressBookTest {

    @Test
    fun aDeeplyNestedBookIsAnOrdinaryImportFailure() {
        val message = deepMessage()
        val bytes = byteArrayOf(0x0D, 0xF0.toByte(), 0x1D, 0xC0.toByte()) + length(message.size) + message
        assertFailsWith<WinBoxBookException> { WinBoxAddressBook.read(bytes) }
    }
    private val address = 0x01
    private val user = 0x02
    private val password = 0x03
    private val comment = 0x04
    private val group = 0x08
    private val ip = 0x0B

    private fun text(value: String) = M2Value.Text(value)

    private fun length(size: Int) = byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte())

    private fun book(vararg sessions: M2Message): ByteArray {
        val out = ByteSink()
        out.write(byteArrayOf(0x0D, 0xF0.toByte(), 0x1D, 0xC0.toByte()))
        for (session in sessions) {
            val bytes = session.encode()
            out.write(length(bytes.size))
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray): List<WinBoxSession> = assertNotNull(WinBoxAddressBook.read(bytes))

    @Test
    fun aSessionKeepsItsAddressLoginPasswordGroupAndComment() {
        val bytes = book(
            M2Message.of(
                address to text("192.0.2.10:8292"), user to text("admin"), password to text("secret-1"), group to text("Lab"), comment to text("Core router"),
            ),
        )
        val session = read(bytes).single()
        assertEquals("192.0.2.10:8292", session.address)
        assertEquals("admin", session.user)
        assertEquals("secret-1", session.password)
        assertEquals("Lab", session.group)
        assertEquals("Core router", session.comment)
        assertEquals("", session.ip)
    }

    @Test
    fun theOrderOfTheFieldsDoesNotMatterAndOtherKeysAreLeftAlone() {
        val bytes = book(
            M2Message.of(
                0x07 to M2Value.Bool(true), 0x09 to M2Value.U32(0xFFFFFFFFL), 0xFE0001 to M2Value.U8(3), 0x06 to text("(x)"), comment to text("edge"),
                0xFE0009 to text("edge"), password to text("p"), user to text("u"), address to text("router.example.net"), group to text(""), 0x0A to M2Value.Bool(false),
            ),
            M2Message.of(address to text("198.51.100.7"), user to text("root"), 0xFE000C to M2Value.U8(5)),
        )
        val sessions = read(bytes)
        assertEquals(listOf("router.example.net", "198.51.100.7"), sessions.map { it.address })
        assertEquals(listOf("u", "root"), sessions.map { it.user })
        assertEquals(listOf("p", ""), sessions.map { it.password })
        assertEquals(listOf("edge", ""), sessions.map { it.comment })
    }

    @Test
    fun aSessionSavedByMacAddressCarriesTheIpAddressWinBoxKnew() {
        val bytes = book(M2Message.of(address to text("02:00:00:00:00:01"), user to text("admin"), ip to text("192.0.2.77:8291")))
        val session = read(bytes).single()
        assertEquals("02:00:00:00:00:01", session.address)
        assertEquals("192.0.2.77:8291", session.ip)
    }

    @Test
    fun aSessionWithoutAnAddressIsLeftOut() {
        val bytes = book(M2Message.of(user to text("admin")), M2Message.of(address to text("  "), user to text("x")), M2Message.of(address to text("192.0.2.1")))
        assertEquals(listOf("192.0.2.1"), read(bytes).map { it.address })
    }

    @Test
    fun textOfAnyKindIsRead() {
        val long = "x".repeat(300)
        val bytes = book(M2Message.of(address to text("маршрутизатор.example.net"), comment to text(long), password to text("пароль-1")))
        val session = read(bytes).single()
        assertEquals("маршрутизатор.example.net", session.address)
        assertEquals(long, session.comment)
        assertEquals("пароль-1", session.password)
    }

    @Test
    fun aBookWithoutSessionsIsEmpty() {
        assertTrue(read(book()).isEmpty())
    }

    @Test
    fun otherBytesAtTheStartAreNotABook() {
        assertNull(WinBoxAddressBook.read("address,user\n192.0.2.1,admin\n".encodeToByteArray()))
        assertNull(WinBoxAddressBook.read(ByteArray(2)))
    }

    @Test
    fun aFileCutShortIsAnError() {
        val whole = book(M2Message.of(address to text("192.0.2.1"), user to text("admin")))
        assertFailsWith<WinBoxBookException> { WinBoxAddressBook.read(whole.copyOf(whole.size - 3)) }
        assertFailsWith<WinBoxBookException> { WinBoxAddressBook.read(whole.copyOf(7)) }
    }

    @Test
    fun aSessionThatIsNoMessageIsAnError() {
        val out = ByteSink()
        out.write(byteArrayOf(0x0D, 0xF0.toByte(), 0x1D, 0xC0.toByte()))
        out.write(length(3))
        out.write(byteArrayOf(1, 2, 3))
        assertFailsWith<WinBoxBookException> { WinBoxAddressBook.read(out.toByteArray()) }
    }
}
