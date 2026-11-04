package com.vasmarfas.networkhelm.ssh

import com.vasmarfas.networkhelm.transport.openTcp
import kotlinx.coroutines.CancellationException

private const val PROBE_MILLIS = 3_000

class SshService(val port: Int?, val disabled: Boolean)

suspend fun sshReach(host: String, service: SshService?, port: Int): SshReach {
    if (service?.disabled == true) return SshReach.Off
    val at = if (port == 22) service?.port ?: 22 else port
    return try {
        openTcp(host, at, PROBE_MILLIS).close()
        SshReach.Open
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SshReach.Closed(at)
    }
}
