package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.CancellationException
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

// the time to open and close a connection; null where nothing listens on the port or nothing answers
suspend fun tcpConnectMillis(
    host: String,
    port: Int,
    timeoutMillis: Int,
    open: suspend (String, Int, Int) -> ByteStream,
): Double? {
    val started = TimeSource.Monotonic.markNow()
    return try {
        open(host, port, timeoutMillis).close()
        started.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
    } catch (e: CancellationException) {
        throw e
    } catch (e: DeviceException) {
        if (e.category == ErrorCategory.Unsupported) throw e
        null
    } catch (e: Exception) {
        null
    }
}
