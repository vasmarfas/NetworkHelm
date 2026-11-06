package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.discovery.formatIpv6
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.duration

enum class MibKind { Group, Scalar, Table, Entry, Column }

class MibObject(
    val oid: Oid,
    val name: String,
    val module: String,
    val kind: MibKind,
    val type: String,
    val tc: String,
    val hint: String,
    val enums: Map<Long, String>,
    val access: String,
    val index: List<String>,
    val implied: Boolean,
    val units: String,
) {
    val writable: Boolean get() = access == "read-write" || access == "read-create" || access == "write-only"

    val readable: Boolean get() = access == "read-only" || access == "read-write" || access == "read-create"
}

// the objects of the standard MIBs, from core/schema/snmp-mibs.tsv that tools/snmp-mibs/build.py writes
class SnmpMib(text: String) {
    private val objects = mutableMapOf<Oid, MibObject>()
    private val names = mutableMapOf<String, MibObject>()
    private val vendors = mutableMapOf<Long, String>()

    init {
        for (line in text.lineSequence()) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val field = line.split('\t')
            if (field.size < 11) throw IllegalArgumentException("a line of the MIB dictionary with ${field.size} fields: $line")
            val at = oid(field[0])
            if (field[3] == "vendor") {
                vendors[at.arcs.last()] = field[1]
                continue
            }
            val index = field[9].split(',').filter { it.isNotEmpty() }
            val enums = field[7].split(',').filter { it.isNotEmpty() }.associate { item ->
                item.substringAfterLast('(').removeSuffix(")").toLong() to item.substringBeforeLast('(')
            }
            val kind = MibKind.entries.first { it.name.lowercase() == field[3] }
            val found = MibObject(at, field[1], field[2], kind, field[4], field[5], field[6], enums, field[8], index.map { it.removePrefix("*") }, index.lastOrNull()?.startsWith("*") == true, field[10])
            objects[at] = found
            names[found.name] = found
        }
    }

    val known: Collection<MibObject> get() = objects.values

    operator fun get(oid: Oid): MibObject? = objects[oid]

    fun named(name: String): MibObject? = names[name]

    // the deepest object the dictionary knows on the way to oid
    fun find(oid: Oid): MibObject? {
        for (size in oid.arcs.size downTo 1) objects[Oid(oid.arcs.subList(0, size))]?.let { return it }
        return null
    }

    fun columns(entry: MibObject): List<MibObject> =
        objects.values.filter { it.kind == MibKind.Column && it.oid.arcs.size == entry.oid.arcs.size + 1 && it.oid.under(entry.oid) }.sortedBy { it.oid }

    fun vendor(oid: Oid): String? = if (oid.arcs.size > ENTERPRISES.arcs.size && oid.under(ENTERPRISES)) vendors[oid.arcs[ENTERPRISES.arcs.size]] else null

    // as snmpwalk writes it: the name of the deepest known object and the arcs after it
    fun describe(oid: Oid): String {
        val known = find(oid) ?: return oid.toString()
        return (listOf(known.name) + oid.arcs.drop(known.oid.arcs.size).map(Long::toString)).joinToString(".")
    }

    fun oidOf(text: String): Oid? {
        val parts = text.trim().removePrefix(".").split('.')
        val head = parts.first().toLongOrNull()?.let { listOf(it) } ?: named(parts.first())?.oid?.arcs ?: return null
        val rest = parts.drop(1).map { it.toLongOrNull() ?: return null }
        return Oid(head + rest).takeIf { it.arcs.size >= 2 }
    }

    // how a person reads the value of the object: enumerations by name, time as a duration, addresses as such
    fun shown(column: MibObject, value: SnmpValue?): String = when (value) {
        is SnmpValue.Number -> when {
            value.tag == TIMETICKS -> duration(value.value / 100)
            column.tc == "TruthValue" -> when (value.value) {
                1L -> "true"
                2L -> "false"
                else -> "${value.value}"
            }
            else -> column.enums[value.value] ?: text(value)
        }
        is SnmpValue.Octets -> octets(column, value.bytes)
        is SnmpValue.Id -> describe(value.oid)
        else -> text(value)
    }

    private fun octets(column: MibObject, bytes: ByteArray): String = when {
        column.type == "Bits" -> (0 until bytes.size * 8).filter { bit -> bytes[bit / 8].toInt() and (0x80 shr bit % 8) != 0 }
            .joinToString(",") { column.enums[it.toLong()] ?: "$it" }
        column.tc == "DateAndTime" && (bytes.size == 8 || bytes.size == 11) -> date(bytes)
        column.tc.startsWith("InetAddress") && bytes.size == 4 -> bytes.joinToString(".") { "${it.toInt() and 0xFF}" }
        column.tc.startsWith("InetAddress") && bytes.size == 16 -> formatIpv6(bytes)
        hexadecimal(column) -> hex(bytes)
        else -> text(SnmpValue.Octets(bytes))
    }

    // the value a person typed, in the type of the object; what does not fit is refused before anything is sent
    fun written(column: MibObject, value: String?): SnmpValue {
        val typed = value.orEmpty().trim()
        fun refused(): Nothing = throw DeviceException(ErrorCategory.ArgumentValue, "$typed is no value of ${column.name}")
        return when (column.type) {
            "Integer32" -> SnmpValue.Number(INTEGER, integerOf(column, typed) ?: refused())
            "Unsigned32", "Gauge32" -> SnmpValue.Number(GAUGE32, typed.toLongOrNull()?.takeIf { it in 0..0xFFFF_FFFFL } ?: refused())
            "TimeTicks" -> SnmpValue.Number(TIMETICKS, ticks(typed) ?: refused())
            "IpAddress" -> SnmpValue.Address(ipv4(typed) ?: refused())
            "ObjectIdentifier" -> SnmpValue.Id(oidOf(typed) ?: refused())
            "Bits" -> SnmpValue.Octets(bits(column, typed) ?: refused())
            "OctetString" -> SnmpValue.Octets(octets(column, typed) ?: refused())
            else -> throw DeviceException(ErrorCategory.Unsupported, "${column.name} is a ${column.type}, which SNMP does not let a manager write")
        }
    }

    private fun integerOf(column: MibObject, typed: String): Long? {
        if (column.tc == "TruthValue") {
            when (typed.lowercase()) {
                "true", "yes", "1" -> return 1
                "false", "no", "2" -> return 2
            }
        }
        return column.enums.entries.firstOrNull { it.value == typed }?.key ?: typed.toLongOrNull()?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
    }

    private fun bits(column: MibObject, typed: String): ByteArray? {
        val set = typed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { name ->
            column.enums.entries.firstOrNull { it.value == name }?.key?.toInt() ?: name.toIntOrNull() ?: return null
        }
        val bytes = ByteArray(set.maxOrNull()?.let { it / 8 + 1 } ?: 0)
        for (bit in set) bytes[bit / 8] = (bytes[bit / 8].toInt() or (0x80 shr bit % 8)).toByte()
        return bytes
    }

    private fun octets(column: MibObject, typed: String): ByteArray? = when {
        column.tc == "DateAndTime" -> dateBytes(typed)
        column.tc.startsWith("InetAddress") && ipv4(typed) != null -> ipv4(typed)
        hexadecimal(column) || column.tc.startsWith("InetAddress") -> hexBytes(typed)
        // without a hint the value was shown as text when it reads as text, and in hex with colons when it does not
        column.hint.isEmpty() && HEX.matches(typed) -> hexBytes(typed)
        else -> typed.encodeToByteArray()
    }

    private fun hexadecimal(column: MibObject) =
        column.tc == "PhysAddress" || column.tc == "MacAddress" || 'x' in column.hint && 'a' !in column.hint && 't' !in column.hint

    companion object {
        val standard: SnmpMib by lazy { SnmpMib(snmpMibs) }

        val ENTERPRISES = oid("1.3.6.1.4.1")
    }
}

