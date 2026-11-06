package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.transport.UdpLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class Oid(val arcs: List<Long>) : Comparable<Oid> {
    operator fun plus(arcs: List<Long>) = Oid(this.arcs + arcs)

    fun under(prefix: Oid) = arcs.size > prefix.arcs.size && arcs.subList(0, prefix.arcs.size) == prefix.arcs

    fun index(column: Oid) = arcs.drop(column.arcs.size)

    override fun compareTo(other: Oid): Int {
        for (i in 0 until minOf(arcs.size, other.arcs.size)) if (arcs[i] != other.arcs[i]) return arcs[i].compareTo(other.arcs[i])
        return arcs.size.compareTo(other.arcs.size)
    }

    override fun toString() = arcs.joinToString(".")
}

fun oid(text: String) = Oid(text.split('.').map(String::toLong))

sealed interface SnmpValue {
    data class Number(val tag: Int, val value: Long) : SnmpValue

    class Octets(val bytes: ByteArray) : SnmpValue

    class Address(val bytes: ByteArray) : SnmpValue

    data class Id(val oid: Oid) : SnmpValue

    data object Null : SnmpValue

    data class Missing(val tag: Int) : SnmpValue

    class Other(val tag: Int, val bytes: ByteArray) : SnmpValue
}

class Binding(val oid: Oid, val value: SnmpValue)

const val INTEGER = 0x02
const val COUNTER32 = 0x41
const val GAUGE32 = 0x42
const val TIMETICKS = 0x43
const val COUNTER64 = 0x46

internal const val OCTETS = 0x04
private const val NULL = 0x05
internal const val OID = 0x06
internal const val SEQUENCE = 0x30
internal const val IP_ADDRESS = 0x40
internal const val GET = 0xA0
private const val RESPONSE = 0xA2
private const val SET = 0xA3
private const val GET_BULK = 0xA5
internal const val REPORT = 0xA8
private const val TOO_BIG = 1

internal class Tlv(val tag: Int, val bytes: ByteArray, val from: Int, val start: Int, val end: Int) {
    fun children(): List<Tlv> = buildList {
        var at = start
        while (at < end) add(tlv(bytes, at, end).also { at = it.end })
    }

    fun signed(): Long {
        if (end == start || end - start > 8) throw malformed("an INTEGER of ${end - start} bytes")
        var value = bytes[start].toLong()
        for (i in start + 1 until end) value = (value shl 8) or (bytes[i].toLong() and 0xFF)
        return value
    }

    fun unsigned(): Long {
        var at = start
        while (at < end - 1 && bytes[at] == 0.toByte()) at++
        if (end == start || end - at > 8) throw malformed("a number of ${end - start} bytes")
        var value = 0L
        for (i in at until end) value = (value shl 8) or (bytes[i].toLong() and 0xFF)
        return value
    }

    fun content() = bytes.copyOfRange(start, end)

    fun whole() = bytes.copyOfRange(from, end)

    fun oid(): Oid {
        val subidentifiers = mutableListOf<Long>()
        var value = 0L
        for (i in start until end) {
            val byte = bytes[i].toInt() and 0xFF
            value = (value shl 7) or (byte and 0x7F).toLong()
            if (byte and 0x80 == 0) {
                subidentifiers += value
                value = 0
            }
        }
        val first = subidentifiers.firstOrNull() ?: throw malformed("an empty OBJECT IDENTIFIER")
        val top = minOf(first / 40, 2)
        return Oid(listOf(top, first - top * 40) + subidentifiers.drop(1))
    }
}

internal fun malformed(what: String) = DeviceException(ErrorCategory.Api, "the SNMP agent sent $what")

internal fun tlv(bytes: ByteArray, from: Int, until: Int): Tlv {
    if (until - from < 2) throw malformed("a cut message")
    val tag = bytes[from].toInt() and 0xFF
    var at = from + 1
    var length = bytes[at++].toInt() and 0xFF
    if (length and 0x80 != 0) {
        val count = length and 0x7F
        if (count == 0 || count > 3 || at + count > until) throw malformed("a length of $count bytes")
        length = 0
        repeat(count) { length = (length shl 8) or (bytes[at++].toInt() and 0xFF) }
    }
    if (at + length > until) throw malformed("a cut message")
    return Tlv(tag, bytes, from, at, at + length)
}

