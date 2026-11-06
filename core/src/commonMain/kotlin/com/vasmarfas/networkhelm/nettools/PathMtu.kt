package com.vasmarfas.networkhelm.nettools

enum class MtuKind { Ethernet, Pppoe, Tunnel, Low }

fun mtuKind(mtu: Int): MtuKind = when {
    mtu >= 1500 -> MtuKind.Ethernet
    mtu >= 1492 -> MtuKind.Pppoe
    mtu >= 1400 -> MtuKind.Tunnel
    else -> MtuKind.Low
}

// the largest echo that crosses the path with the don't-fragment flag, plus 28 bytes of the IPv4 and ICMP headers;
// the search runs from 576 (what every host takes) to 1500 (Ethernet), null where even the smallest gets no answer
suspend fun pathMtu(ops: HostOps, address: String, timeoutMillis: Int = 2_000): Int? {
    // a router that says the packet is too big has answered for good, a silent path may only have lost one
    suspend fun passes(payload: Int): Boolean {
        repeat(2) {
            val reply = ops.ping(address, timeoutMillis, size = payload, dontFragment = true)
            if (reply.timeMillis != null) return true
            if (reply.fragmentationNeeded) return false
        }
        return false
    }
    var low = 548
    var high = 1472
    if (!passes(low)) return null
    if (passes(high)) return high + 28
    while (high - low > 1) {
        val middle = (low + high) / 2
        if (passes(middle)) low = middle else high = middle
    }
    return low + 28
}
