package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.openTcp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class PortResult(val port: Int, val open: Boolean)

class PortEntry(val port: Int, val protocol: String, val service: String) {
    // the query comes trimmed; a number matches from its first digit
    fun matches(query: String, description: String): Boolean =
        query.isEmpty() || port.toString().startsWith(query) || listOf(service, protocol, description).any { it.contains(query, ignoreCase = true) }
}

// "22, 80,443 8000-8100": duplicates go, the order of the first mention stays
fun parsePorts(spec: String): List<Int> {
    val out = LinkedHashSet<Int>()
    spec.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }.forEach { part ->
        if (part.contains('-')) {
            val from = part.substringBefore('-').toIntOrNull()?.coerceAtLeast(1)
            val to = part.substringAfter('-').toIntOrNull()?.coerceAtMost(65535)
            if (from != null && to != null && from <= to) out.addAll(from..to)
        } else {
            part.toIntOrNull()?.takeIf { it in 1..65535 }?.let { out += it }
        }
    }
    return out.toList()
}

fun scanPorts(
    host: String,
    ports: List<Int>,
    parallel: Int = 64,
    timeoutMillis: Int = 800,
    open: suspend (String, Int, Int) -> ByteStream = ::openTcp,
): Flow<PortResult> = channelFlow {
    val permits = Semaphore(parallel)
    for (port in ports) {
        launch { send(PortResult(port, permits.withPermit { connects(open, host, port, timeoutMillis) })) }
    }
}

private suspend fun connects(open: suspend (String, Int, Int) -> ByteStream, host: String, port: Int, timeoutMillis: Int): Boolean = try {
    open(host, port, timeoutMillis).close()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: DeviceException) {
    if (e.category == ErrorCategory.Unsupported) throw e
    false
} catch (e: Exception) {
    false
}

object WellKnownPorts {
    val top: List<Int> = listOf(
        21, 22, 23, 25, 53, 80, 81, 88, 110, 111, 135, 139, 143, 161, 389, 443, 445, 465, 515, 548, 554, 587, 631, 636, 873, 902, 993, 995,
        1080, 1433, 1521, 1723, 1883, 2049, 2181, 2375, 3000, 3128, 3306, 3389, 4443, 5000, 5001, 5060, 5201, 5222, 5432, 5900, 6379, 6443,
        8000, 8006, 8008, 8080, 8081, 8083, 8088, 8123, 8291, 8443, 8728, 8729, 8883, 9000, 9090, 9091, 9100, 9200, 10000, 10050, 10051, 11211, 27017, 32400,
    )

