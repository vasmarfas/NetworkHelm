package com.vasmarfas.networkhelm

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import platform.posix.getenv

object IosLab {
    @OptIn(ExperimentalForeignApi::class)
    private val file = getenv("NETWORKHELM_LAB")?.toKString()?.let(::Path)?.takeIf(SystemFileSystem::exists)
    private val values = file?.let { SystemFileSystem.source(it).buffered().use(Source::readString) }.orEmpty().lines()
        .filter { '=' in it && !it.startsWith("#") }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

    fun router(prefix: String, vararg keys: String): Map<String, String>? =
        keys.associateWith { values["$prefix.$it"].orEmpty() }.takeIf { fields -> fields.values.none(String::isEmpty) }

    fun bytes(path: String) = SystemFileSystem.source(Path(file?.parent ?: Path("."), path)).buffered().use(Source::readByteArray)
}
