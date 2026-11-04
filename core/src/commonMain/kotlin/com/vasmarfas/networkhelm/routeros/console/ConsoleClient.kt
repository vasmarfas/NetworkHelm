package com.vasmarfas.networkhelm.routeros.console

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.LinkState
import com.vasmarfas.networkhelm.routeros.api.ApiConnectionClosedException
import com.vasmarfas.networkhelm.routeros.api.ApiEncoding
import com.vasmarfas.networkhelm.routeros.api.ApiReply
import com.vasmarfas.networkhelm.routeros.api.ApiResult
import com.vasmarfas.networkhelm.routeros.api.ApiTrapException
import com.vasmarfas.networkhelm.routeros.api.RouterOsClient
import com.vasmarfas.networkhelm.routeros.api.RouterOsLink
import com.vasmarfas.networkhelm.routeros.api.rounds
import com.vasmarfas.networkhelm.session.SafeMode
import com.vasmarfas.networkhelm.ssh.ShellChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException

private val BOOLEAN = Regex("invalid value of (\\S+), must be either yes or no")
private val REFUSAL = Regex("^bad (?:parameter|command name) (\\S+)")
private const val PROFILE = "/tool/profile"
internal const val LINE_LIMIT = 16_000

// RouterOS 6 redraws the line after every key, so a line of n characters costs about n squared
private const val CLASSIC_LINE_LIMIT = 2_500
private const val CALL_MILLIS = 120_000L
private const val ROUND_MILLIS = 1_000L

