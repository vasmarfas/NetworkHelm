package com.vasmarfas.networkhelm.nettools

class Ipv4Subnet(val address: Long, val prefix: Int) {
    val mask: Long get() = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    val wildcard: Long get() = mask.inv() and 0xFFFFFFFFL
    val network: Long get() = address and mask
    val broadcast: Long get() = network or wildcard
    val totalAddresses: Long get() = 1L shl (32 - prefix)
    val usableHosts: Long get() = when (prefix) {
        32 -> 1
        31 -> 2
        else -> totalAddresses - 2
    }
    val firstHost: Long get() = if (prefix >= 31) network else network + 1
    val lastHost: Long get() = if (prefix >= 31) broadcast else broadcast - 1
    val cidr: String get() = "${Ipv4.format(network)}/$prefix"
    val ipClass: Char get() {
        val first = (address shr 24).toInt()
        return when {
            first < 128 -> 'A'
            first < 192 -> 'B'
            first < 224 -> 'C'
            first < 240 -> 'D'
            else -> 'E'
        }
    }
}

object Ipv4 {
    private val dotted = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val lastOctet = Regex("""\d{1,3}""")

    fun parse(text: String): Long? {
        val m = dotted.find(text.trim()) ?: return null
        var result = 0L
        for (i in 1..4) {
            val octet = m.groupValues[i].toInt()
            if (octet !in 0..255) return null
            result = (result shl 8) or octet.toLong()
        }
        return result
    }

    fun format(value: Long): String =
        "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"

    fun binary(value: Long): String =
        (0..3).joinToString(".") { i -> ((value shr (24 - i * 8)) and 0xFF).toString(2).padStart(8, '0') }

    fun maskToPrefix(mask: Long): Int? {
        if (mask == 0L) return 0
        var prefix = 0
        var seenZero = false
        for (i in 31 downTo 0) {
            if ((mask shr i) and 1L == 1L) {
                if (seenZero) return null
                prefix++
            } else {
                seenZero = true
            }
        }
        return prefix
    }

    // "10.0.0.1/8", "10.0.0.1/255.0.0.0", "10.0.0.1 255.0.0.0", or a bare address with its classful prefix
    fun parseSubnet(text: String): Ipv4Subnet? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val slash = t.indexOf('/')
        if (slash >= 0) {
            val address = parse(t.substring(0, slash)) ?: return null
            val suffix = t.substring(slash + 1).trim()
            val prefix = suffix.toIntOrNull() ?: parse(suffix)?.let(::maskToPrefix) ?: return null
            if (prefix !in 0..32) return null
            return Ipv4Subnet(address, prefix)
        }
        val parts = t.split(Regex("\\s+"))
        if (parts.size == 2) {
            val address = parse(parts[0]) ?: return null
            val prefix = parse(parts[1])?.let(::maskToPrefix) ?: parts[1].toIntOrNull() ?: return null
            if (prefix !in 0..32) return null
            return Ipv4Subnet(address, prefix)
        }
        val address = parse(t) ?: return null
        val prefix = when ((address shr 24).toInt()) {
            in 0..127 -> 8
            in 128..191 -> 16
            in 192..223 -> 24
            else -> 32
        }
        return Ipv4Subnet(address, prefix)
    }

    // "10.0.0.1-10.0.0.9" or "10.0.0.1-9" (the last octet); the forms of parseSubnet give the whole network, but a bare address is that one host
    fun parseRange(text: String): Pair<Long, Long>? {
        val t = text.trim()
        val dash = t.indexOf('-')
        if (dash < 0) {
            parse(t)?.let { return it to it }
            return parseSubnet(t)?.let { it.network to it.broadcast }
        }
        val first = parse(t.substring(0, dash)) ?: return null
        val tail = t.substring(dash + 1).trim()
        val last = parse(tail) ?: lastOctet.matchEntire(tail)?.value?.toInt()?.takeIf { it <= 255 }?.let { (first and 0xFFFFFF00L) or it.toLong() } ?: return null
        return if (first <= last) first to last else null
    }

    // the fewest blocks that hold exactly first..last: each is the biggest aligned block that still fits
    fun cover(first: Long, last: Long): List<Ipv4Subnet> {
        val blocks = mutableListOf<Ipv4Subnet>()
        var at = first
        while (at <= last) {
            val aligned = if (at == 0L) 1L shl 32 else at.takeLowestOneBit()
            val size = minOf(aligned, (last - at + 1).takeHighestOneBit())
            blocks += Ipv4Subnet(at, 32 - size.countTrailingZeroBits())
            at += size
        }
        return blocks
    }

    // the smallest single network that holds both ends
    fun enclosing(first: Long, last: Long): Ipv4Subnet = Ipv4Subnet(first, (first xor last).toInt().countLeadingZeroBits())

    fun ptrName(value: Long): String =
        (0..3).joinToString(".") { i -> ((value shr (i * 8)) and 0xFF).toString() } + ".in-addr.arpa"
}

