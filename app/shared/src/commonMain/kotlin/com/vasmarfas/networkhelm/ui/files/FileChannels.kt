package com.vasmarfas.networkhelm.ui.files

import com.vasmarfas.networkhelm.ssh.HostKeyRejectedException

enum class FileVia { Api, Sftp, Native, Rpcd }

enum class Why { Rights, SshOff, SshClosed, KeyRefused, NoSsh, TooBig }

internal sealed interface Bulk {
    data object Asking : Bulk

    data class Ready(val via: FileVia) : Bulk

    data class Missing(val why: Why, val port: Int? = null) : Bulk
}

internal sealed interface Route {
    data class Over(val via: FileVia) : Route

    data class Blocked(val why: Why, val port: Int? = null) : Route
}

internal class NoChannelException(val why: Why, val port: Int? = null) : Exception("no channel for the files: $why")

internal fun Throwable.refusedHostKey(): Boolean = generateSequence(this) { it.cause }.any { it is HostKeyRejectedException }
