package com.vasmarfas.networkhelm.transport

class AnsweringServer(private val answer: (String) -> String) {
    val requests = mutableListOf<String>()

    fun open(): ByteStream = object : ByteStream {
        private val request = StringBuilder()
        private var reply: CannedStream? = null

        override suspend fun write(bytes: ByteArray) {
            request.append(bytes.decodeToString())
        }

        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val stream = reply ?: CannedStream(answer(request.toString())).also {
                requests += request.toString()
                reply = it
            }
            return stream.read(buffer, offset, length)
        }

        override fun close() {}
    }
}

fun httpReply(status: Int, body: String = "", head: String = "") = "HTTP/1.1 $status X\r\n${head}Content-Length: ${body.encodeToByteArray().size}\r\n\r\n$body"
