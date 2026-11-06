package com.vasmarfas.networkhelm.nettools

class Allocation(val need: Int, val subnet: Ipv4Subnet)

object Subnets {
    // as the calculator reads it: a bare address stands for its classful network
    fun span(text: String): Pair<Long, Long>? = Ipv4.parseSubnet(text)?.let { it.network to it.broadcast } ?: Ipv4.parseRange(text)

    fun network(text: String): Ipv4Subnet? = span(text)?.let { (first, last) -> Ipv4.cover(first, last).singleOrNull() }

    // the prefix that cuts a network into at least that many equal parts; null when the network has fewer addresses
    fun prefixFor(network: Ipv4Subnet, parts: Int): Int? {
        if (parts < 1) return null
        return (network.prefix + Int.SIZE_BITS - (parts - 1).countLeadingZeroBits()).takeIf { it <= 32 }
    }

    // the parts of a network that are one prefix long; none unless the prefix lies between the network's own and 32
    fun split(network: Ipv4Subnet, prefix: Int): Sequence<Ipv4Subnet> {
        if (prefix !in network.prefix..32) return emptySequence()
        val size = 1L shl (32 - prefix)
        return (0L until (1L shl (prefix - network.prefix))).asSequence().map { Ipv4Subnet(network.network + it * size, prefix) }
    }

    // the biggest need first, each block on a multiple of its own size so that a range which is not a network works too; the least block is a /30
    fun allocate(first: Long, last: Long, needs: List<Int>): List<Allocation>? {
        var at = first
        val plan = mutableListOf<Allocation>()
        for (need in needs.sortedDescending()) {
            var bits = 2
            while ((1L shl bits) - 2 < need) bits++
            val size = 1L shl bits
            at = (at + size - 1) / size * size
            if (at + size - 1 > last) return null
            plan += Allocation(need, Ipv4Subnet(at, 32 - bits))
            at += size
        }
        return plan
    }

    // entries that overlap or touch melt into one, then each is covered by the fewest blocks
    fun summarize(ranges: List<Pair<Long, Long>>): List<Ipv4Subnet> {
        val merged = mutableListOf<Pair<Long, Long>>()
        for (range in ranges.sortedBy { it.first }) {
            val previous = merged.lastOrNull()
            if (previous != null && range.first <= previous.second + 1) {
                merged[merged.lastIndex] = previous.first to maxOf(previous.second, range.second)
            } else {
                merged += range
            }
        }
        return merged.flatMap { (first, last) -> Ipv4.cover(first, last) }
    }

    // entries separated by commas or semicolons, or one to a line
    fun items(text: String): List<String> = text.split(Regex("[,;\r\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

    // whole numbers above zero separated by commas, semicolons or spaces
    fun hostCounts(text: String): List<Int>? =
        text.split(Regex("[,;\\s]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull()?.takeIf { count -> count > 0 } ?: return null }.takeIf { it.isNotEmpty() }
}
