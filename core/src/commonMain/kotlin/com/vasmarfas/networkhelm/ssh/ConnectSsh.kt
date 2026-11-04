package com.vasmarfas.networkhelm.ssh

import com.vasmarfas.networkhelm.transport.TrustKind
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.rejected

// JSch on the desktop and Android, libssh2 on iOS; a browser has no SSH
expect suspend fun connectSsh(
    host: String,
    port: Int,
    user: String,
    auth: SshAuth,
    acceptHostKey: (HostKey) -> Boolean,
    timeoutMillis: Int = 10_000,
): SshSession

suspend fun connectSshTrusted(host: String, port: Int, user: String, auth: SshAuth, policy: TrustPolicy): SshSession {
    val known = policy.known(host, port, TrustKind.Ssh)
    return try {
        connectSsh(host, port, user, auth, acceptHostKey = { it.fingerprint == known })
    } catch (e: HostKeyRejectedException) {
        if (!policy.confirm(host, port, TrustKind.Ssh, e.key.fingerprint, known)) throw rejected(host, e.key.fingerprint)
        connectSsh(host, port, user, auth, acceptHostKey = { it.fingerprint == e.key.fingerprint })
    }
}
