package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.Base64Bytes
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.transport.openTlsTrusted
import kotlinx.serialization.Serializable

enum class OpenWrtProtocol(val encrypted: Boolean, val defaultPort: Int) {
    Ssh(encrypted = true, defaultPort = 22),
    UbusSsl(encrypted = true, defaultPort = 443),
    Ubus(encrypted = false, defaultPort = 80),
}

@Serializable
class OpenWrtLogin(
    val host: String,
    val user: String,
    val password: String,
    val protocol: OpenWrtProtocol = OpenWrtProtocol.Ssh,
    val port: Int = protocol.defaultPort,
    @Serializable(with = Base64Bytes::class) val sshKey: ByteArray? = null,
    val sshAgent: Boolean = false,
    val sshPort: Int = 22,
    val sshPassphrase: String? = null,
)

suspend fun connectOpenWrt(ssh: SshSession, forms: ((Ubus) -> LuciForms)? = null): OpenWrtSession = try {
    val ubus = SshUbus(ssh)
    val uci = if ("uci" in ubus.objects()) RpcdUci(ubus) { uciSession(ubus) } else ShellUci(ssh)
    OpenWrtSession(ubus, uci, readFacts(ubus, ssh), ssh, encrypted = true, forms?.invoke(SshUbus(ssh)))
} catch (e: Exception) {
    throw e.toDeviceException()
}

suspend fun connectOpenWrt(login: OpenWrtLogin, trust: TrustPolicy, ssh: SshSession? = null, forms: ((Ubus) -> LuciForms)? = null): OpenWrtSession = try {
    val tls = login.protocol.encrypted
    val ubus = HttpUbus(login.host, login.user, login.password) { if (tls) openTlsTrusted(login.host, login.port, trust) else openTcp(login.host, login.port) }
    OpenWrtSession(ubus, RpcdUci(ubus), readFacts(ubus, null), ssh, encrypted = tls, forms?.invoke(ubus))
} catch (e: Exception) {
    throw e.toDeviceException()
}

suspend fun connectOpenWrt(user: String, password: String, web: UbusWeb, encrypted: Boolean): OpenWrtSession = try {
    val ubus = HttpUbus(user, password, web)
    OpenWrtSession(ubus, RpcdUci(ubus), readFacts(ubus, null), null, encrypted, null)
} catch (e: Exception) {
    throw e.toDeviceException()
}
