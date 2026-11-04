package com.vasmarfas.networkhelm.discovery

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
import kotlinx.serialization.Serializable

val ScanPorts = listOf(22, 80, 443, 8291, 8728, 8729)

@Serializable
class Found(val address: String, val ports: List<Int>)

fun hosts(network: String): List<String>? {
    val parts = network.trim().split('/')
    if (parts.size != 2) return null
    val prefix = parts[1].toIntOrNull()?.takeIf { it in 22..32 } ?: return null
    val bytes = parts[0].split('.').map { it.toIntOrNull()?.takeIf { byte -> byte in 0..255 } ?: return null }
    if (bytes.size != 4) return null
    val mask = 0xFFFFFFFFL shl (32 - prefix) and 0xFFFFFFFFL
    val start = bytes.fold(0L) { acc, byte -> acc shl 8 or byte.toLong() } and mask
    val count = 1L shl (32 - prefix)
    val range = if (prefix >= 31) start until start + count else start + 1 until start + count - 1
    return range.map { "${it shr 24 and 255}.${it shr 16 and 255}.${it shr 8 and 255}.${it and 255}" }
}

fun scan(
    addresses: List<String>,
    ports: List<Int> = ScanPorts,
    parallel: Int = 32,
    timeoutMillis: Int = 700,
    open: suspend (String, Int, Int) -> ByteStream = ::openTcp,
): Flow<Found> = channelFlow {
    val permits = Semaphore(parallel)
    for (address in addresses) {
        launch {
            val answered = ports.filter { port -> permits.withPermit { answers(open, address, port, timeoutMillis) } }
            if (answered.isNotEmpty()) send(Found(address, answered))
        }
    }
}

private suspend fun answers(open: suspend (String, Int, Int) -> ByteStream, address: String, port: Int, timeoutMillis: Int): Boolean = try {
    open(address, port, timeoutMillis).close()
    true
} catch (e: CancellationException) {
    throw e
} catch (e: DeviceException) {
    if (e.category == ErrorCategory.Unsupported) throw e
    false
} catch (e: Exception) {
    false
}
