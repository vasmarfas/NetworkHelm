package com.vasmarfas.networkhelm.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class MenuPath(val segments: List<String>) {

    override fun toString() = segments.joinToString("/")

    fun apiCommand(command: String) = "/" + (segments + command).joinToString("/")

    val cli: String get() = "/" + segments.joinToString(" ")

    fun child(name: String) = MenuPath(segments + name)

    val parent: MenuPath? get() = if (segments.isEmpty()) null else MenuPath(segments.dropLast(1))

    companion object {
        val Root = MenuPath(emptyList())

        fun parse(text: String) = MenuPath(text.trim().split('/', ' ').filter { it.isNotEmpty() })
    }
}
