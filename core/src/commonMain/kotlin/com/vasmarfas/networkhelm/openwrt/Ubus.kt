package com.vasmarfas.networkhelm.openwrt

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface Ubus {
    suspend fun call(obj: String, method: String, args: JsonObject = JsonObject(emptyMap())): JsonObject

    suspend fun objects(): Set<String>

    fun listen(vararg events: String): Flow<JsonObject>

    fun subscribe(obj: String): Flow<JsonObject>
}

class UbusException(val status: Int, message: String = statusText(status)) : Exception(message)

object UbusStatus {
    const val INVALID_ARGUMENT = 2
    const val METHOD_NOT_FOUND = 3
    const val NOT_FOUND = 4
    const val NO_DATA = 5
    const val PERMISSION_DENIED = 6
    const val TIMEOUT = 7
    const val NOT_SUPPORTED = 8
    const val UNKNOWN_ERROR = 9
    const val CONNECTION_FAILED = 10
}

private fun statusText(status: Int) = when (status) {
    1 -> "Invalid command"
    2 -> "Invalid argument"
    3 -> "Method not found"
    4 -> "Not found"
    5 -> "No response"
    6 -> "Permission denied"
    7 -> "Request timed out"
    8 -> "Operation not supported"
    10 -> "Connection failed"
    11 -> "Out of memory"
    12 -> "Parsing message data failed"
    13 -> "System error"
    else -> "Unknown error"
}
