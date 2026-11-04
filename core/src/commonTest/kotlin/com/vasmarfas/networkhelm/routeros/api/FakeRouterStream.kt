package com.vasmarfas.networkhelm.routeros.api

import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.channels.Channel

class FakeRouterStream : ByteStream {
    val sent = Channel<List<String>>(Channel.UNLIMITED)
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var chunk = ByteArray(0)
    private var position = 0

    fun reply(vararg words: String) = replyBytes(words.map { it.encodeToByteArray() })

    fun replyBytes(words: List<ByteArray>) {
        incoming.trySend(encodeSentence(words))
    }

    suspend fun nextSent(): List<String> = sent.receive()

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position == chunk.size) {
            chunk = incoming.receiveCatching().getOrNull() ?: return -1
            position = 0
        }
        val count = minOf(length, chunk.size - position)
        chunk.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override suspend fun write(bytes: ByteArray) {
        var at = 0
        val sentence = SentenceReader { buffer, offset, length ->
            if (at == bytes.size) -1 else {
                val count = minOf(length, bytes.size - at)
                bytes.copyInto(buffer, offset, at, at + count)
                at += count
                count
            }
        }.next()
        sent.send(sentence.orEmpty().map { it.decodeToString() })
    }

    override fun close() {
        incoming.close()
    }
}
