package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TrustKind { Tls, Ssh }

class TrustRequest(val host: String, val port: Int, val kind: TrustKind, val fingerprint: String, val previous: String?)

interface TrustStore {
    suspend fun get(host: String, port: Int, kind: TrustKind): String?

    suspend fun put(host: String, port: Int, kind: TrustKind, fingerprint: String)
}

class MemoryTrustStore : TrustStore {
    private val lock = Mutex()
    private val entries = HashMap<String, String>()

    override suspend fun get(host: String, port: Int, kind: TrustKind) = lock.withLock { entries[trustKey(host, port, kind)] }

    override suspend fun put(host: String, port: Int, kind: TrustKind, fingerprint: String) =
        lock.withLock { entries[trustKey(host, port, kind)] = fingerprint }
}

class TrustPolicy(private val store: TrustStore, private val ask: suspend (TrustRequest) -> Boolean) {

    suspend fun known(host: String, port: Int, kind: TrustKind): String? = store.get(host, port, kind)

    suspend fun confirm(host: String, port: Int, kind: TrustKind, fingerprint: String, previous: String?): Boolean {
        val accepted = ask(TrustRequest(host, port, kind, fingerprint, previous))
        if (accepted) store.put(host, port, kind, fingerprint)
        return accepted
    }

    fun through(leads: Map<Int, Pair<String, Int>>): TrustPolicy {
        fun real(host: String, port: Int) = leads[port]?.takeIf { host == "127.0.0.1" } ?: (host to port)
        val mapped = object : TrustStore {
            override suspend fun get(host: String, port: Int, kind: TrustKind): String? = real(host, port).let { (h, p) -> store.get(h, p, kind) }

            override suspend fun put(host: String, port: Int, kind: TrustKind, fingerprint: String) = real(host, port).let { (h, p) -> store.put(h, p, kind, fingerprint) }
        }
        return TrustPolicy(mapped) { request -> real(request.host, request.port).let { (h, p) -> ask(TrustRequest(h, p, request.kind, request.fingerprint, request.previous)) } }
    }
}

// the first connection only reads the certificate: the dialog may outlast the time the router waits for a login
suspend fun openTlsTrusted(host: String, port: Int, policy: TrustPolicy): TlsStream {
    val known = policy.known(host, port, TrustKind.Tls)
    val seen = try {
        if (known != null) return openTls(host, port, known)
        val probe = openTls(host, port, null)
        probe.close()
        probe.certificateSha256
    } catch (e: CertificateMismatchException) {
        e.actual
    }
    if (!policy.confirm(host, port, TrustKind.Tls, seen, known)) throw rejected(host, seen)
    return openTls(host, port, seen)
}

internal fun trustKey(host: String, port: Int, kind: TrustKind) = "$kind $host $port"

internal fun rejected(host: String, fingerprint: String) =
    DeviceException(ErrorCategory.PermissionDenied, "$host: $fingerprint was not accepted")
