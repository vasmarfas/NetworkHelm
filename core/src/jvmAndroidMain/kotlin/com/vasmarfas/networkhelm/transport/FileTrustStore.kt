package com.vasmarfas.networkhelm.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FileTrustStore(private val file: File) : TrustStore {
    private val lock = Mutex()

    override suspend fun get(host: String, port: Int, kind: TrustKind): String? = lock.withLock { read()[trustKey(host, port, kind)] }

    override suspend fun put(host: String, port: Int, kind: TrustKind, fingerprint: String) = lock.withLock {
        val entries = read() + (trustKey(host, port, kind) to fingerprint)
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(entries.entries.joinToString("") { "${it.key} ${it.value}\n" })
        }
    }

    private suspend fun read(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext emptyMap()
        file.readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.associate {
            val split = it.lastIndexOf(' ')
            it.substring(0, split) to it.substring(split + 1)
        }
    }
}
