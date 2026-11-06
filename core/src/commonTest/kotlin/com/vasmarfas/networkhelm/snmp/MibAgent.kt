package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.transport.UdpLink
import kotlinx.coroutines.delay

// an SNMPv2c agent over a map of values: GET, GETBULK, and SET with the RowStatus of RFC 2579 for the columns named in statuses
internal class MibAgent(private val latencyMillis: Long = 0) : UdpLink {
    val values = mutableMapOf<Oid, SnmpValue>()
    val sets = mutableListOf<List<Pair<Oid, SnmpValue>>>()
    val statuses = mutableSetOf<Oid>()
    val required = mutableSetOf<Oid>()
    val refused = mutableMapOf<Oid, Int>()
    var stepsOnly = false
    // from this OID on the agent answers GETBULK with its first OID again
    var backwardsAfter: Oid? = null
    var requests = 0
    private val replies = ArrayDeque<ByteArray>()

    operator fun set(name: String, value: SnmpValue) {
        values[oid(name)] = value
    }

    override suspend fun send(datagram: ByteArray) {
        requests++
        val (_, community, pdu) = tlv(datagram, 0, datagram.size).children()
        val (id, _, repetitions, list) = pdu.children()
        val asked = list.children().map { binding -> binding.children().let { (name, value) -> name.oid() to decoded(value) } }
        val (error, index, bindings) = when (pdu.tag) {
            GET -> Triple(0, 0, asked.map { (name) -> name to (values[name] ?: SnmpValue.Missing(0x80)) })
            SET_PDU -> written(asked)
            else -> Triple(0, 0, bulk(asked.map { it.first }, repetitions.signed().toInt()))
        }
        val body = id.whole() + integer(error.toLong()) + integer(index.toLong()) +
            encoded(SEQUENCE, bindings.fold(ByteArray(0)) { all, (name, value) -> all + encoded(SEQUENCE, encoded(name) + bytes(value)) })
        replies += encoded(SEQUENCE, integer(1) + community.whole() + encoded(RESPONSE_PDU, body))
    }

    override suspend fun receive(timeoutMillis: Int): ByteArray? {
        if (latencyMillis > 0) delay(latencyMillis)
        return replies.removeFirstOrNull()
    }

    override fun close() {}

    private fun bulk(names: List<Oid>, repetitions: Int): List<Pair<Oid, SnmpValue>> {
        val sorted = values.keys.sorted()
        return (0 until repetitions).flatMap { r ->
            names.map { name ->
                val back = backwardsAfter
                val next = sorted.filter { it > name }.getOrNull(r)?.let { if (back != null && it >= back) sorted.first() else it }
                if (next == null) name to SnmpValue.Missing(0x82) else next to values.getValue(next)
            }
        }.take(48)
    }

    private fun written(asked: List<Pair<Oid, SnmpValue>>): Triple<Int, Int, List<Pair<Oid, SnmpValue>>> {
        sets.add(asked)
        fun failed(status: Int, at: Int) = Triple(status, at + 1, asked)
        asked.forEachIndexed { at, (name, _) -> refused[name]?.let { return failed(it, at) } }
        val changed = values.toMutableMap()
        val activated = mutableListOf<Pair<Oid, Int>>()
        asked.forEachIndexed { at, (name, value) ->
            val status = statuses.firstOrNull { name.under(it) }
            if (status == null) {
                changed[name] = value
                return@forEachIndexed
            }
            val row = name.index(status)
            val exists = values.containsKey(name)
            when ((value as SnmpValue.Number).value) {
                4L -> {
                    if (exists || stepsOnly) return failed(INCONSISTENT, at)
                    activated.add(name to at)
                }
                5L -> {
                    if (exists) return failed(INCONSISTENT, at)
                    changed[name] = SnmpValue.Number(INTEGER, 3)
                }
                1L -> {
                    if (!exists) return failed(INCONSISTENT, at)
                    activated.add(name to at)
                }
                6L -> changed.keys.removeAll { key -> key.arcs.size == status.arcs.size + row.size && key.under(Oid(status.arcs.dropLast(1))) && key.arcs.takeLast(row.size) == row }
                else -> return failed(WRONG_VALUE, at)
            }
        }
        for ((name, at) in activated) {
            val status = statuses.first { name.under(it) }
            val row = name.index(status)
            if (required.any { (it + row) !in changed }) return failed(INCONSISTENT, at)
            changed[name] = SnmpValue.Number(INTEGER, 1)
        }
        values.clear()
        values.putAll(changed)
        return Triple(0, 0, asked)
    }

    private fun bytes(value: SnmpValue): ByteArray = when (value) {
        is SnmpValue.Missing -> encoded(value.tag, ByteArray(0))
        else -> encoded(value)
    }

    private companion object {
        const val SET_PDU = 0xA3
        const val RESPONSE_PDU = 0xA2
        const val WRONG_VALUE = 10
        const val INCONSISTENT = 12
    }
}
