package com.vasmarfas.networkhelm.routeros.api

// the API carries bytes without a charset; comments typed in WinBox 3 on a Russian Windows are stored in cp1251
enum class ApiEncoding {
    Utf8,
    Windows1251,
    ;

    fun encode(text: String): ByteArray = when (this) {
        Utf8 -> text.encodeToByteArray()
        Windows1251 -> ByteArray(text.length) { encodeCp1251(text[it]) }
    }

    fun decode(bytes: ByteArray): String = when (this) {
        Utf8 -> try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            decodeCp1251(bytes)
        }
        Windows1251 -> decodeCp1251(bytes)
    }
}

private const val CP1251_HIGH =
    "ЂЃ‚ѓ„…†‡€‰Љ‹ЊЌЋЏ" +
        "ђ‘’“”•–—\u0098™љ›њќћџ" +
        " ЎўЈ¤Ґ¦§Ё©Є«¬­®Ї" +
        "°±Ііґµ¶·ё№є»јЅѕї"

private fun decodeCp1251(bytes: ByteArray): String = buildString(bytes.size) {
    for (b in bytes) {
        val code = b.toInt() and 0xFF
        append(
            when {
                code < 0x80 -> code.toChar()
                code < 0xC0 -> CP1251_HIGH[code - 0x80]
                else -> (0x0410 + code - 0xC0).toChar()
            },
        )
    }
}

private fun encodeCp1251(c: Char): Byte {
    val code = c.code
    return when {
        code < 0x80 -> code.toByte()
        code in 0x0410..0x044F -> (code - 0x0410 + 0xC0).toByte()
        else -> CP1251_HIGH.indexOf(c).let {
            if (it < 0) throw IllegalArgumentException("'$c' (U+${code.toString(16).uppercase()}) has no cp1251 code")
            (it + 0x80).toByte()
        }
    }
}
