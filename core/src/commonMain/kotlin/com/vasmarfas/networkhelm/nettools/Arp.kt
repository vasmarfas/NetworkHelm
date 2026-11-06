package com.vasmarfas.networkhelm.nettools

private val arpEntry = Regex("""(\d{1,3}(?:\.\d{1,3}){3}).*?\b((?:[0-9a-f]{1,2}[:-]){5}[0-9a-f]{1,2})\b""", RegexOption.IGNORE_CASE)

// the neighbour table as it is printed by "arp -a" on Windows, "arp -an" on macOS, /proc/net/arp on Linux and Android, "ip neigh":
// an IPv4 address and the first MAC after it on the same line; an entry that was never answered has no MAC or only zeros
fun parseArp(text: String): Map<String, String> = buildMap {
    for (line in text.lineSequence()) {
        val match = arpEntry.find(line) ?: continue
        val mac = MacAddress.normalize(match.groupValues[2])?.takeIf { it != "000000000000" && it != "FFFFFFFFFFFF" } ?: continue
        put(match.groupValues[1], MacAddress.colon(mac))
    }
}
