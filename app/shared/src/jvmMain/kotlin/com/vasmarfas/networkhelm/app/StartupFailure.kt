package com.vasmarfas.networkhelm.app

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.time.LocalDateTime

// the launcher of the installed app only says "Failed to launch JVM" when the program dies at its start, so the cause is kept in a file;
// the temporary folder of Linux is shared, so a link left under the name of the file is not followed
fun reportStartupFailure(error: Throwable, directory: File = File(System.getProperty("java.io.tmpdir"))): File? {
    val file = File(directory, "NetworkHelm-startup-error.log")
    val text = buildString {
        appendLine("${LocalDateTime.now()} NetworkHelm ${System.getProperty("jpackage.app-version").orEmpty()}")
        appendLine("${System.getProperty("os.name")} ${System.getProperty("os.version")}, Java ${System.getProperty("java.version")}")
        appendLine()
        append(error.stackTraceToString())
    }
    return try {
        Files.writeString(file.toPath(), text, CREATE, TRUNCATE_EXISTING, NOFOLLOW_LINKS)
        file
    } catch (e: IOException) {
        null
    }
}
