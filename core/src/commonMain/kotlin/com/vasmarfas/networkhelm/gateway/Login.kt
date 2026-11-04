package com.vasmarfas.networkhelm.gateway

import kotlinx.serialization.Serializable

// GET /login: 204 for an open session, else 401 with LoginPage; POST /login: 204, 401, or 200 with SetUpCode
@Serializable
class LoginPage(val code: Boolean, val setUp: Boolean)

@Serializable
class LoginForm(val user: String, val password: String, val code: String = "")

@Serializable
class SetUpCode(val otpauth: String)
