package com.vasmarfas.networkhelm.session

import com.vasmarfas.networkhelm.openwrt.LuciForms
import com.vasmarfas.networkhelm.openwrt.OpenWrtLogin
import com.vasmarfas.networkhelm.openwrt.OpenWrtProtocol
import com.vasmarfas.networkhelm.openwrt.OpenWrtSession
import com.vasmarfas.networkhelm.openwrt.Ubus
import com.vasmarfas.networkhelm.openwrt.connectOpenWrt
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsSession
import com.vasmarfas.networkhelm.routeros.connectRouterOs
import com.vasmarfas.networkhelm.routeros.schema.MemorySchemaCache
import com.vasmarfas.networkhelm.routeros.schema.SchemaCache
import com.vasmarfas.networkhelm.ssh.LazySsh
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.SshService
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.ssh.connectSshTrusted
import com.vasmarfas.networkhelm.ssh.sshReach
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope

suspend fun connectWithSsh(login: RouterOsLogin, trust: TrustPolicy, scope: CoroutineScope, schemaCache: SchemaCache = MemorySchemaCache(), native: NativeSide? = null): DeviceSession {
    val auth = SshAuth.of(login.password, login.sshKey, login.sshAgent, login.sshPassphrase)
    val opened = CompletableDeferred<RouterOsSession>()
    val ssh = LazySsh(probe = { sshReach(login.host, opened.await().sshService(), login.sshPort) }) {
        val port = if (login.sshPort == 22) opened.await().sshPort() ?: 22 else login.sshPort
        connectSshTrusted(login.host, port, login.user, auth, trust)
    }
    return connectRouterOs(login, trust, scope, ssh, schemaCache, native).also(opened::complete)
}

suspend fun connectOpenWrtWithSsh(login: OpenWrtLogin, trust: TrustPolicy, forms: ((Ubus, SshSession?) -> LuciForms)?): DeviceSession {
    val auth = SshAuth.of(login.password, login.sshKey, login.sshAgent, login.sshPassphrase)
    if (login.protocol != OpenWrtProtocol.Ssh) {
        val opened = CompletableDeferred<OpenWrtSession>()
        val ssh = LazySsh(probe = { sshReach(login.host, SshService(opened.await().sshPort(), false), login.sshPort) }) {
            val port = if (login.sshPort == 22) opened.await().sshPort() ?: 22 else login.sshPort
            connectSshTrusted(login.host, port, login.user, auth, trust)
        }
        return connectOpenWrt(login, trust, ssh, forms?.let { make -> { ubus -> make(ubus, null) } }).also(opened::complete)
    }
    val ssh = connectSshTrusted(login.host, login.port, login.user, auth, trust)
    return try {
        connectOpenWrt(ssh, forms?.let { make -> { ubus -> make(ubus, ssh) } })
    } catch (e: Exception) {
        ssh.close()
        throw e
    }
}
