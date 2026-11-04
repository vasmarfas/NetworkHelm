package com.vasmarfas.networkhelm.transport

class CannedStream(answer: String) : ByteStream {
    val written = StringBuilder()
    var closed = false
        private set
    private val data = answer.encodeToByteArray()
    private var at = 0

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (at == data.size) return -1
        val count = minOf(length, data.size - at, 7)
        data.copyInto(buffer, offset, at, at + count)
        at += count
        return count
    }

    override suspend fun write(bytes: ByteArray) {
        written.append(bytes.decodeToString())
    }

    override fun close() {
        closed = true
    }
}