internal fun decoded(tlv: Tlv): SnmpValue = when (tlv.tag) {
    INTEGER -> SnmpValue.Number(INTEGER, tlv.signed())
    COUNTER32, GAUGE32, TIMETICKS, COUNTER64 -> SnmpValue.Number(tlv.tag, tlv.unsigned())
    OCTETS -> SnmpValue.Octets(tlv.content())
    IP_ADDRESS -> SnmpValue.Address(tlv.content())
    OID -> SnmpValue.Id(tlv.oid())
    NULL -> SnmpValue.Null
    0x80, 0x81, 0x82 -> SnmpValue.Missing(tlv.tag)
    else -> SnmpValue.Other(tlv.tag, tlv.content())
}

internal fun encoded(tag: Int, content: ByteArray): ByteArray {
    val size = content.size
    val length = when {
        size < 0x80 -> byteArrayOf(size.toByte())
        size < 0x100 -> byteArrayOf(0x81.toByte(), size.toByte())
        else -> byteArrayOf(0x82.toByte(), (size shr 8).toByte(), size.toByte())
    }
    return byteArrayOf(tag.toByte()) + length + content
}

internal fun integer(value: Long, tag: Int = INTEGER): ByteArray {
    var size = 8
    while (size > 1 && (value shr (size * 8 - 9)).let { it == 0L || it == -1L }) size--
    return encoded(tag, ByteArray(size) { (value shr ((size - 1 - it) * 8)).toByte() })
}

internal fun encoded(oid: Oid): ByteArray {
    val body = mutableListOf<Byte>()
    for (subidentifier in listOf(oid.arcs[0] * 40 + oid.arcs[1]) + oid.arcs.drop(2)) {
        val chunk = ArrayDeque<Byte>()
        var rest = subidentifier
        do {
            chunk.addFirst(((rest and 0x7F) or if (chunk.isEmpty()) 0L else 0x80L).toByte())
            rest = rest shr 7
        } while (rest > 0)
        body += chunk
    }
    return encoded(OID, body.toByteArray())
}

internal fun pdu(type: Int, id: Int, a: Int, b: Int, oids: List<Oid>): ByteArray {
    val bindings = oids.fold(ByteArray(0)) { all, oid -> all + encoded(SEQUENCE, encoded(oid) + encoded(NULL, ByteArray(0))) }
    return encoded(type, integer(id.toLong()) + integer(a.toLong()) + integer(b.toLong()) + encoded(SEQUENCE, bindings))
}

internal fun setPdu(id: Int, values: List<Pair<Oid, SnmpValue>>): ByteArray {
    val bindings = values.fold(ByteArray(0)) { all, (oid, value) -> all + encoded(SEQUENCE, encoded(oid) + encoded(value)) }
    return encoded(SET, integer(id.toLong()) + integer(0) + integer(0) + encoded(SEQUENCE, bindings))
}

// a value of 64 bits above Long.MAX_VALUE comes only from Counter64, which is never written
internal fun encoded(value: SnmpValue): ByteArray = when (value) {
    is SnmpValue.Number -> integer(value.value, value.tag)
    is SnmpValue.Octets -> encoded(OCTETS, value.bytes)
    is SnmpValue.Address -> encoded(IP_ADDRESS, value.bytes)
    is SnmpValue.Id -> encoded(value.oid)
    else -> throw IllegalArgumentException("a $value cannot be written")
}

internal fun request(community: String, pdu: ByteArray): ByteArray = encoded(SEQUENCE, integer(1) + encoded(OCTETS, community.encodeToByteArray()) + pdu)

internal class Response(val id: Int, val error: Int, val index: Int, val bindings: List<Binding>)

internal fun response(bytes: ByteArray): Response {
    val message = tlv(bytes, 0, bytes.size).takeIf { it.tag == SEQUENCE } ?: throw malformed("no message")
    return response(message.children().getOrNull(2) ?: throw malformed("no PDU"))
}

