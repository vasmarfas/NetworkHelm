package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.ssh.ShellChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MEPTY = 76L
private const val MEPTY_LOGIN = 0x0A0065L
private const val MEPTY_CLOSE = 0x0A0066L
private const val MEPTY_DATA = 0x0A0067L
private const val MEPTY_RESIZE = 0x0A0068L
private val SYSTEM_INFO = listOf(13L, 4L)
private const val PASSWORD = 1
private const val DATA = 2
private const val COUNTER = 3
private const val COLUMNS = 5
private const val ROWS = 6
private const val TERMINAL_TYPE = 7
private const val CONNECTION_TYPE = 8
private const val SETTINGS = 0x0B
private const val SETTING = 1
private const val WRITE_CHUNK = 1024

// the router sends about 8 KB at a time and waits for the byte count of what arrived before it sends more
class NativeTerminal private constructor(
    private val connection: NativeConnection,
    private val source: NativeSource,
    private val sessionId: Long,
) : ShellChannel {
    private val writeLock = Mutex()
    private var pending = ByteArray(0)
    private var pendingOffset = 0
    private var received = 0L
    private var acknowledged = 0L
    private var sent = 0L
    private var ended = false

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (pendingOffset == pending.size) {
            if (ended) return -1
            if (received != acknowledged && source.isIdle()) acknowledge()
            val message = source.next() ?: return -1
            if (message.number(M2Key.SessionId) != sessionId) continue
            when (message.number(M2Key.Command)) {
                MEPTY_CLOSE -> ended = true
                MEPTY_DATA -> {
                    pending = message.raw(DATA) ?: continue
                    pendingOffset = 0
                    received += pending.size
                }
            }
        }
        val count = minOf(length, pending.size - pendingOffset)
        pending.copyInto(buffer, offset, pendingOffset, pendingOffset + count)
        pendingOffset += count
        return count
    }

    override suspend fun write(bytes: ByteArray) {
        for (start in bytes.indices step WRITE_CHUNK) {
            val chunk = bytes.copyOfRange(start, minOf(start + WRITE_CHUNK, bytes.size))
            writeLock.withLock {
                source.send(listOf(MEPTY), *data(sent, chunk))
                sent += chunk.size
            }
        }
    }

    override fun resize(columns: Int, rows: Int) = background {
        command(MEPTY_RESIZE, COLUMNS to M2Value.U32(columns.toLong()), ROWS to M2Value.U32(rows.toLong()))
    }

    override fun close() = background {
        try {
            if (!ended) command(MEPTY_CLOSE)
        } finally {
            source.close()
        }
    }

    private fun background(block: suspend () -> Unit) {
        connection.scope.launch {
            try {
                block()
            } catch (e: DeviceException) {
                // the session ended before the call went out
            }
        }
    }

    private suspend fun command(command: Long, vararg fields: Pair<Int, M2Value>) = writeLock.withLock {
        source.send(
            listOf(MEPTY),
            M2Key.Request to M2Value.Bool(true),
            M2Key.RequestId to M2Value.U8(source.nextRequestId()),
            M2Key.SessionId to M2Value.U32(sessionId),
            M2Key.Command to M2Value.U32(command),
            *fields,
        )
    }

    private suspend fun acknowledge() = writeLock.withLock {
        source.send(listOf(MEPTY), *data(received, null))
        acknowledged = received
    }

    private fun data(counter: Long, bytes: ByteArray?): Array<Pair<Int, M2Value>> = listOfNotNull(
        COUNTER to M2Value.U32(counter),
        M2Key.SessionId to M2Value.U32(sessionId),
        M2Key.Command to M2Value.U32(MEPTY_DATA),
        bytes?.let { DATA to M2Value.Raw(it) },
    ).toTypedArray()

    companion object {
        suspend fun open(connection: NativeConnection, password: String, term: String, columns: Int, rows: Int): NativeTerminal {
            val source = connection.newSource()
            try {
                source.request(SYSTEM_INFO, M2Value.U8(7))
                val reply = source.request(
                    listOf(MEPTY),
                    M2Value.U32(MEPTY_LOGIN),
                    COLUMNS to M2Value.U32(columns.toLong()),
                    ROWS to M2Value.U32(rows.toLong()),
                    TERMINAL_TYPE to M2Value.Text(term),
                    CONNECTION_TYPE to M2Value.U32(0),
                    SETTINGS to M2Value.Message(M2Message.of(SETTING to M2Value.U32(0))),
                    PASSWORD to M2Value.Text(password),
                )
                val sessionId = reply.number(M2Key.SessionId)
                    ?: throw DeviceException(ErrorCategory.PermissionDenied, "the router refused to open a console")
                return NativeTerminal(connection, source, sessionId).also { it.acknowledge() }
            } catch (e: Exception) {
                source.close()
                throw e
            }
        }
    }
}
