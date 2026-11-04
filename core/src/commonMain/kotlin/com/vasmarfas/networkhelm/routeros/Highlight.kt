package com.vasmarfas.networkhelm.routeros

import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.session.DeviceSession
import kotlinx.coroutines.flow.firstOrNull

suspend fun highlight(session: DeviceSession, script: String): List<String>? {
    val row = session.run(MenuPath.parse("console"), "inspect", mapOf("request" to "highlight", "input" to script))
        .firstOrNull { it["type"] == "highlight" } ?: return null
    return perCharacter(script, row["highlight"]?.split(',') ?: return null)
}

internal fun perCharacter(script: String, classes: List<String>): List<String>? {
    if (classes.size == script.length) return classes
    if (classes.size != script.encodeToByteArray().size) return null
    val result = ArrayList<String>(script.length)
    var byte = 0
    var i = 0
    while (i < script.length) {
        val char = script[i]
        val pair = char.isHighSurrogate() && i + 1 < script.length && script[i + 1].isLowSurrogate()
        val width = when {
            pair -> 4
            char.code < 0x80 -> 1
            char.code < 0x800 -> 2
            else -> 3
        }
        result += classes[byte]
        if (pair) result += classes[byte]
        byte += width
        i += if (pair) 2 else 1
    }
    return result
}