internal fun response(pdu: Tlv): Response {
    if (pdu.tag != RESPONSE) throw malformed("no Response")
    val fields = pdu.children()
    if (fields.size < 4) throw malformed("a Response of ${fields.size} fields")
    val bindings = fields[3].children().map { binding ->
        val (name, value) = binding.children().takeIf { it.size == 2 } ?: throw malformed("a variable binding of the wrong shape")
        Binding(name.oid(), decoded(value))
    }
    return Response(fields[0].signed().toInt(), fields[1].signed().toInt(), fields[2].signed().toInt(), bindings)
}

class SnmpClient(
    private val link: UdpLink,
    private val community: String,
    private val timeoutMillis: Int = 1_500,
    private val tries: Int = 3,
    private val user: SnmpUser? = null,
) {
    private val mutex = Mutex()
    private var next = Random.nextInt(1, 1 shl 30)
    private var usm: Usm? = null

    val encrypted: Boolean get() = user?.priv != null

    suspend fun get(oids: List<Oid>): List<Binding> = exchange(oids) { pdu(GET, it, 0, 0, oids) }

    suspend fun set(values: List<Pair<Oid, SnmpValue>>) {
        exchange(values.map { it.first }, writing = true) { setPdu(it, values) }
    }

    // the objects after from in the agent's order, as many as one answer holds
    suspend fun after(from: Oid): List<Binding> {
        var repetitions = MAX_BINDINGS
        while (true) {
            try {
                return exchange(listOf(from)) { pdu(GET_BULK, it, 0, repetitions, listOf(from)) }
            } catch (e: TooBig) {
                if (repetitions == 1) throw DeviceException(ErrorCategory.Api, "the SNMP agent cannot send even one object after $from")
                repetitions = maxOf(1, repetitions / 2)
            }
        }
    }

    // an agent that answers an OID not past the last one would be walked forever, so the walk ends there
    suspend fun subtree(root: Oid, most: Int): List<Binding> {
        val found = mutableListOf<Binding>()
        var at = root
        while (found.size < most) {
            val answer = after(at)
            for (binding in answer) {
                if (binding.value is SnmpValue.Missing || !binding.oid.under(root) || binding.oid <= at || found.size >= most) return found
                found += binding
                at = binding.oid
            }
            if (answer.isEmpty()) return found
        }
        return found
    }

    suspend fun columns(columns: List<Oid>, most: Int? = null): Map<Oid, Map<List<Long>, SnmpValue>> {
        val rows = columns.associateWith { linkedMapOf<List<Long>, SnmpValue>() }
        val at = columns.associateWithTo(mutableMapOf()) { it }
        var limit = MAX_BINDINGS
        while (at.isNotEmpty()) {
            val asked = at.keys.toList()
            val repetitions = maxOf(1, limit / asked.size)
            val from = asked.map(at::getValue)
            val answer = try {
                exchange(from) { pdu(GET_BULK, it, 0, repetitions, from) }
            } catch (e: TooBig) {
                if (limit == 1) throw DeviceException(ErrorCategory.Api, "the SNMP agent cannot send even one row of ${asked.first()}")
                limit = maxOf(1, limit / 2)
                continue
            }
            var moved = false
            for ((i, column) in asked.withIndex()) {
                var r = 0
                while (r * asked.size + i < answer.size && column in at) {
                    val binding = answer[r * asked.size + i]
                    if (binding.value is SnmpValue.Missing || !binding.oid.under(column) || binding.oid <= at.getValue(column)) {
                        at.remove(column)
                    } else {
                        rows.getValue(column)[binding.oid.index(column)] = binding.value
                        at[column] = binding.oid
                        if (most != null && rows.getValue(column).size >= most) at.remove(column)
                    }
                    moved = true
                    r++
                }
                if (rows.getValue(column).size > MAX_ROWS) throw DeviceException(ErrorCategory.Api, "$column has more than $MAX_ROWS rows")
            }
            if (!moved) throw DeviceException(ErrorCategory.Api, "the SNMP agent answered GETBULK without a row")
        }
        return rows
    }

    fun close() = link.close()

    private fun id(): Int = next.also { next = if (next == Int.MAX_VALUE) 1 else next + 1 }

    private suspend fun exchange(oids: List<Oid>, writing: Boolean = false, build: (Int) -> ByteArray): List<Binding> = mutex.withLock {
        val id = id()
        val body = build(id)
        var left = tries
        var resent = false
        while (left > 0) {
            val security = user?.let { usm ?: Usm(it, discover()).also { found -> usm = found } }
            link.send(security?.request(body, id) ?: request(community, body))
            val answer = try {
                answer(id, security)
            } catch (e: Report) {
                if (resent || e.counter != notInTimeWindows && e.counter != unknownEngineIds) throw DeviceException(ErrorCategory.Api, "the SNMP agent reported ${e.counter}")
                if (e.counter == unknownEngineIds) usm = null
                resent = true
                continue
            }
            if (answer == null) {
                left--
                continue
            }
            if (answer.error == TOO_BIG) throw TooBig()
            if (answer.error != 0) throw SnmpError(answer.error, oids.getOrNull(answer.index - 1), oids)
            return answer.bindings
        }
        val silent = if (writing) "a community that may not write" else "a wrong community"
        throw DeviceException(ErrorCategory.ConnectionLost, "no SNMP answer in ${(tries * timeoutMillis).milliseconds}: $silent gets none either")
    }

    private suspend fun answer(id: Int, security: Usm?): Response? {
        val sent = TimeSource.Monotonic.markNow()
        while (true) {
            val left = timeoutMillis - sent.elapsedNow().inWholeMilliseconds
            if (left <= 0) return null
            val bytes = link.receive(left.toInt()) ?: return null
            val answer = try {
                if (security == null) response(bytes) else security.response(bytes).takeIf { it.first == id }?.second
            } catch (e: Report) {
                if (e.msgId == id) throw e
                null
            } catch (e: DeviceException) {
                if (e.category == ErrorCategory.PermissionDenied) throw e
                null
            }
            if (answer?.id == id) return answer
        }
    }

    private suspend fun discover(): Engine {
        repeat(tries) {
            val id = id()
            link.send(discovery(id))
            val sent = TimeSource.Monotonic.markNow()
            while (true) {
                val left = timeoutMillis - sent.elapsedNow().inWholeMilliseconds
                if (left <= 0) break
                val bytes = link.receive(left.toInt()) ?: break
                val engine = try {
                    discovered(bytes, id)
                } catch (e: DeviceException) {
                    null
                }
                if (engine != null) return engine
            }
        }
        throw DeviceException(ErrorCategory.ConnectionLost, "no SNMPv3 answer in ${(tries * timeoutMillis).milliseconds}: the agent did not name its engine")
    }

    private class TooBig : Exception()

    private companion object {
        // about 1400 bytes of answer for the columns of ifTable, under the MTU of Ethernet
        const val MAX_BINDINGS = 48
        const val MAX_ROWS = 100_000
    }
}