    val all: List<PortEntry> = listOf(
        PortEntry(20, "TCP", "ftp-data"),
        PortEntry(21, "TCP", "ftp"),
        PortEntry(22, "TCP", "ssh"),
        PortEntry(23, "TCP", "telnet"),
        PortEntry(25, "TCP", "smtp"),
        PortEntry(53, "TCP/UDP", "dns"),
        PortEntry(67, "UDP", "dhcp-server"),
        PortEntry(68, "UDP", "dhcp-client"),
        PortEntry(69, "UDP", "tftp"),
        PortEntry(80, "TCP", "http"),
        PortEntry(81, "TCP", "http"),
        PortEntry(88, "TCP/UDP", "kerberos"),
        PortEntry(110, "TCP", "pop3"),
        PortEntry(111, "TCP/UDP", "rpcbind"),
        PortEntry(119, "TCP", "nntp"),
        PortEntry(123, "UDP", "ntp"),
        PortEntry(135, "TCP", "msrpc"),
        PortEntry(137, "UDP", "netbios-ns"),
        PortEntry(138, "UDP", "netbios-dgm"),
        PortEntry(139, "TCP", "netbios-ssn"),
        PortEntry(143, "TCP", "imap"),
        PortEntry(161, "UDP", "snmp"),
        PortEntry(162, "UDP", "snmp-trap"),
        PortEntry(179, "TCP", "bgp"),
        PortEntry(194, "TCP", "irc"),
        PortEntry(389, "TCP/UDP", "ldap"),
        PortEntry(443, "TCP", "https"),
        PortEntry(445, "TCP", "smb"),
        PortEntry(465, "TCP", "smtps"),
        PortEntry(500, "UDP", "ike"),
        PortEntry(514, "UDP", "syslog"),
        PortEntry(515, "TCP", "lpd"),
        PortEntry(520, "UDP", "rip"),
        PortEntry(546, "UDP", "dhcpv6-client"),
        PortEntry(547, "UDP", "dhcpv6-server"),
        PortEntry(548, "TCP", "afp"),
        PortEntry(554, "TCP/UDP", "rtsp"),
        PortEntry(587, "TCP", "submission"),
        PortEntry(631, "TCP/UDP", "ipp"),
        PortEntry(636, "TCP", "ldaps"),
        PortEntry(853, "TCP", "dns-over-tls"),
        PortEntry(873, "TCP", "rsync"),
        PortEntry(902, "TCP", "vmware"),
        PortEntry(993, "TCP", "imaps"),
        PortEntry(995, "TCP", "pop3s"),
        PortEntry(1080, "TCP", "socks"),
        PortEntry(1194, "UDP", "openvpn"),
        PortEntry(1433, "TCP", "mssql"),
        PortEntry(1521, "TCP", "oracle"),
        PortEntry(1701, "UDP", "l2tp"),
        PortEntry(1723, "TCP", "pptp"),
        PortEntry(1812, "UDP", "radius"),
        PortEntry(1813, "UDP", "radius-acct"),
        PortEntry(1883, "TCP", "mqtt"),
        PortEntry(1900, "UDP", "ssdp"),
        PortEntry(2049, "TCP/UDP", "nfs"),
        PortEntry(2181, "TCP", "zookeeper"),
        PortEntry(2375, "TCP", "docker"),
        PortEntry(2376, "TCP", "docker-tls"),
        PortEntry(3000, "TCP", "http-dev"),
        PortEntry(3128, "TCP", "squid"),
        PortEntry(3306, "TCP", "mysql"),
        PortEntry(3389, "TCP", "rdp"),
        PortEntry(3478, "UDP", "stun-turn"),
        PortEntry(4443, "TCP", "https-alt"),
        PortEntry(4500, "UDP", "ipsec-nat-t"),
        PortEntry(5000, "TCP", "http-dev"),
        PortEntry(5001, "TCP", "synology"),
        PortEntry(5060, "TCP/UDP", "sip"),
        PortEntry(5061, "TCP", "sips"),
        PortEntry(5201, "TCP/UDP", "iperf3"),
        PortEntry(5222, "TCP", "xmpp"),
        PortEntry(5353, "UDP", "mdns"),
        PortEntry(5432, "TCP", "postgresql"),
        PortEntry(5683, "UDP", "coap"),
        PortEntry(5900, "TCP", "vnc"),
        PortEntry(5938, "TCP", "teamviewer"),
        PortEntry(6379, "TCP", "redis"),
        PortEntry(6443, "TCP", "kubernetes"),
        PortEntry(6881, "TCP/UDP", "bittorrent"),
        PortEntry(7547, "TCP", "tr-069"),
        PortEntry(8000, "TCP", "http-alt"),
        PortEntry(8006, "TCP", "pve"),
        PortEntry(8008, "TCP", "http-alt"),
        PortEntry(8080, "TCP", "http-alt"),
        PortEntry(8081, "TCP", "http-alt"),
        PortEntry(8083, "TCP", "vesta"),
        PortEntry(8088, "TCP", "asterisk"),
        PortEntry(8123, "TCP", "home-assistant"),
        PortEntry(8291, "TCP", "winbox"),
        PortEntry(8443, "TCP", "https-alt"),
        PortEntry(8728, "TCP", "routeros-api"),
        PortEntry(8729, "TCP", "routeros-api-ssl"),
        PortEntry(8883, "TCP", "mqtts"),
        PortEntry(9000, "TCP", "portainer"),
        PortEntry(9090, "TCP", "cockpit"),
        PortEntry(9091, "TCP", "transmission"),
        PortEntry(9100, "TCP", "jetdirect"),
        PortEntry(9200, "TCP", "elasticsearch"),
        PortEntry(9418, "TCP", "git"),
        PortEntry(10000, "TCP", "webmin"),
        PortEntry(10050, "TCP", "zabbix-agent"),
        PortEntry(10051, "TCP", "zabbix"),
        PortEntry(11211, "TCP", "memcached"),
        PortEntry(25565, "TCP", "minecraft"),
        PortEntry(27017, "TCP", "mongodb"),
        PortEntry(32400, "TCP", "plex"),
        PortEntry(51820, "UDP", "wireguard"),
        PortEntry(62078, "TCP", "ios-sync"),
    )

    private val byPort: Map<Int, PortEntry> = all.associateBy { it.port }

    fun service(port: Int): String? = byPort[port]?.service
}
