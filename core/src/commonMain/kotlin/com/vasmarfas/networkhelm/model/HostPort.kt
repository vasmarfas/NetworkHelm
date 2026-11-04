package com.vasmarfas.networkhelm.model

internal fun hostPort(address: String, port: String?) = when {
    port.isNullOrEmpty() -> address
    ':' in address -> "[$address]:$port"
    else -> "$address:$port"
}
