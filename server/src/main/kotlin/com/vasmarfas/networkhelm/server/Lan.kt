package com.vasmarfas.networkhelm.server

import java.net.InetAddress
import java.net.UnknownHostException

// 100.64.0.0/10 is the shared space of carrier NAT that Tailscale and other VPNs use
// behind a reverse proxy on this computer every client looks local
fun isLocal(address: String): Boolean {
    val parsed = try {
        InetAddress.getByName(address.removePrefix("[").removeSuffix("]"))
    } catch (e: UnknownHostException) {
        return false
    }
    val bytes = parsed.address
    return parsed.isLoopbackAddress || parsed.isSiteLocalAddress || parsed.isLinkLocalAddress ||
        (bytes.size == 16 && bytes[0].toInt() and 0xfe == 0xfc) ||
        (bytes.size == 4 && bytes[0].toInt() and 0xff == 100 && bytes[1].toInt() and 0xc0 == 64)
}