class Ipv6Address(val groups: IntArray) {
    init {
        require(groups.size == 8)
    }

    fun expanded(): String = groups.joinToString(":") { it.toString(16).padStart(4, '0') }

    fun ptrName(): String = expanded().replace(":", "").reversed().toCharArray().joinToString(".") + ".ip6.arpa"

    fun compressed(): String {
        var bestStart = -1
        var bestLen = 0
        var i = 0
        while (i < 8) {
            if (groups[i] == 0) {
                var j = i
                while (j < 8 && groups[j] == 0) j++
                if (j - i > bestLen) {
                    bestLen = j - i
                    bestStart = i
                }
                i = j
            } else {
                i++
            }
        }
        if (bestLen < 2) return groups.joinToString(":") { it.toString(16) }
        val head = groups.take(bestStart).joinToString(":") { it.toString(16) }
        val tail = groups.drop(bestStart + bestLen).joinToString(":") { it.toString(16) }
        return "$head::$tail"
    }

    fun withPrefix(prefix: Int): Ipv6Address = Ipv6Address(
        IntArray(8) { g ->
            val bits = (prefix - g * 16).coerceIn(0, 16)
            val mask = if (bits == 0) 0 else (0xFFFF shl (16 - bits)) and 0xFFFF
            groups[g] and mask
        },
    )

    fun lastInPrefix(prefix: Int): Ipv6Address = Ipv6Address(IntArray(8) { g -> groups[g] or (0xFFFF shr (prefix - g * 16).coerceIn(0, 16)) })

    companion object {
        fun parse(text: String): Ipv6Address? {
            var t = text.trim().lowercase()
            if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length - 1)
            if (t.isEmpty() || t.count { it == ':' } < 2) return null
            val doubleColon = t.indexOf("::")
            if (doubleColon >= 0 && t.indexOf("::", doubleColon + 1) >= 0) return null
            fun parseGroups(part: String): List<Int>? {
                if (part.isEmpty()) return emptyList()
                val items = part.split(":")
                val out = mutableListOf<Int>()
                for ((index, item) in items.withIndex()) {
                    if (item.contains('.') && index == items.lastIndex) {
                        val v4 = Ipv4.parse(item) ?: return null
                        out.add(((v4 shr 16) and 0xFFFF).toInt())
                        out.add((v4 and 0xFFFF).toInt())
                    } else {
                        if (item.isEmpty() || item.length > 4) return null
                        out.add(item.toIntOrNull(16) ?: return null)
                    }
                }
                return out
            }
            val groups: List<Int> = if (doubleColon >= 0) {
                val head = parseGroups(t.substring(0, doubleColon)) ?: return null
                val tail = parseGroups(t.substring(doubleColon + 2)) ?: return null
                if (head.size + tail.size > 7) return null
                head + List(8 - head.size - tail.size) { 0 } + tail
            } else {
                parseGroups(t)?.takeIf { it.size == 8 } ?: return null
            }
            return Ipv6Address(groups.toIntArray())
        }

        fun parseWithPrefix(text: String): Pair<Ipv6Address, Int>? {
            val t = text.trim()
            val slash = t.indexOf('/')
            val address = parse(if (slash >= 0) t.substring(0, slash) else t) ?: return null
            val prefix = if (slash >= 0) t.substring(slash + 1).trim().toIntOrNull() ?: return null else 64
            if (prefix !in 0..128) return null
            return address to prefix
        }
    }
}

fun reverseName(address: String): String? =
    Ipv4.parse(address)?.let(Ipv4::ptrName) ?: Ipv6Address.parse(address)?.ptrName()
