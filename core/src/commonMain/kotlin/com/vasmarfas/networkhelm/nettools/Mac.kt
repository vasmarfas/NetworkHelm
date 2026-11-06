package com.vasmarfas.networkhelm.nettools

class MacBlock(val prefix: String, val vendor: String, val country: String?) {
    val type: String
        get() = when {
            prefix.length == 6 -> "MA-L"
            prefix.length == 7 -> "MA-M"
            prefix.startsWith("0050C2") || prefix.startsWith("40D855") -> "IAB"
            else -> "MA-S"
        }
    val first: String get() = prefix.padEnd(12, '0')
    val last: String get() = prefix.padEnd(12, 'F')
}

// one line per block: prefix of 6, 7 or 9 hex digits, organisation, country; the first line is "#<date of the registry>"
class MacRegistry(val date: String, private val blocks: Map<String, MacBlock>) {
    fun find(hex: String): MacBlock? = listOf(9, 7, 6).firstNotNullOfOrNull { if (hex.length >= it) blocks[hex.take(it)] else null }

    companion object {
        fun parse(text: String): MacRegistry {
            val lines = text.lines()
            val blocks = HashMap<String, MacBlock>(lines.size * 2)
            for (line in lines) {
                if (line.isEmpty() || line.startsWith('#')) continue
                val fields = line.split('\t')
                blocks[fields[0]] = MacBlock(fields[0], fields[1], fields.getOrNull(2))
            }
            return MacRegistry(lines.first().removePrefix("#"), blocks)
        }
    }
}

object MacAddress {
    private fun digits(input: String): String? {
        val groups = input.trim().split(Regex("[\\s:.-]+")).filter { it.isNotEmpty() }
        if (groups.any { g -> g.any { it.digitToIntOrNull(16) == null } }) return null
        // arp on macOS drops the leading zeros of a group: 3c:7:54:12:34:56
        val width = if (groups.any { it.length > 2 }) 4 else 2
        return groups.mapIndexed { i, g -> if (i < groups.lastIndex || groups.size * width == 12) g.padStart(width, '0') else g }
            .joinToString("").uppercase()
    }

    fun normalize(input: String): String? = digits(input)?.takeIf { it.length == 12 }

    fun prefix(input: String): String? = digits(input)?.takeIf { it.length in 6..12 }

    fun colon(hex: String) = hex.chunked(2).joinToString(":")

    fun isLocal(hex: String) = (hex.substring(0, 2).toInt(16) and 2) == 2

    // a group address belongs to the owner of its OUI with the group bit cleared
    fun individual(hex: String) = (hex.substring(0, 2).toInt(16) and 0xFE).toString(16).padStart(2, '0').uppercase() + hex.substring(2)
}

fun magicPacket(mac: String): ByteArray? {
    val hex = MacAddress.normalize(mac) ?: return null
    val address = hex.chunked(2).map { it.toInt(16).toByte() }
    return ByteArray(6 + 16 * 6) { i -> if (i < 6) 0xFF.toByte() else address[(i - 6) % 6] }
}