class ConsoleClient internal constructor(
    private val size: Int = 4,
    private val dialect: Dialect = Dialect.Serialize,
    private val encoding: ApiEncoding = ApiEncoding.Utf8,
    private val open: suspend () -> ShellChannel,
) : RouterOsClient {
    private val slots = Channel<ConsoleTerminal?>(size).also { repeat(size) { _ -> it.trySend(null) } }

    private val yesNo = MutableStateFlow(setOf("disabled"))

    private val refused = MutableStateFlow(emptyMap<String, Set<String>>())
    private val link = MutableStateFlow(LinkState.Up)

    // Safe Mode is bound to the session that took it, so one console, kept out of the pool, makes every change
    private val held = MutableStateFlow<ConsoleTerminal?>(null)
    private val heldLine = Mutex()
    private val mode = Mutex()

    override val status: Flow<LinkState> = link.asStateFlow()

    override val safeMode: SafeMode = object : SafeMode {
        override val active: Flow<Boolean> = held.map { it != null }

        override val actions: Flow<Int> = flow {
            while (true) {
                emit(if (held.value == null) 0 else undoable())
                delay(ROUND_MILLIS)
            }
        }

        override suspend fun enter(): Boolean = mode.withLock {
            if (held.value != null) return@withLock true
            val console = acquire()
            val taken = try {
                console.safe()
            } catch (e: Throwable) {
                release(console, false)
                throw e
            }
            if (taken) held.value = console else release(console, true)
            taken
        }

        override suspend fun commit() = mode.withLock {
            val console = held.value ?: return@withLock
            var released = false
            try {
                heldLine.withLock { console.safe() }
                released = true
            } finally {
                held.value = null
                release(console, released)
            }
        }

        override suspend fun rollback() = mode.withLock {
            val console = held.value ?: return@withLock
            held.value = null
            heldLine.withLock { release(console, false) }
        }
    }

    override suspend fun call(words: List<String>): ApiResult {
        var call = translate(words)
        var answer = run(call)
        if (!answer.ok) {
            val name = BOOLEAN.find(answer.text)?.groupValues?.get(1)
            if (name != null && words.any { it == "=$name=true" || it == "=$name=false" }) {
                yesNo.update { it + name }
                call = translate(words)
                answer = run(call)
            }
        }
        while (!answer.ok) {
            val last = answer.text.substringAfterLast('\n')
            val menu = call.menu
            val word = REFUSAL.find(last)?.groupValues?.get(1)
            call = when {
                menu != null && word != null && word in call.offered -> {
                    refused.update { it + (menu to it[menu].orEmpty() + word) }
                    translate(words)
                }
                last.startsWith(AS_VALUE) -> call.fallback ?: break
                else -> break
            }
            answer = run(call)
        }
        if (!answer.ok) throw trap(answer.text.ifBlank { "the console gave no answer" }.let { if (call.probe) probed(it) else it })
        return result(call, answer.text)
    }

    private fun translate(words: List<String>) = try {
        when (dialect) {
            Dialect.Serialize -> consoleCall(words, yesNo.value, refused.value, encoding)
            Dialect.Classic -> classicCall(words, yesNo.value, refused.value, encoding)
        }
    } catch (e: IllegalArgumentException) {
        throw DeviceException(ErrorCategory.ArgumentValue, e.message.orEmpty(), e)
    }

    override fun command(words: List<String>, sent: CompletableDeferred<Unit>?): Flow<ApiReply> = flow {
        if (words.first().endsWith("/listen")) throw ApiTrapException(null, "listen is not available over the console")
        sent?.complete(Unit)
        val stream = rounds(words) { asked -> call(asked).rows.map { row -> if (words.first() == PROFILE) percents(row) else row } }
        if (stream == null) call(words).rows.forEach { emit(ApiReply("!re", it, null)) } else emitAll(stream)
    }

    // the console writes the usage of profile in tenths of a percent, the API in percents
    private fun percents(row: Map<String, String>): Map<String, String> {
        val tenths = row["usage"]?.toIntOrNull() ?: return row
        return row + ("usage" to if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}")
    }

    override fun <T> each(block: suspend FlowCollector<T>.(RouterOsLink) -> Unit): Flow<T> = flow { block(this@ConsoleClient) }

    fun warm(scope: CoroutineScope) {
        repeat(size - 1) {
            scope.launch {
                val console = try {
                    acquire()
                } catch (e: ApiConnectionClosedException) {
                    return@launch
                } catch (e: DeviceException) {
                    return@launch
                }
                release(console, true)
            }
        }
    }

    override fun close() {
        link.value = LinkState.Closed
        held.getAndUpdate { null }?.close()
        slots.close()
        while (true) {
            val slot = slots.tryReceive()
            if (!slot.isSuccess) break
            slot.getOrNull()?.close()
        }
    }

    private suspend fun undoable(): Int = call(listOf("/system/history/print", "=count-only=", "?floating-undo=true")).done["ret"]?.toIntOrNull() ?: 0

    private suspend fun run(call: ConsoleCall): Answer {
        val limit = if (dialect == Dialect.Classic) CLASSIC_LINE_LIMIT else LINE_LIMIT
        if (call.line.length > limit) throw DeviceException(ErrorCategory.ArgumentValue, "the line for the console is ${call.line.length} characters long, longer than the $limit that this router takes")
        if (encoding == ApiEncoding.Windows1251) {
            try {
                encoding.encode(call.line)
            } catch (e: IllegalArgumentException) {
                throw DeviceException(ErrorCategory.ArgumentValue, e.message.orEmpty(), e)
            }
        }
        val holder = held.value
        if (call.changes && holder != null) {
            return heldLine.withLock {
                try {
                    ask(holder, call)
                } catch (e: ApiConnectionClosedException) {
                    held.compareAndSet(holder, null)
                    throw e
                }
            }
        }
        return try {
            attempt(call)
        } catch (e: ApiConnectionClosedException) {
            if (!call.quick) throw e
            attempt(call)
        }
    }

    private suspend fun attempt(call: ConsoleCall): Answer {
        val console = acquire()
        var healthy = false
        try {
            return ask(console, call).also { healthy = true }
        } finally {
            release(console, healthy)
        }
    }

    private suspend fun ask(console: ConsoleTerminal, call: ConsoleCall): Answer {
        try {
            val answer = withTimeoutOrNull(CALL_MILLIS) { console.run(call.line, call.quick) }
                ?: throw ApiConnectionClosedException("the router did not answer in ${CALL_MILLIS / 1000} s")
            link.update { state -> if (state == LinkState.Closed) state else LinkState.Up }
            return answer
        } catch (e: CancellationException) {
            throw e
        } catch (e: DeviceException) {
            throw if (e.category == ErrorCategory.ConnectionLost) dropped(e) else e
        } catch (e: ApiConnectionClosedException) {
            throw lost(e)
        } catch (e: Exception) {
            throw lost(ApiConnectionClosedException(e.message ?: "the console failed", e))
        }
    }

    private fun lost(error: ApiConnectionClosedException): ApiConnectionClosedException {
        link.update { state -> if (state == LinkState.Closed) state else LinkState.Reconnecting }
        return error
    }

    private fun dropped(error: DeviceException): ApiConnectionClosedException = lost(ApiConnectionClosedException(error.message.orEmpty(), error))

    private fun result(call: ConsoleCall, text: String): ApiResult = when (call.reply) {
        Reply.Rows -> ApiResult(asked(call, parse(text)), emptyMap())
        Reply.Records -> {
            val printed = records(text)
            yesNo.update { it + printed.flags }
            ApiResult(asked(call, printed.rows), emptyMap())
        }
        Reply.Pairs -> ApiResult(pairs(text), emptyMap())
        Reply.Count, Reply.Id, Reply.Text -> ApiResult(emptyList(), if (text.isBlank()) emptyMap() else mapOf("ret" to text.trim()))
        Reply.Nothing -> ApiResult(emptyList(), emptyMap())
    }

    private fun asked(call: ConsoleCall, rows: List<Map<String, String>>) =
        if (call.proplist.isEmpty()) rows else rows.map { row -> row.filterKeys { it == ".id" || it in call.proplist } }

    private fun parse(text: String): List<Map<String, String>> = try {
        rows(text)
    } catch (e: SerializationException) {
        throw DeviceException(ErrorCategory.Api, "the router answered with something other than rows: ${text.take(200)}", e)
    }

    private suspend fun acquire(): ConsoleTerminal {
        val slot = try {
            slots.receive()
        } catch (e: ClosedReceiveChannelException) {
            throw ApiConnectionClosedException("the client is closed", e)
        }
        if (slot != null) return slot
        return try {
            ConsoleTerminal.open(open, dialect, encoding)
        } catch (e: Throwable) {
            slots.trySend(null)
            throw when {
                e is ApiConnectionClosedException -> lost(e)
                e is DeviceException && e.category == ErrorCategory.ConnectionLost -> dropped(e)
                else -> e
            }
        }
    }

    private fun release(console: ConsoleTerminal, healthy: Boolean) {
        val kept = console.takeIf { healthy }
        if (kept == null) console.close()
        if (!slots.trySend(kept).isSuccess) kept?.close()
    }
}
