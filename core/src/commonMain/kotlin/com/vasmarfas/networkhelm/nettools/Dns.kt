package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.UdpLink
import com.vasmarfas.networkhelm.transport.closingAfter
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.transport.openUdp
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class DnsRecord(val name: String, val type: Int, val ttl: Long, val data: String) {
    val typeName: String get() = DnsTypes.name(type)
}

class DnsResponse(
    val id: Int,
    val rcode: Int,
    val truncated: Boolean,
    val answers: List<DnsRecord>,
    val authority: List<DnsRecord>,
) {
    val rcodeName: String get() = DnsTypes.rcodeName(rcode)
}

object DnsTypes {
    val byName: Map<String, Int> = mapOf(
        "A" to 1, "NS" to 2, "CNAME" to 5, "SOA" to 6, "PTR" to 12, "MX" to 15, "TXT" to 16, "AAAA" to 28, "SRV" to 33, "CAA" to 257,
    )
    private val byType: Map<Int, String> = byName.entries.associate { it.value to it.key }

    val queryable: List<String> = listOf("A", "AAAA", "MX", "NS", "TXT", "CNAME", "PTR", "SOA", "SRV", "CAA")

    fun name(type: Int): String = byType[type] ?: "TYPE$type"

    fun rcodeName(rcode: Int): String = when (rcode) {
        0 -> "NOERROR"
        1 -> "FORMERR"
        2 -> "SERVFAIL"
        3 -> "NXDOMAIN"
        4 -> "NOTIMP"
        5 -> "REFUSED"
        else -> "RCODE$rcode"
    }
}

object DnsMessage {
    private const val OPT = 41

