package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory

// the browser does not tell an unreachable device from a refusal over CORS, mixed content or a certificate
class BrowserBlockedException(val url: String, cause: Throwable? = null) :
    DeviceException(ErrorCategory.ConnectionLost, "the browser did not let the page reach $url", cause)

class BrowserTrust : TrustStore {
    override suspend fun get(host: String, port: Int, kind: TrustKind): String = BROWSER_CHECKED

    override suspend fun put(host: String, port: Int, kind: TrustKind, fingerprint: String) = Unit
}

const val BROWSER_CHECKED = "checked by the browser"
