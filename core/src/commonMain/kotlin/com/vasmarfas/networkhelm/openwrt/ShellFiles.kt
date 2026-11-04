package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.ssh.RemoteFile
import com.vasmarfas.networkhelm.ssh.SftpClient
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.ByteStream
import kotlin.time.Instant

internal class OpenWrtSsh(private val ssh: SshSession) : SshSession by ssh {
    override suspend fun sftp(): SftpClient = if (ssh.exec("[ -x $SFTP_SERVER ]").exitStatus == 0) ssh.sftp() else ShellFiles(ssh)
}

internal const val SFTP_SERVER = "/usr/libexec/sftp-server"

// head -c N ends the command after the last byte, so the channel closes without a half close from the client
internal class ShellFiles(private val ssh: SshSession) : SftpClient {
    override suspend fun list(path: String): List<RemoteFile> {
        val listed = ssh.exec("ls -la --full-time ${quote(path)}")
        if (listed.exitStatus != 0) throw DeviceException(ErrorCategory.MissingItem, listed.error.ifBlank { listed.output }.trim())
        return listing(listed.output)
    }

    override suspend fun read(path: String): ByteArray {
        if (ssh.exec("[ -f ${quote(path)} ] && [ -r ${quote(path)} ]").exitStatus != 0) throw DeviceException(ErrorCategory.MissingItem, "$path cannot be read")
        val stream = ssh.process("cat ${quote(path)}")
        try {
            return stream.readAll()
        } finally {
            stream.close()
        }
    }

    override suspend fun write(path: String, bytes: ByteArray) {
        val stream = ssh.process("head -c ${bytes.size} > ${quote(path)}")
        try {
            stream.write(bytes)
            stream.readAll()
        } finally {
            stream.close()
        }
        if (ssh.exec("[ $(wc -c < ${quote(path)}) -eq ${bytes.size} ]").exitStatus != 0) throw DeviceException(ErrorCategory.General, "$path was not written whole")
    }

    override suspend fun remove(path: String) {
        val removed = ssh.exec("if [ -d ${quote(path)} ] && [ ! -L ${quote(path)} ]; then rmdir ${quote(path)}; else rm -f ${quote(path)}; fi")
        if (removed.exitStatus != 0) throw DeviceException(ErrorCategory.General, removed.error.ifBlank { removed.output }.trim())
    }

    override suspend fun rename(from: String, to: String) {
        val moved = ssh.exec("if [ -e ${quote(to)} ]; then printf '%s\\n' ${quote("${to.substringAfterLast('/')} exists")} >&2; exit 1; fi; mv ${quote(from)} ${quote(to)}")
        if (moved.exitStatus != 0) throw DeviceException(ErrorCategory.General, moved.error.ifBlank { moved.output }.trim())
    }

    override suspend fun mkdir(path: String) {
        val made = ssh.exec("mkdir ${quote(path)}")
        if (made.exitStatus != 0) throw DeviceException(ErrorCategory.General, made.error.ifBlank { made.output }.trim())
    }

    override fun close() = Unit

    private suspend fun ByteStream.readAll(): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(65_536)
        while (true) {
            val count = read(buffer, 0, buffer.size)
            if (count < 0) break
            chunks += buffer.copyOf(count)
        }
        val all = ByteArray(chunks.sumOf { it.size })
        var at = 0
        for (chunk in chunks) {
            chunk.copyInto(all, at)
            at += chunk.size
        }
        return all
    }
}

internal fun listing(output: String): List<RemoteFile> = output.lines().mapNotNull { line ->
    val words = line.trim().split(Regex("\\s+"), limit = 9)
    if (words.size < 9 || words[0].length != 10) return@mapNotNull null
    val name = words[8].substringBefore(" -> ")
    if (name == "." || name == "..") return@mapNotNull null
    val zone = words[7]
    val modified = Instant.parse("${words[5]}T${words[6]}${zone.take(3)}:${zone.drop(3)}").epochSeconds
    RemoteFile(name, words[4].toLongOrNull() ?: 0, words[0].startsWith('d'), modified)
}
