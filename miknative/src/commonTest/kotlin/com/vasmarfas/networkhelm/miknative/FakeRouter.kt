package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch

private class ChannelStream(private val incoming: Channel<ByteArray>, private val outgoing: Channel<ByteArray>) : ByteStream {
    private var current = ByteArray(0)
    private var position = 0

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (position == current.size) {
            current = incoming.receiveCatching().getOrNull() ?: return -1
            position = 0
        }
        val count = minOf(length, current.size - position)
        current.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override suspend fun write(bytes: ByteArray) {
        try {
            outgoing.send(bytes.copyOf())
        } catch (e: ClosedSendChannelException) {
            throw IllegalStateException("the other end is closed", e)
        }
    }

    override fun close() {
        outgoing.close()
    }
}

internal fun pipe(): Pair<ByteStream, ByteStream> {
    val toServer = Channel<ByteArray>(Channel.UNLIMITED)
    val toClient = Channel<ByteArray>(Channel.UNLIMITED)
    return ChannelStream(toClient, toServer) to ChannelStream(toServer, toClient)
}

internal class FakeRouter(
    scope: CoroutineScope,
    private val stream: ByteStream,
    private val user: String,
    private val password: String,
    private val handler: suspend FakeRouter.(M2Message) -> Unit,
) {
    private var channel: SecureChannel? = null
    val received = ArrayList<M2Message>()

    init {
        scope.launch { serve() }
    }

    suspend fun send(message: M2Message) {
        stream.write(checkNotNull(channel).seal(message.encode()))
    }

    suspend fun sendTogether(vararg messages: M2Message) {
        val secure = checkNotNull(channel)
        stream.write(encodeFrame(TAG_ENCRYPTED, messages.map { secure.record(it.encode()) }.reduce(ByteArray::plus)))
    }

    suspend fun reply(message: M2Message, vararg fields: Pair<Int, M2Value>) {
        val from = message.numbers(M2Key.From).orEmpty()
        val to = message.numbers(M2Key.To).orEmpty()
        val head = arrayOf(
            M2Key.To to M2Value.U32s(from.toLongArray()),
            M2Key.From to M2Value.U32s(to.toLongArray()),
            0xFF0003 to M2Value.U8(2),
        )
        val id = message[M2Key.RequestId]?.let { M2Key.RequestId to it }
        send(M2Message.of(*head, *listOfNotNull(id).toTypedArray(), *fields))
    }

    fun close() = stream.close()

    private suspend fun serve() {
        val frames = FrameReader(stream)
        val srp = EcSrp5Server(user, password, ByteArray(16) { (it + 1).toByte() })
        val hello = frames.next() ?: return
        stream.write(encodeFrame(TAG_ENCRYPTED, srp.challenge(hello.payload)))
        val confirmation = frames.next() ?: return
        val proof = srp.confirm(confirmation.payload)
        if (proof == null) {
            stream.close()
            return
        }
        stream.write(encodeFrame(TAG_ENCRYPTED, proof))
        val secure = SecureChannel(srp.keys())
        channel = secure
        while (true) {
            val frame = frames.next() ?: break
            for (body in secure.open(frame.payload)) {
                val message = M2Message.decode(body)
                received += message
                handler(message)
            }
        }
    }
}

internal fun M2Message.message(key: Int): M2Message? = (get(key) as? M2Value.Message)?.value
