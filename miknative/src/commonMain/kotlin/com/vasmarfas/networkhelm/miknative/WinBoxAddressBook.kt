package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.winbox.WinBoxBookException
import com.vasmarfas.networkhelm.winbox.WinBoxSession

private const val MAGIC = 0xC01DF00DL
private const val HEADER = 4

object WinBoxAddressBook {
    private const val ADDRESS = 0x01
    private const val USER = 0x02
    private const val PASSWORD = 0x03
    private const val COMMENT = 0x04
    private const val GROUP = 0x08
    private const val IP = 0x0B

    fun read(bytes: ByteArray): List<WinBoxSession>? {
        if (bytes.size < HEADER || number(bytes, 0) != MAGIC) return null
        val sessions = ArrayList<WinBoxSession>()
        var position = HEADER
        while (position < bytes.size) {
            if (bytes.size - position < 4) throw WinBoxBookException("the file ends inside a length")
            val length = number(bytes, position)
            position += 4
            if (length > bytes.size - position) throw WinBoxBookException("the file ends inside a session")
            val message = try {
                M2Message.decode(bytes.copyOfRange(position, position + length.toInt()))
            } catch (e: M2FormatException) {
                throw WinBoxBookException("a session is not a message: ${e.message}")
            }
            position += length.toInt()
            val address = message.text(ADDRESS).orEmpty().trim()
            if (address.isEmpty()) continue
            sessions += WinBoxSession(
                address = address,
                user = message.text(USER).orEmpty(),
                password = message.text(PASSWORD).orEmpty(),
                group = message.text(GROUP).orEmpty(),
                comment = message.text(COMMENT).orEmpty(),
                ip = message.text(IP).orEmpty().trim(),
            )
        }
        return sessions
    }

    private fun number(bytes: ByteArray, at: Int): Long {
        var value = 0L
        for (index in 0 until 4) value = value or ((bytes[at + index].toLong() and 0xFF) shl (8 * index))
        return value
    }
}
