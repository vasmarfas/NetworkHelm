package com.vasmarfas.networkhelm.winbox

// not a data class: its toString would print the password into a log
class WinBoxSession(val address: String, val user: String, val password: String, val group: String, val comment: String, val ip: String)

class WinBoxBookException(message: String) : Exception(message)