private val HEX = Regex("([0-9A-Fa-f]{2}:)+[0-9A-Fa-f]{2}")

private val SPAN = Regex("(\\d+)([wdhms])")

private fun ticks(typed: String): Long? {
    typed.toLongOrNull()?.let { return it.takeIf { ticks -> ticks in 0..0xFFFF_FFFFL } }
    if (typed.isEmpty() || SPAN.replace(typed, "").isNotEmpty()) return null
    val seconds = SPAN.findAll(typed).sumOf { match ->
        match.groupValues[1].toLong() * mapOf("w" to 604_800L, "d" to 86_400L, "h" to 3_600L, "m" to 60L, "s" to 1L).getValue(match.groupValues[2])
    }
    return (seconds * 100).takeIf { it <= 0xFFFF_FFFFL }
}

internal fun ipv4(typed: String): ByteArray? {
    val parts = typed.split('.').map { it.toIntOrNull()?.takeIf { part -> part in 0..255 } ?: return null }
    return if (parts.size == 4) ByteArray(4) { parts[it].toByte() } else null
}

internal fun hexBytes(typed: String): ByteArray? {
    val digits = typed.filter { it != ':' && it != '-' && it != ' ' }.removePrefix("0x")
    if (digits.length % 2 != 0 || digits.any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

private fun date(bytes: ByteArray): String {
    fun two(at: Int) = "${bytes[at].toInt() and 0xFF}".padStart(2, '0')
    val year = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    val zone = if (bytes.size == 11) " ${bytes[8].toInt().toChar()}${two(9)}:${two(10)}" else ""
    return "$year-${two(2)}-${two(3)} ${two(4)}:${two(5)}:${two(6)}$zone"
}

private val DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})(?: ([+-])(\\d{2}):(\\d{2}))?")

private fun dateBytes(typed: String): ByteArray? {
    val parts = DATE.matchEntire(typed)?.groupValues ?: return null
    val year = parts[1].toInt()
    val time = byteArrayOf((year shr 8).toByte(), year.toByte()) + (2..6).map { parts[it].toInt().toByte() } + 0.toByte()
    return if (parts[7].isEmpty()) time else time + byteArrayOf(parts[7][0].code.toByte(), parts[8].toInt().toByte(), parts[9].toInt().toByte())
}
