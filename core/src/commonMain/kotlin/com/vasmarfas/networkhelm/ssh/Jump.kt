package com.vasmarfas.networkhelm.ssh

import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.listenTcp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Hop(val host: String, val port: Int, val user: String, val auth: SshAuth)

class Jump private constructor(private val scope: CoroutineScope) {
    private val sessions = mutableListOf<SshSession>()
    private val tunnels = mutableListOf<Forwarding>()
    private val ends = mutableMapOf<Int, Pair<String, Int>>()

    val leads: Map<Int, Pair<String, Int>> get() = ends

    suspend fun tunnel(host: String, port: Int): Int {
        val forwarding = Forwarding(sessions.last(), listenTcp(LOOPBACK, 0), host, port)
        tunnels += forwarding
        ends[forwarding.localPort] = host to port
        scope.launch { forwarding.serve() }
        return forwarding.localPort
    }

    fun close() {
        tunnels.forEach { it.close() }
        sessions.reversed().forEach { it.close() }
    }

    companion object {
        const val LOOPBACK = "127.0.0.1"

        suspend fun open(hops: List<Hop>, trust: TrustPolicy, scope: CoroutineScope): Jump {
            val jump = Jump(scope)
            try {
                for (hop in hops) {
                    val (host, port) = if (jump.sessions.isEmpty()) hop.host to hop.port else LOOPBACK to jump.tunnel(hop.host, hop.port)
                    jump.sessions += connectSshTrusted(host, port, hop.user, hop.auth, trust.through(jump.leads))
                }
            } catch (e: Exception) {
                jump.close()
                throw e
            }
            return jump
        }
    }
}

class JumpedSession(private val session: DeviceSession, private val jump: Jump) : DeviceSession by session {
    override fun close() {
        session.close()
        jump.close()
    }
}
