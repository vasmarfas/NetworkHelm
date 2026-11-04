package com.vasmarfas.networkhelm.miknative

internal class ByteSink(capacity: Int = 64) {
    private var buffer = ByteArray(capacity)

    var size = 0
        private set

    fun write(byte: Int) {
        reserve(1)
        buffer[size++] = byte.toByte()
    }

    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        reserve(length)
        bytes.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun reserve(extra: Int) {
        if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }
}

// the check of a MAC must not stop at the first byte that differs
internal fun sameBytes(left: ByteArray, right: ByteArray): Boolean {
    var difference = left.size xor right.size
    for (i in 0 until minOf(left.size, right.size)) difference = difference or (left[i].toInt() xor right[i].toInt())
    return difference == 0
}

internal expect fun secureRandomBytes(size: Int): ByteArray