    fun buildQuery(name: String, type: Int, id: Int): ByteArray {
        val out = ArrayList<Byte>(64)
        fun u16(v: Int) {
            out.add((v shr 8).toByte())
            out.add(v.toByte())
        }
        u16(id)
        u16(0x0120)
        u16(1)
        u16(0)
        u16(0)
        u16(1)
        name.trimEnd('.').split('.').filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.encodeToByteArray()
            out.add(bytes.size.toByte())
            bytes.forEach { out.add(it) }
        }
        out.add(0)
        u16(type)
        u16(1)
        // EDNS0 record: a resolver may answer up to 1232 bytes over UDP before it sets TC
        out.add(0)
        u16(OPT)
        u16(1232)
        u16(0)
        u16(0)
        u16(0)
        return out.toByteArray()
    }

    fun parse(bytes: ByteArray): DnsResponse {
        var pos = 0
        fun u8(): Int = bytes[pos++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Long = (u16().toLong() shl 16) or u16().toLong()
        fun at16(offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

        fun readName(start: Int = -1): String {
            val labels = mutableListOf<String>()
            var p = if (start >= 0) start else pos
            var jumped = false
            var guard = 0
            while (guard++ < 128) {
                val len = bytes[p].toInt() and 0xFF
                if (len == 0) {
                    p++
                    break
                }
                if (len and 0xC0 == 0xC0) {
                    val pointer = ((len and 0x3F) shl 8) or (bytes[p + 1].toInt() and 0xFF)
                    if (!jumped && start < 0) pos = p + 2
                    jumped = true
                    p = pointer
                    continue
                }
                labels.add(bytes.decodeToString(p + 1, p + 1 + len))
                p += 1 + len
            }
            if (!jumped && start < 0) pos = p
            return if (labels.isEmpty()) "." else labels.joinToString(".")
        }

        fun readRecord(): DnsRecord {
            val name = readName()
            val type = u16()
            u16()
            val ttl = u32()
            val length = u16()
            val start = pos
            val data = when (type) {
                1 -> if (length == 4) (0 until 4).joinToString(".") { (bytes[start + it].toInt() and 0xFF).toString() } else hex(bytes, start, length)
                28 -> if (length == 16) Ipv6Address(IntArray(8) { at16(start + it * 2) }).compressed() else hex(bytes, start, length)
                2, 5, 12 -> readName(start)
                15 -> "${at16(start)} ${readName(start + 2)}"
                16 -> {
                    var p = start
                    val parts = mutableListOf<String>()
                    while (p < start + length) {
                        val l = bytes[p].toInt() and 0xFF
                        parts.add(bytes.decodeToString(p + 1, p + 1 + l))
                        p += 1 + l
                    }
                    parts.joinToString("")
                }
                6 -> {
                    val mnameLength = nameLength(bytes, start)
                    val rnameLength = nameLength(bytes, start + mnameLength)
                    var p = start + mnameLength + rnameLength
                    val numbers = List(5) {
                        val value = (at16(p).toLong() shl 16) or at16(p + 2).toLong()
                        p += 4
                        value
                    }
                    "${readName(start)} ${readName(start + mnameLength)} ${numbers.joinToString(" ")}"
                }
                33 -> "${at16(start)} ${at16(start + 2)} ${at16(start + 4)} ${readName(start + 6)}"
                257 -> {
                    val flags = bytes[start].toInt() and 0xFF
                    val tagLength = bytes[start + 1].toInt() and 0xFF
                    val tag = bytes.decodeToString(start + 2, start + 2 + tagLength)
                    "$flags $tag ${bytes.decodeToString(start + 2 + tagLength, start + length)}"
                }
                else -> hex(bytes, start, length)
            }
            pos = start + length
            return DnsRecord(name, type, ttl, data)
        }

        val id = u16()
        val flags = u16()
        val questions = u16()
        val answers = u16()
        val authority = u16()
        u16()
        repeat(questions) {
            readName()
            u16()
            u16()
        }
        return DnsResponse(
            id = id,
            rcode = flags and 0xF,
            truncated = flags and 0x0200 != 0,
            answers = List(answers) { readRecord() },
            authority = List(authority) { readRecord() },
        )
    }

    private fun nameLength(bytes: ByteArray, start: Int): Int {
        var p = start
        while (true) {
            val len = bytes[p].toInt() and 0xFF
            if (len == 0) return p - start + 1
            if (len and 0xC0 == 0xC0) return p - start + 2
            p += 1 + len
        }
    }

    private fun hex(bytes: ByteArray, start: Int, length: Int): String =
        (start until start + length).joinToString("") { (bytes[it].toInt() and 0xFF).toString(16).padStart(2, '0') }
}

val PublicResolvers = listOf(
    "Cloudflare" to "1.1.1.1",
    "Google" to "8.8.8.8",
    "Quad9" to "9.9.9.9",
    "Yandex" to "77.88.8.8",
)

// null when the resolver did not answer over UDP; an answer with TC set is asked again over TCP
suspend fun dnsQuery(
    server: String,
    name: String,
    type: Int,
    timeoutMillis: Int = 3_000,
    udp: suspend (String, Int) -> UdpLink = ::openUdp,
    tcp: suspend (String, Int, Int) -> ByteStream = ::openTcp,
): DnsResponse? {
    val id = Random.nextInt(0x10000)
    val query = DnsMessage.buildQuery(name, type, id)
    val link = udp(server, 53)
    val answer = try {
        (1..2).firstNotNullOfOrNull {
            link.send(query)
            link.receive(timeoutMillis)?.let(DnsMessage::parse)?.takeIf { reply -> reply.id == id }
        }
    } finally {
        link.close()
    }
    if (answer == null || !answer.truncated) return answer
    return tcp(server, 53, timeoutMillis).closingAfter(timeoutMillis.milliseconds) { stream ->
        stream.write(byteArrayOf((query.size shr 8).toByte(), query.size.toByte()) + query)
        val head = stream.readExactly(2)
        DnsMessage.parse(stream.readExactly(((head[0].toInt() and 0xFF) shl 8) or (head[1].toInt() and 0xFF)))
    }
}

private suspend fun ByteStream.readExactly(count: Int): ByteArray {
    val out = ByteArray(count)
    var at = 0
    while (at < count) {
        val read = read(out, at, count - at)
        if (read < 0) error("the stream ended after $at of $count bytes")
        at += read
    }
    return out
}
