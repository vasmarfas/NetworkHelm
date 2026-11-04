package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory

private const val ID_BYTES = 6

class RomonTarget(val id: String, val user: String, val password: String) {
    internal val idBytes: ByteArray = id.split(':').map { part ->
        part.toIntOrNull(16)?.takeIf { part.length == 2 }?.toByte()
            ?: throw DeviceException(ErrorCategory.ArgumentValue, "$id is not a RoMON id")
    }.toByteArray().also {
        if (it.size != ID_BYTES) throw DeviceException(ErrorCategory.ArgumentValue, "$id is not a RoMON id")
    }
}
