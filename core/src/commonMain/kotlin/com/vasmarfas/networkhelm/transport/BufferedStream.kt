package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory

class BufferedStream(private val stream: ByteStream) : ByteStream {
    private var buffer = ByteArray(8 * 1024)
    private var start = 0
    private var end = 0

    suspend fun readLine(maxBytes: Int = Int.MAX_VALUE): String? {
        require(maxBytes >= 0)
        val capacity = minOf(maxBytes.toLong() + 2, Int.MAX_VALUE.toLong()).toInt()
        var scanned = 0
        while (true) {
            val newline = (start + scanned until end).firstOrNull { buffer[it] == LF }
            if (newline != null) {
                val lineEnd = if (newline > start && buffer[newline - 1] == CR) newline - 1 else newline
                if (lineEnd - start > maxBytes) throw tooLong(maxBytes)
                val line = buffer.decodeToString(start, lineEnd)
                start = newline + 1
                return line
            }
            scanned = end - start
            if (scanned >= capacity) throw tooLong(maxBytes)
            if (!fill(capacity)) {
                if (end - start > maxBytes) throw tooLong(maxBytes)
                return if (start < end) buffer.decodeToString(start, end).also { start = end } else null
            }
        }
    }

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (start == end && !fill()) return -1
        val count = minOf(length, end - start)
        this.buffer.copyInto(buffer, offset, start, start + count)
        start += count
        return count
    }

    override suspend fun write(bytes: ByteArray) = stream.write(bytes)

    override fun close() = stream.close()

    private suspend fun fill(capacity: Int = Int.MAX_VALUE): Boolean {
        compact()
        if (end == buffer.size) buffer = buffer.copyOf(minOf(buffer.size.toLong() * 2, capacity.toLong()).toInt())
        val count = stream.read(buffer, end, minOf(buffer.size - end, capacity - end))
        if (count < 0) return false
        end += count
        return true
    }

    private fun tooLong(limit: Int) = DeviceException(ErrorCategory.Api, "a response line exceeds $limit bytes")

    private fun compact() {
        if (start == 0) return
        buffer.copyInto(buffer, 0, start, end)
        end -= start
        start = 0
    }

    private companion object {
        const val CR = '\r'.code.toByte()
        const val LF = '\n'.code.toByte()
    }
}
