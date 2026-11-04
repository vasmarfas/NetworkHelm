package com.vasmarfas.networkhelm.app

interface BrowserAccess {
    val ready: Boolean
    val open: Boolean
    val port: Int
    // any address may log in, not only local networks, and then the code is asked whatever the code flag says
    val public: Boolean
    val code: Boolean
    val otpauth: String?
    val addresses: List<String>
    val certificate: String?
    val webApp: Boolean
    val problem: AccessProblem?

    suspend fun setPassword(password: String)

    suspend fun newCode()

    suspend fun change(port: Int, public: Boolean, code: Boolean)

    suspend fun open()

    suspend fun close()

    suspend fun resume()
}

sealed interface AccessProblem {
    class PortTaken(val port: Int) : AccessProblem

    class Failed(val reason: String) : AccessProblem
}
