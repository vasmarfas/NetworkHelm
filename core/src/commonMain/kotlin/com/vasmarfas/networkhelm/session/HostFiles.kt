package com.vasmarfas.networkhelm.session

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.ssh.RemoteFile
import com.vasmarfas.networkhelm.ssh.SftpClient
import com.vasmarfas.networkhelm.ssh.SshSession

internal class HostFiles(private val ssh: SshSession) {
    private var places: Places? = null

    private class Places(val folders: List<String>, val offset: String)

    suspend fun read(): List<Item> {
        val places = places()
        return sftp { client ->
            places.folders.flatMap { folder ->
                val name = folder.drop(1)
                val files = client.list(folder)
                if (name.isEmpty()) {
                    files.map { item(it.name, it, places.offset) } + files.filter { it.directory }.flatMap { directory ->
                        client.list("/${directory.name}").map { item("${directory.name}/${it.name}", it, places.offset) }
                    }
                } else {
                    listOf(Item(name, mapOf("name" to name, "type" to "directory"))) + files.map { item("$name/${it.name}", it, places.offset) }
                }
            }
        }
    }

    suspend fun remove(names: List<String>) = sftp { client -> names.forEach { client.remove("/$it") } }

    private fun item(path: String, file: RemoteFile, offset: String) = Item(path, mapOf(
        "name" to path,
        "type" to if (file.directory) "directory" else "file",
        "size" to "${file.size}",
        "last-modified" to local(file.modifiedEpochSeconds, offset),
    ))

    private suspend fun places(): Places {
        places?.let { return it }
        val found = try {
            val lines = ssh.exec("pwd; date +%z").output.lines().map(String::trim)
            val home = lines.getOrNull(0)?.takeIf { it.startsWith("/") && it.length > 1 }
            Places(listOfNotNull(home, "/tmp").distinct(), lines.getOrNull(1).orEmpty())
        } catch (e: DeviceException) {
            if (e.category != ErrorCategory.Unsupported) throw e
            Places(listOf("/"), "")
        }
        return found.also { places = it }
    }

    private suspend fun <T> sftp(block: suspend (SftpClient) -> T): T {
        val client = ssh.sftp()
        return try {
            block(client)
        } finally {
            client.close()
        }
    }
}
