package com.vasmarfas.networkhelm.discovery

import com.vasmarfas.networkhelm.routeros.api.ApiEncoding
import com.vasmarfas.networkhelm.transport.Base64Bytes
import kotlinx.serialization.Serializable

@Serializable
class MndpAnnouncement(
    val mac: String?,
    val identity: String?,
    val version: String?,
    val platform: String?,
    val uptimeSeconds: Long?,
    val softwareId: String?,
    val board: String?,
    val interfaceName: String?,
    val ipv4: String?,
    val ipv6: String?,
    val other: Map<Int, @Serializable(with = Base64Bytes::class) ByteArray>,
)

@Serializable
class MndpSighting(val sourceAddress: String, val announcement: MndpAnnouncement)

fun parseMndp(packet: ByteArray): MndpAnnouncement? {
    // the header alone is the refresh request of a client such as WinBox, not an announcement
    if (packet.size <= 4) return null
    val fields = HashMap<Int, ByteArray>()
    var at = 4
    while (at + 4 <= packet.size) {
        val type = packet.u16(at)
        val length = packet.u16(at + 2)
        if (at + 4 + length > packet.size) return null
        fields[type] = packet.copyOfRange(at + 4, at + 4 + length)
        at += 4 + length
    }
    if (at != packet.size) return null
    fun text(type: Int) = fields[type]?.let(ApiEncoding.Utf8::decode)
    return MndpAnnouncement(
        mac = fields[MAC]?.takeIf { it.size == 6 }?.joinToString(":") { it.hex2().uppercase() },
        identity = text(IDENTITY),
        version = text(VERSION),
        platform = text(PLATFORM),
        // the only little-endian field of the packet
        uptimeSeconds = fields[UPTIME]?.takeIf { it.size == 4 }?.let { b -> (0 until 4).sumOf { (b[it].toLong() and 0xFF) shl (8 * it) } },
        softwareId = text(SOFTWARE_ID),
        board = text(BOARD),
        interfaceName = text(INTERFACE),
        ipv4 = fields[IPV4]?.takeIf { it.size == 4 }?.joinToString(".") { (it.toInt() and 0xFF).toString() },
        ipv6 = fields[IPV6]?.takeIf { it.size == 16 }?.let(::formatIpv6),
        other = fields.filterKeys { it !in KNOWN },
    )
}

private const val MAC = 1
private const val IDENTITY = 5
private const val VERSION = 7
private const val PLATFORM = 8
private const val UPTIME = 10
private const val SOFTWARE_ID = 11
private const val BOARD = 12
private const val UNPACK = 14
private const val IPV6 = 15
private const val INTERFACE = 16
private const val IPV4 = 17
private val KNOWN = setOf(MAC, IDENTITY, VERSION, PLATFORM, UPTIME, SOFTWARE_ID, BOARD, UNPACK, IPV6, INTERFACE, IPV4)

private fun ByteArray.u16(at: Int) = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

private fun Byte.hex2() = (toInt() and 0xFF).toString(16).padStart(2, '0')

fun formatIpv6(bytes: ByteArray): String {
    val groups = IntArray(8) { bytes.u16(it * 2) }
    var bestStart = -1
    var bestLength = 1
    var i = 0
    while (i < 8) {
        if (groups[i] != 0) {
            i++
            continue
        }
        val start = i
        while (i < 8 && groups[i] == 0) i++
        if (i - start > bestLength) {
            bestStart = start
            bestLength = i - start
        }
    }
    if (bestStart < 0) return groups.joinToString(":") { it.toString(16) }
    val head = groups.copyOfRange(0, bestStart).joinToString(":") { it.toString(16) }
    val tail = groups.copyOfRange(bestStart + bestLength, 8).joinToString(":") { it.toString(16) }
    return "$head::$tail"
}