// the error status of an answer; the object is the one the agent pointed at, or null when it pointed at none
class SnmpError(val status: Int, val oid: Oid?, oids: List<Oid>) : DeviceException(
    ErrorCategory.Api,
    "the SNMP agent answered ${statuses.getOrNull(status)?.first ?: "error $status"} for " +
        (oid?.let { "${SnmpMib.standard.describe(it)} ($it)" } ?: oids.joinToString()) + (statuses.getOrNull(status)?.second?.let { ": $it" } ?: ""),
) {
    companion object {
        const val WRONG_VALUE = 10
        const val INCONSISTENT_VALUE = 12
    }
}

private val statuses = listOf(
    "noError" to null,
    "tooBig" to "the answer does not fit one message",
    "noSuchName" to "the agent has no such object or may not change it",
    "badValue" to "the agent does not take this value",
    "readOnly" to "the object is read-only",
    "genErr" to "the agent failed to apply the change",
    "noAccess" to "the community or user may not write this object",
    "wrongType" to "the value has the wrong type for the object",
    "wrongLength" to "the value has the wrong length for the object",
    "wrongEncoding" to "the value is encoded wrongly",
    "wrongValue" to "the agent does not take this value",
    "noCreation" to "the agent cannot create this object",
    "inconsistentValue" to "the value does not fit the other values of the row or the state of the agent",
    "resourceUnavailable" to "the agent has no resources for the change",
    "commitFailed" to "the agent failed to apply the change and undid it",
    "undoFailed" to "the agent failed to apply the change and could not undo all of it",
    "authorizationError" to "the community or user may not do this",
    "notWritable" to "the object cannot be written",
    "inconsistentName" to "the agent cannot create a row with this index now",
)
