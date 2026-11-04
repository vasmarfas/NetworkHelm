package com.vasmarfas.networkhelm.ui.editor

import com.vasmarfas.networkhelm.routeros.api.ApiEncoding

enum class LineEnding(val text: String, val label: String) {
    Lf("\n", "LF"),
    CrLf("\r\n", "CRLF"),
    Cr("\r", "CR"),
}

class TextDocument(val text: String, val ending: LineEnding, val cp1251: Boolean, val bom: Boolean, val mixed: Boolean) {
    fun encode(edited: String, ending: LineEnding = this.ending, cp1251: Boolean = this.cp1251): ByteArray {
        val body = if (ending == LineEnding.Lf) edited else edited.replace("\n", ending.text)
        val bytes = if (cp1251) ApiEncoding.Windows1251.encode(body) else body.encodeToByteArray()
        return if (bom && !cp1251) BOM + bytes else bytes
    }

    val charset: String get() = if (cp1251) "cp1251" else if (bom) "UTF-8 BOM" else "UTF-8"
}

private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

private const val SNIFF = 8192

fun decodeText(bytes: ByteArray, forceCp1251: Boolean = false): TextDocument? {
    val head = bytes.copyOf(minOf(bytes.size, SNIFF))
    if (head.any { it == 0.toByte() }) return null
    val controls = head.count { b -> val c = b.toInt() and 0xFF; c < 0x20 && c !in listOf(9, 10, 13, 12, 27) }
    if (head.isNotEmpty() && controls * 10 > head.size) return null
    val bom = bytes.size >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]
    val body = if (bom) bytes.copyOfRange(3, bytes.size) else bytes
    val utf8 = if (forceCp1251) null else try {
        body.decodeToString(throwOnInvalidSequence = true)
    } catch (e: CharacterCodingException) {
        null
    }
    val raw = utf8 ?: ApiEncoding.Windows1251.decode(body)
    val crlf = Regex("\r\n").findAll(raw).count()
    val lf = raw.count { it == '\n' } - crlf
    val cr = raw.count { it == '\r' } - crlf
    val ending = when {
        crlf >= lf && crlf >= cr && crlf > 0 -> LineEnding.CrLf
        cr > lf && cr > crlf -> LineEnding.Cr
        else -> LineEnding.Lf
    }
    val mixed = listOf(crlf, lf, cr).count { it > 0 } > 1
    val text = if (crlf == 0 && cr == 0) raw else raw.replace("\r\n", "\n").replace('\r', '\n')
    return TextDocument(text, ending, utf8 == null, bom && utf8 != null, mixed)
}
