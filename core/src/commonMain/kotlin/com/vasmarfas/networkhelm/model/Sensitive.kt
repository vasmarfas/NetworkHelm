package com.vasmarfas.networkhelm.model

// a bare key is too common a name to count, the variables of a container have one
fun sensitive(property: String, type: ValueType? = null): Boolean {
    if (type == ValueType.Secret) return true
    val name = property.split('.', ' ').last().replace('_', '-')
    return secretWords.any { name == it || name.endsWith("-$it") } || name.matches(wepKey)
}

// a field with choices refers to an element instead of holding a key, as auth-key of the NTP server does
fun Argument.sensitive() = type == ValueType.Secret || choices.isEmpty() && sensitive(name, type)

private val secretWords =listOf("password", "passphrase", "secret", "psk", "private-key", "preshared-key", "pre-shared-key", "auth-key", "authentication-key", "md5-key", "enc-key")

private val wepKey = Regex("static-key-[0-3]")
