package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.ssh.ExecResult
import com.vasmarfas.networkhelm.ssh.SshSession
import com.vasmarfas.networkhelm.transport.BufferedStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// calls share one long-lived sh: a channel per call costs dropbear about 60 ms, a line to the shell about 7
class SshUbus(private val ssh: SshSession, private val maxResponseBytes: Int = MAX_RESPONSE_BYTES) : Ubus {
    private val lock = Mutex()
    private var shell: BufferedStream? = null

    override suspend fun call(obj: String, method: String, args: JsonObject): JsonObject {
        val output = ubus("call ${quote(obj)} ${quote(method)} ${quote(args.toString())}").joinToString("")
        return if (output.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(output).jsonObject
    }

    override suspend fun objects(): Set<String> = ubus("list").filter { it.isNotBlank() }.toSet()

    // a file of /proc read with cat comes whole, where rpcd reads only its first page
    suspend fun sh(command: String): ExecResult {
        val (status, output) = run("{ $command; } 2>&1")
        return ExecResult(output.joinToString("\n"), "", status)
    }

    private suspend fun ubus(command: String): List<String> {
        val (status, output) = run("ubus -S $command 2>/dev/null")
        if (status != 0) throw UbusException(status)
        return output
    }

    private suspend fun run(command: String): Pair<Int, List<String>> {
        val output = mutableListOf<String>()
        val status = lock.withLock {
            val shell = shell ?: BufferedStream(ssh.process("sh")).also { shell = it }
            var status = -1
            var size = 0
            try {
                shell.write("$command; s=$?; echo; echo \"$END \$s\"\n".encodeToByteArray())
                while (status < 0) {
                    val line = shell.readLine(maxOf(maxResponseBytes - size, END.length + 4)) ?: throw UbusException(UbusStatus.CONNECTION_FAILED, "the shell on the router has ended")
                    if (line.startsWith(END)) {
                        status = line.substringAfter(' ').toInt()
                    } else {
                        size += line.encodeToByteArray().size + 1
                        if (size > maxResponseBytes) throw DeviceException(ErrorCategory.Api, "the ubus response exceeds $maxResponseBytes bytes")
                        output += line
                    }
                }
            } finally {
                if (status < 0) {
                    shell.close()
                    this.shell = null
                }
            }
            status
        }
        return status to output
    }

    override fun listen(vararg events: String): Flow<JsonObject> = messages(untilClosed("ubus -S listen " + events.joinToString(" ") { quote(it) }))

    override fun subscribe(obj: String): Flow<JsonObject> = messages(untilClosed("ubus -S subscribe ${quote(obj)}"))

    fun output(command: String): Flow<String> = flow {
        val stream = BufferedStream(ssh.process(command))
        try {
            while (true) emit(stream.readLine(maxResponseBytes) ?: break)
        } finally {
            stream.close()
        }
    }

    private fun messages(command: String) = output(command).filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }

    private companion object {
        const val END = "rd-status"
        const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024
    }
}

internal fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"

// dropbear leaves a command running when its channel closes, and a ubus subscription left so lives on in ubusd
internal fun untilClosed(command: String) = "exec 3<&0; $command & p=\$!; { read _ <&3; kill \$p; } >/dev/null 2>&1 & wait \$p"
