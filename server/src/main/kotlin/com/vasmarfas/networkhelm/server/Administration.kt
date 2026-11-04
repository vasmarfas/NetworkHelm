package com.vasmarfas.networkhelm.server

import java.io.File

private val OPTIONS = listOf("--set-password", "--reset-password", "--new-code")

private const val MIN_PASSWORD = 8

fun administer(home: File, args: Array<String>, environment: Map<String, String>, ask: (String) -> String?, say: (String) -> Unit): Int? {
    val named = args.filter { it in OPTIONS }
    if (named.isEmpty()) return null
    return try {
        require(named.size == 1) { "name one of ${OPTIONS.joinToString()}" }
        when (named.single()) {
            "--set-password" -> setPassword(home, environment, ask, say)
            "--reset-password" -> resetPassword(home, say)
            else -> newCode(home, say)
        }
        0
    } catch (e: IllegalArgumentException) {
        say("Nothing is changed: ${e.message}.")
        1
    } catch (e: IllegalStateException) {
        say("Nothing is changed: ${e.message}.")
        1
    }
}

private fun setPassword(home: File, environment: Map<String, String>, ask: (String) -> String?, say: (String) -> Unit) {
    val password = environment["NETWORKHELM_GATEWAY_PASSWORD"]?.takeIf { it.isNotBlank() } ?: run {
        val first = typed(ask, "New password of admin: ")
        require(first == typed(ask, "The same again: ")) { "the two passwords differ" }
        first
    }
    require(password.length >= MIN_PASSWORD) { "the password has less than $MIN_PASSWORD characters" }
    val sealed = Gateway.account(home) != null && File(home, "secrets.json").isFile
    val current = if (!sealed) null else environment["NETWORKHELM_GATEWAY_CURRENT_PASSWORD"]?.takeIf { it.isNotBlank() } ?: run {
        val typed = ask("Current password, or Enter if it is lost (the saved passwords of the routers are put aside then): ")
        requireNotNull(typed) { "the saved passwords of the routers need the current password in NETWORKHELM_GATEWAY_CURRENT_PASSWORD, or --reset-password drops them" }
        typed.takeIf { it.isNotEmpty() }
    }
    Gateway.changePassword(home, current, password, say)
    say("The password of admin is set. A gateway that runs takes it at its next request and ends its sessions.")
}

private fun resetPassword(home: File, say: (String) -> Unit) {
    val password = madeUpPassword()
    Gateway.changePassword(home, null, password, say)
    say("The password of admin, shown this once: $password")
}

private fun newCode(home: File, say: (String) -> Unit) {
    say("The code for an authenticator app: ${Gateway.newCode(home).otpauth}")
    say("The login page shows its QR code after the password; the code is asked with --code or --public.")
}

private fun typed(ask: (String) -> String?, prompt: String): String =
    requireNotNull(ask(prompt)) { "there is no terminal to type on: give the password in NETWORKHELM_GATEWAY_PASSWORD" }
