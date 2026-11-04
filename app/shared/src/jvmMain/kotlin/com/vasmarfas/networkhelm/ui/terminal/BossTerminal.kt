package com.vasmarfas.networkhelm.ui.terminal

import ai.rever.bossterm.compose.EmbeddableTerminal
import ai.rever.bossterm.compose.PlatformServices
import ai.rever.bossterm.compose.getPlatformServices
import ai.rever.bossterm.compose.rememberEmbeddableTerminalState
import ai.rever.bossterm.compose.settings.TerminalSettingsOverride
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vasmarfas.networkhelm.app.LocalSettings
import com.vasmarfas.networkhelm.ssh.ShellChannel
import kotlinx.coroutines.CompletableDeferred
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

@Composable
fun BossTerminal(shell: ShellChannel, modifier: Modifier) {
    val services = remember(shell) { SshPlatformServices(shell) }
    val lines = LocalSettings.current.terminalLines
    EmbeddableTerminal(
        state = rememberEmbeddableTerminalState(),
        modifier = modifier,
        autoFocus = true,
        platformServices = services,
        settingsOverride = remember(lines) { TerminalSettingsOverride(bufferMaxLines = lines) },
    )
}

internal actual val terminalScrollback = true

private class SshPlatformServices(private val shell: ShellChannel, defaults: PlatformServices = getPlatformServices()) : PlatformServices by defaults {
    override fun getProcessService(): PlatformServices.ProcessService = object : PlatformServices.ProcessService {
        override suspend fun spawnProcess(config: PlatformServices.ProcessService.ProcessConfig): PlatformServices.ProcessService.ProcessHandle =
            SshProcessHandle(shell)
    }
}

internal class SshProcessHandle(private val shell: ShellChannel) : PlatformServices.ProcessService.ProcessHandle {
    private val closed = CompletableDeferred<Int>()

    // a byte that is not UTF-8 left in the buffer would stop the decoding, and the console with it, for good
    private val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    private val bytes = ByteBuffer.allocate(16 * 1024)
    private val chars = CharBuffer.allocate(16 * 1024)

    override suspend fun write(data: String) = shell.write(data.encodeToByteArray())

    override suspend fun writeBytes(data: ByteArray) = shell.write(data)

    // a UTF-8 sequence split between two reads stays in the buffer until the rest arrives
    override suspend fun read(): String? {
        val count = shell.read(bytes.array(), bytes.position(), bytes.remaining())
        if (count < 0) {
            closed.complete(0)
            return null
        }
        bytes.position(bytes.position() + count)
        bytes.flip()
        chars.clear()
        decoder.decode(bytes, chars, false)
        bytes.compact()
        chars.flip()
        return chars.toString()
    }

    override fun isAlive(): Boolean = !closed.isCompleted

    override suspend fun kill() {
        shell.close()
        closed.complete(0)
    }

    override suspend fun waitFor(): Int = closed.await()

    override suspend fun resize(columns: Int, rows: Int) = shell.resize(columns, rows)

    override fun getExitCode(): Int? = if (closed.isCompleted) 0 else null

    override fun getPid(): Long? = null

    override fun getWorkingDirectory(): String? = null
}
