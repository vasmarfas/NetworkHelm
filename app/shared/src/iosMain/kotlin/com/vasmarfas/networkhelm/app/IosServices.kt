package com.vasmarfas.networkhelm.app

import com.vasmarfas.networkhelm.miknative.WinBoxAddressBook
import com.vasmarfas.networkhelm.miknative.connectNativeHost
import com.vasmarfas.networkhelm.miknative.nativeSide
import com.vasmarfas.networkhelm.session.connectOpenWrtWithSsh
import com.vasmarfas.networkhelm.session.connectWithSsh
import com.vasmarfas.networkhelm.transport.TrustKind
import com.vasmarfas.networkhelm.transport.TrustStore
import eu.anifantakis.lib.ksafe.KSafe
import platform.Foundation.NSUserDefaults

// no MNDP: it needs the multicast entitlement, which Apple grants on request
fun iosServices(): Services = Services(
    DefaultsStorage(),
    DefaultsTrustStore(),
    neighbors = null,
    secrets = KSafeSecrets(KSafe(fileName = "routers")),
    connectOpenWrt = { login, trust -> connectOpenWrtWithSsh(login, trust, forms = null) },
    connect = { login, trust, scope -> connectWithSsh(login, trust, scope, native = login.nativeSide(scope)) },
    connectTcp8291 = ::connectNativeHost,
    readWinBoxBook = WinBoxAddressBook::read,
)

private class DefaultsStorage : Storage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun read(name: String): String? = defaults.stringForKey(name)

    override suspend fun write(name: String, text: String) = defaults.setObject(text, name)
}

// fingerprints of certificates and host keys are public, so UserDefaults is enough
private class DefaultsTrustStore : TrustStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun get(host: String, port: Int, kind: TrustKind) = defaults.stringForKey(key(host, port, kind))

    override suspend fun put(host: String, port: Int, kind: TrustKind, fingerprint: String) = defaults.setObject(fingerprint, key(host, port, kind))

    private fun key(host: String, port: Int, kind: TrustKind) = "trust/$kind/$host/$port"
}
