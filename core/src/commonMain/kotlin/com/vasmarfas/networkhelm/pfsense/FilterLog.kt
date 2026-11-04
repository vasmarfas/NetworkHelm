package com.vasmarfas.networkhelm.pfsense

import com.vasmarfas.networkhelm.model.hostPort

// where the protocol, length, source, destination and the protocol-specific fields stand, by IP version
private val places = mapOf("4" to listOf(16, 17, 18, 19, 20), "6" to listOf(12, 14, 15, 16, 17))

internal fun filterLog(message: String): Map<String, String>? {
    val fields = message.split(',')
    val place = places[fields.getOrNull(8)] ?: return null
    if (fields.size < place.last()) return null
    val (protocol, length, source, destination) = place.take(4).map(fields::get)
    val extra = fields.drop(place.last())
    val ported = protocol == "tcp" || protocol == "udp"
    return buildMap {
        put("tracker", fields[3])
        put("interface", fields[4])
        put("reason", fields[5])
        put("action", fields[6])
        put("direction", fields[7])
        put("protocol", protocol)
        put("length", length)
        put("source", hostPort(source, extra.getOrNull(0)?.takeIf { ported }))
        put("destination", hostPort(destination, extra.getOrNull(1)?.takeIf { ported }))
        when (protocol) {
            "tcp" -> extra.getOrNull(3)?.let { put("flags", it) }
            "icmp", "ipv6-icmp" -> extra.getOrNull(0)?.let { put("flags", it) }
        }
    }
}
