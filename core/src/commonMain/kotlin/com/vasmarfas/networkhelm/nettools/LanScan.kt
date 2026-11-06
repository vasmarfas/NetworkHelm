package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.openTcp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

const val LAN_LIMIT = 4096

private const val CONNECT_TIMEOUT_MILLIS = 400

// the ports a phone, a PC, a NAS, a printer and a router tend to have open; 62078 is the sync service of an iPhone
val LanPorts: List<Int> = listOf(22, 53, 80, 139, 443, 445, 5000, 8080, 8291, 62078)

data class LanHost(val address: String, val timeMillis: Double?, val ports: List<Int>, val name: String? = null)

// one answer per address, null where nothing answered an echo or a connection; ping and reverse are null where the system gives none
fun scanLan(
    addresses: List<String>,
    ping: (suspend (String) -> PingReply)?,
    reverse: (suspend (String) -> String?)?,
    parallel: Int = 48,
    open: suspend (String, Int, Int) -> ByteStream = ::openTcp,
): Flow<LanHost?> = channelFlow {
    val permits = Semaphore(parallel)
    for (address in addresses) {
        launch {
            val found = permits.withPermit { probe(address, ping, open) }
            send(if (found != null && reverse != null) found.copy(name = reverse(address)) else found)
        }
    }
}

private suspend fun probe(address: String, ping: (suspend (String) -> PingReply)?, open: suspend (String, Int, Int) -> ByteStream): LanHost? = coroutineScope {
    val echo = async { ping?.invoke(address)?.timeMillis }
    val connects = LanPorts.map { port -> async { tcpConnectMillis(address, port, CONNECT_TIMEOUT_MILLIS, open)?.let { port to it } } }
    val answers = connects.awaitAll().filterNotNull()
    val time = (listOfNotNull(echo.await()) + answers.map { it.second }).minOrNull()
    time?.let { LanHost(address, it, answers.map { answer -> answer.first }) }
}

// the first and the last address to scan: a network leaves out its own address and its broadcast, which no device answers from
fun scanSpan(text: String): Pair<Long, Long>? {
    val span = Ipv4.parseRange(text) ?: return null
    val subnet = Ipv4.parseSubnet(text)
    return if (subnet != null && span.first == subnet.network && span.second == subnet.broadcast) subnet.firstHost to subnet.lastHost else span
}

// the network to start from: a private one, and 192.168 before 10 before 172.16, where Docker, WSL and Hyper-V put their bridges
fun defaultNetwork(networks: List<LocalNetwork>): LocalNetwork? = networks.minByOrNull { network -> Ipv4.parse(network.address)?.let(::privateRank) ?: 3 }

private fun privateRank(address: Long): Int = when {
    (address shr 16) == 0xC0A8L -> 0
    (address shr 24) == 10L -> 1
    (address shr 20) == 0xAC1L -> 2
    else -> 3
}

// the /24 around the address of the device, which is what a home network is; a larger one would not fit a scan
fun LocalNetwork.scanRange(): String? =
    Ipv4.parseSubnet("$address/${prefix.coerceIn(24, 30)}")?.let { "${Ipv4.format(it.network)}/${it.prefix}" }
