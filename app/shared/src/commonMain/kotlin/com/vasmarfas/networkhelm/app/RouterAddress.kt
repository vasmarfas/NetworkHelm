package com.vasmarfas.networkhelm.app

data class RouterAddress(val host: String, val port: Int?)

fun parseAddress(text: String): RouterAddress? {
    val value = text.trim()
    if (value.isEmpty()) return null
    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        if (end <= 1) return null
        val rest = value.substring(end + 1)
        return when {
            rest.isEmpty() -> RouterAddress(value.substring(1, end), null)
            rest.startsWith(":") -> port(rest.substring(1))?.let { RouterAddress(value.substring(1, end), it) }
            else -> null
        }
    }
    if (value.count { it == ':' } > 1) return RouterAddress(value, null)
    val host = value.substringBefore(':')
    if (host.isEmpty() || host.any { it.isWhitespace() }) return null
    if (':' !in value) return RouterAddress(host, null)
    return port(value.substringAfter(':'))?.let { RouterAddress(host, it) }
}

private fun port(text: String) = text.toIntOrNull()?.takeIf { it in 1..65535 }

fun formatAddress(host: String, port: Int?): String = when {
    port == null -> host
    ':' in host -> "[$host]:$port"
    else -> "$host:$port"
}

fun isMacAddress(text: String): Boolean {
    val clean = text.trim()
    val parts = when {
        ':' in clean -> clean.split(':')
        '-' in clean -> clean.split('-')
        '.' in clean -> clean.split('.')
        else -> return false
    }
    return parts.size == 6 && parts.all { it.isNotEmpty() && it.length <= 2 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
}
