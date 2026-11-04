package com.vasmarfas.networkhelm.qr

import kotlin.math.abs

class QrCode private constructor(val version: Int, val mask: Int, private val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size

    operator fun get(row: Int, column: Int): Boolean = modules[row][column]

    companion object {
        fun encode(text: String): QrCode = encode(text.encodeToByteArray())

        fun encode(data: ByteArray): QrCode {
            val version = (1..Blocks.size).firstOrNull { 4 + countBits(it) + data.size * 8 <= Blocks[it - 1].data * 8 }
                ?: throw IllegalArgumentException("${data.size} bytes do not fit a QR code of version ${Blocks.size}")
            val codewords = codewords(data, version)
            val size = 17 + 4 * version
            val modules = Array(size) { BooleanArray(size) }
            val function = Array(size) { BooleanArray(size) }
            functionPatterns(version, modules, function)
            place(codewords, modules, function)
            val mask = (0 until 8).minBy { penalty(masked(modules, function, it)) }
            return QrCode(version, mask, masked(modules, function, mask))
        }

        private fun countBits(version: Int) = if (version <= 9) 8 else 16

        private fun codewords(data: ByteArray, version: Int): IntArray {
            val blocks = Blocks[version - 1]
            val capacity = blocks.data * 8
            val bits = Bits()
            bits.add(0b0100, 4)
            bits.add(data.size, countBits(version))
            for (byte in data) bits.add(byte.toInt() and 0xFF, 8)
            bits.add(0, minOf(4, capacity - bits.size))
            bits.add(0, (8 - bits.size % 8) % 8)
            var pad = 0
            while (bits.size < capacity) bits.add(if (pad++ % 2 == 0) 0xEC else 0x11, 8)
            val bytes = bits.bytes()
            val divisor = divisor(blocks.ecPerBlock)
            var start = 0
            val dataBlocks = (0 until blocks.short + blocks.long).map { index ->
                val length = blocks.shortData + if (index < blocks.short) 0 else 1
                bytes.copyOfRange(start, start + length).also { start += length }
            }
            val ecBlocks = dataBlocks.map { remainder(it, divisor) }
            val result = ArrayList<Int>()
            for (i in 0..blocks.shortData) for (block in dataBlocks) if (i < block.size) result += block[i]
            for (i in 0 until blocks.ecPerBlock) for (block in ecBlocks) result += block[i]
            return result.toIntArray()
        }

        private fun functionPatterns(version: Int, modules: Array<BooleanArray>, function: Array<BooleanArray>) {
            val size = modules.size
            fun set(row: Int, column: Int, dark: Boolean) {
                modules[row][column] = dark
                function[row][column] = true
            }
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            for ((top, left) in listOf(0 to 0, 0 to size - 7, size - 7 to 0)) {
                for (dr in -1..7) for (dc in -1..7) {
                    val row = top + dr
                    val column = left + dc
                    if (row !in 0 until size || column !in 0 until size) continue
                    val ring = maxOf(abs(dr - 3), abs(dc - 3))
                    set(row, column, ring != 2 && ring != 4)
                }
            }
            val centres = AlignmentCentres[version - 1]
            for (row in centres) for (column in centres) {
                val last = centres.last()
                if (row == 6 && column == 6 || row == 6 && column == last || row == last && column == 6) continue
                for (dr in -2..2) for (dc in -2..2) set(row + dr, column + dc, maxOf(abs(dr), abs(dc)) != 1)
            }
            for (i in 0..8) {
                function[8][i] = true
                function[i][8] = true
            }
            for (i in 0 until 8) function[8][size - 1 - i] = true
            for (i in 0 until 7) function[size - 1 - i][8] = true
            set(size - 8, 8, true)
            if (version >= 7) {
                val bits = versionBits(version)
                for (i in 0 until 18) {
                    val dark = (bits ushr i) and 1 == 1
                    val near = i / 3
                    val far = size - 11 + i % 3
                    set(near, far, dark)
                    set(far, near, dark)
                }
            }
        }

        private fun place(codewords: IntArray, modules: Array<BooleanArray>, function: Array<BooleanArray>) {
            val size = modules.size
            var bit = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                val upward = (right + 1) and 2 == 0
                for (step in 0 until size) {
                    val row = if (upward) size - 1 - step else step
                    for (column in right downTo right - 1) {
                        if (function[row][column] || bit >= codewords.size * 8) continue
                        modules[row][column] = (codewords[bit / 8] ushr (7 - bit % 8)) and 1 == 1
                        bit++
                    }
                }
                right -= 2
            }
        }

        private fun masked(modules: Array<BooleanArray>, function: Array<BooleanArray>, mask: Int): Array<BooleanArray> {
            val size = modules.size
            val result = Array(size) { row -> BooleanArray(size) { column -> modules[row][column] xor (!function[row][column] && flips(mask, row, column)) } }
            val format = formatBits(mask)
            for (i in 0 until 15) {
                val dark = (format ushr i) and 1 == 1
                when {
                    i < 6 -> result[i][8] = dark
                    i == 6 -> result[7][8] = dark
                    i == 7 -> result[8][8] = dark
                    i == 8 -> result[8][7] = dark
                    else -> result[8][14 - i] = dark
                }
                if (i < 8) result[8][size - 1 - i] = dark else result[size - 15 + i][8] = dark
            }
            return result
        }

        private fun flips(mask: Int, row: Int, column: Int): Boolean = when (mask) {
            0 -> (row + column) % 2 == 0
            1 -> row % 2 == 0
            2 -> column % 3 == 0
            3 -> (row + column) % 3 == 0
            4 -> (row / 2 + column / 3) % 2 == 0
            5 -> row * column % 2 + row * column % 3 == 0
            6 -> (row * column % 2 + row * column % 3) % 2 == 0
            else -> ((row + column) % 2 + row * column % 3) % 2 == 0
        }

        internal fun formatBits(mask: Int): Int {
            var remainder = mask
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            return ((mask shl 10) or remainder) xor 0x5412
        }

        internal fun versionBits(version: Int): Int {
            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            return (version shl 12) or remainder
        }

        private fun penalty(modules: Array<BooleanArray>): Int {
            val size = modules.size
            var result = 0
            for (line in 0 until size) {
                for (vertical in listOf(false, true)) {
                    fun at(i: Int) = if (vertical) modules[i][line] else modules[line][i]
                    var run = 1
                    for (i in 1..size) {
                        if (i < size && at(i) == at(i - 1)) {
                            run++
                            continue
                        }
                        if (run >= 5) result += run - 2
                        run = 1
                    }
                    for (i in 0..size - 11) {
                        val window = BooleanArray(11) { at(i + it) }
                        if (window.contentEquals(FinderLight) || window.contentEquals(LightFinder)) result += 40
                    }
                }
            }
            for (row in 0 until size - 1) for (column in 0 until size - 1) {
                val colour = modules[row][column]
                if (modules[row][column + 1] == colour && modules[row + 1][column] == colour && modules[row + 1][column + 1] == colour) result += 3
            }
            val dark = modules.sumOf { line -> line.count { it } }
            result += abs(dark * 100 / (size * size) - 50) / 5 * 10
            return result
        }

        private fun divisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (i in 0 until degree) {
                    result[i] = multiply(result[i], root)
                    if (i + 1 < degree) result[i] = result[i] xor result[i + 1]
                }
                root = multiply(root, 2)
            }
            return result
        }

        internal fun remainder(data: IntArray, divisor: IntArray): IntArray {
            val result = IntArray(divisor.size)
            for (byte in data) {
                val factor = byte xor result[0]
                for (i in 0 until result.size - 1) result[i] = result[i + 1]
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor multiply(divisor[i], factor)
            }
            return result
        }

        internal fun remainder(data: IntArray, degree: Int): IntArray = remainder(data, divisor(degree))

        private fun multiply(x: Int, y: Int): Int {
            var result = 0
            for (i in 7 downTo 0) {
                result = (result shl 1) xor ((result ushr 7) * 0x11D)
                result = result xor (((y ushr i) and 1) * x)
            }
            return result
        }

        private val FinderLight = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        private val LightFinder = FinderLight.reversedArray()
    }
}

