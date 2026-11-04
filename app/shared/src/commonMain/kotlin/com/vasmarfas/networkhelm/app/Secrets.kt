package com.vasmarfas.networkhelm.app

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeProtectionInfo

interface Secrets {
    val protection: KSafeProtectionInfo? get() = null

    suspend fun get(key: String): String?

    suspend fun put(key: String, value: String)

    suspend fun delete(key: String)
}

class MemorySecrets : Secrets {
    private val values = HashMap<String, String>()

    override suspend fun get(key: String) = values[key]

    override suspend fun put(key: String, value: String) {
        values[key] = value
    }

    override suspend fun delete(key: String) {
        values.remove(key)
    }
}

class KSafeSecrets(private val store: KSafe) : Secrets {
    override val protection: KSafeProtectionInfo get() = store.protectionInfo

    override suspend fun get(key: String) = store.get<String?>(key, null)

    override suspend fun put(key: String, value: String) = store.put(key, value)

    override suspend fun delete(key: String) = store.delete(key)
}

fun passwordKey(address: String, user: String) = "password/$address/$user"

fun sshKeyKey(address: String, user: String) = "ssh-key/$address/$user"

fun sshPassphraseKey(address: String, user: String) = "ssh-passphrase/$address/$user"

fun snmpPrivacyKey(address: String, user: String) = "snmp-privacy/$address/$user"
