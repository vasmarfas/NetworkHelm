package com.vasmarfas.networkhelm.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class FileStorage(private val directory: File, private val replace: (File, File) -> Unit = ::replaceFile) : Storage {
    private val writing = ConcurrentHashMap<String, Mutex>()

    override suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        File(directory, name).takeIf { it.isFile }?.readText()
    }

    // one writer at a time for a name: two of them share the temporary file, and Windows refuses a replacement of a file that another one is replacing
    override suspend fun write(name: String, text: String) = writing.getOrPut(name) { Mutex() }.withLock {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val file = File(directory, name)
            val temporary = File(directory, "$name.tmp")
            try {
                temporary.writeText(text)
                replace(temporary, file)
            } finally {
                if (temporary.exists() && !temporary.delete()) throw IOException("could not remove the temporary file")
            }
        }
    }
}

internal expect fun replaceFile(temporary: File, target: File)