private class Bits {
    private val bits = ArrayList<Boolean>()
    val size: Int get() = bits.size

    fun add(value: Int, count: Int) {
        for (i in count - 1 downTo 0) bits += (value ushr i) and 1 == 1
    }

    fun bytes(): IntArray = IntArray(bits.size / 8) { byte -> (0 until 8).fold(0) { acc, i -> (acc shl 1) or if (bits[byte * 8 + i]) 1 else 0 } }
}

private class BlockLayout(val ecPerBlock: Int, val short: Int, val shortData: Int, val long: Int) {
    val data: Int get() = short * shortData + long * (shortData + 1)
}

private val Blocks = listOf(
    BlockLayout(10, 1, 16, 0),
    BlockLayout(16, 1, 28, 0),
    BlockLayout(26, 1, 44, 0),
    BlockLayout(18, 2, 32, 0),
    BlockLayout(24, 2, 43, 0),
    BlockLayout(16, 4, 27, 0),
    BlockLayout(18, 4, 31, 0),
    BlockLayout(22, 2, 38, 2),
    BlockLayout(22, 3, 36, 2),
    BlockLayout(26, 4, 43, 1),
    BlockLayout(30, 1, 50, 4),
    BlockLayout(22, 6, 36, 2),
    BlockLayout(22, 8, 37, 1),
    BlockLayout(24, 4, 40, 5),
    BlockLayout(24, 5, 41, 5),
    BlockLayout(28, 7, 45, 3),
    BlockLayout(28, 10, 46, 1),
    BlockLayout(26, 9, 43, 4),
    BlockLayout(26, 3, 44, 11),
    BlockLayout(26, 3, 41, 13),
)

private val AlignmentCentres = listOf(
    intArrayOf(),
    intArrayOf(6, 18),
    intArrayOf(6, 22),
    intArrayOf(6, 26),
    intArrayOf(6, 30),
    intArrayOf(6, 34),
    intArrayOf(6, 22, 38),
    intArrayOf(6, 24, 42),
    intArrayOf(6, 26, 46),
    intArrayOf(6, 28, 50),
    intArrayOf(6, 30, 54),
    intArrayOf(6, 32, 58),
    intArrayOf(6, 34, 62),
    intArrayOf(6, 26, 46, 66),
    intArrayOf(6, 26, 48, 70),
    intArrayOf(6, 26, 50, 74),
    intArrayOf(6, 30, 54, 78),
    intArrayOf(6, 30, 56, 82),
    intArrayOf(6, 30, 58, 86),
    intArrayOf(6, 34, 62, 90),
)
