package com.vasmarfas.networkhelm.offline

// no inflater is shared by all platforms, so deflate (RFC 1951) is read bit by bit, as puff.c of zlib does

private const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
private const val MAX_ARCHIVE_ENTRIES = 4_096

internal fun untar(bytes: ByteArray, maxBytes: Int = MAX_ARCHIVE_BYTES, maxEntries: Int = MAX_ARCHIVE_ENTRIES): Map<String, ByteArray> {
    val files = linkedMapOf<String, ByteArray>()
    var at = 0
    var entries = 0
    var total = 0L
    while (at + 512 <= bytes.size) {
        val header = bytes.copyOfRange(at, at + 512)
        if (header.all { it == 0.toByte() }) break
        require(++entries <= maxEntries) { "the archive has more than $maxEntries entries" }
        val size = field(header, 124, 12).trim().toLongOrNull(8)?.takeIf { it >= 0 && it <= bytes.size.toLong() - at - 512 }?.toInt()
            ?: throw IllegalArgumentException("a tar header without a size the archive holds")
        total += size
        require(total <= maxBytes) { "the archive expands beyond $maxBytes bytes" }
        val name = field(header, 0, 100)
        val prefix = field(header, 345, 155)
        val type = header[156].toInt().toChar()
        at += 512
        if (type == '0' || type == '\u0000') files[(if (prefix.isEmpty()) name else "$prefix/$name").removePrefix("./")] = bytes.copyOfRange(at, at + size)
        at += (size + 511) / 512 * 512
    }
    return files
}

private fun field(header: ByteArray, from: Int, length: Int): String =
    header.copyOfRange(from, from + length).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()

internal fun gunzip(bytes: ByteArray, maxBytes: Int = MAX_ARCHIVE_BYTES): ByteArray {
    require(maxBytes > 0)
    require(bytes.size >= 18 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte() && bytes[2] == 8.toByte()) { "not gzip" }
    val flags = bytes[3].toInt()
    var at = 10
    if (flags and 4 != 0) at += 2 + (bytes[at].toInt() and 0xFF) + ((bytes[at + 1].toInt() and 0xFF) shl 8)
    for (flag in listOf(8, 16)) {
        if (flags and flag == 0) continue
        while (at < bytes.size && bytes[at] != 0.toByte()) at++
        at++
    }
    if (flags and 2 != 0) at += 2
    return Inflater(bytes, at, maxBytes).inflate()
}

private class Inflater(private val data: ByteArray, private var at: Int, private val limit: Int) {
    private var buffer = 0
    private var count = 0
    private var out = ByteArray(minOf(64 * 1024, limit))
    private var size = 0

    fun inflate(): ByteArray {
        do {
            val last = bits(1)
            when (bits(2)) {
                0 -> stored()
                1 -> codes(fixedLiterals, fixedDistances)
                2 -> dynamic()
                else -> throw IllegalArgumentException("a deflate block of an unknown type")
            }
        } while (last == 0)
        return out.copyOf(size)
    }

    private fun bits(wanted: Int): Int {
        while (count < wanted) {
            require(at < data.size) { "the deflate data ends early" }
            buffer = buffer or ((data[at++].toInt() and 0xFF) shl count)
            count += 8
        }
        val value = buffer and ((1 shl wanted) - 1)
        buffer = buffer ushr wanted
        count -= wanted
        return value
    }

    private fun put(byte: Byte) {
        require(size < limit) { "the archive expands beyond $limit bytes" }
        if (size == out.size) out = out.copyOf(minOf(size.toLong() * 2, limit.toLong()).toInt())
        out[size++] = byte
    }

    private fun stored() {
        buffer = 0
        count = 0
        require(at + 4 <= data.size) { "the deflate data ends early" }
        val length = (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)
        at += 4
        require(at + length <= data.size) { "the deflate data ends early" }
        repeat(length) { put(data[at++]) }
    }

    private fun codes(literals: Huffman, distances: Huffman) {
        while (true) {
            val symbol = decode(literals)
            when {
                symbol < 256 -> put(symbol.toByte())
                symbol == 256 -> return
                else -> {
                    val index = symbol - 257
                    require(index < LENGTH_BASE.size) { "a length code out of range" }
                    val length = LENGTH_BASE[index] + bits(LENGTH_EXTRA[index])
                    val code = decode(distances)
                    require(code < DISTANCE_BASE.size) { "a distance code out of range" }
                    val distance = DISTANCE_BASE[code] + bits(DISTANCE_EXTRA[code])
                    require(distance <= size) { "a distance back past the start" }
                    repeat(length) { put(out[size - distance]) }
                }
            }
        }
    }

    private fun dynamic() {
        val literalCount = bits(5) + 257
        val distanceCount = bits(5) + 1
        val lengthCount = bits(4) + 4
        require(literalCount <= 286 && distanceCount <= 30) { "too many deflate codes" }
        val lengthLengths = IntArray(19)
        for (i in 0 until lengthCount) lengthLengths[LENGTH_ORDER[i]] = bits(3)
        val lengthCode = Huffman(lengthLengths)
        val lengths = IntArray(literalCount + distanceCount)
        var index = 0
        while (index < lengths.size) {
            val symbol = decode(lengthCode)
            if (symbol < 16) {
                lengths[index++] = symbol
                continue
            }
            require(symbol != 16 || index > 0) { "a repeat with nothing before it" }
            val value = if (symbol == 16) lengths[index - 1] else 0
            val times = when (symbol) {
                16 -> 3 + bits(2)
                17 -> 3 + bits(3)
                else -> 11 + bits(7)
            }
            require(index + times <= lengths.size) { "a repeat past the end of the lengths" }
            repeat(times) { lengths[index++] = value }
        }
        require(lengths[256] != 0) { "no code for the end of the block" }
        codes(Huffman(lengths.copyOfRange(0, literalCount)), Huffman(lengths.copyOfRange(literalCount, lengths.size)))
    }

    private fun decode(huffman: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (length in 1..15) {
            code = code or bits(1)
            val count = huffman.counts[length]
            if (code - first < count) return huffman.symbols[index + code - first]
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        throw IllegalArgumentException("a deflate code no table holds")
    }
}

private class Huffman(lengths: IntArray) {
    val counts = IntArray(16)
    val symbols = IntArray(lengths.size)

    init {
        for (length in lengths) counts[length]++
        counts[0] = 0
        val offsets = IntArray(16)
        for (length in 1 until 15) offsets[length + 1] = offsets[length] + counts[length]
        for (symbol in lengths.indices) if (lengths[symbol] != 0) symbols[offsets[lengths[symbol]]++] = symbol
    }
}

private val fixedLiterals = Huffman(IntArray(288) { symbol -> if (symbol < 144) 8 else if (symbol < 256) 9 else if (symbol < 280) 7 else 8 })
private val fixedDistances = Huffman(IntArray(30) { 5 })

private val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
private val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
private val DISTANCE_BASE = intArrayOf(
    1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
)
private val DISTANCE_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
private val LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
