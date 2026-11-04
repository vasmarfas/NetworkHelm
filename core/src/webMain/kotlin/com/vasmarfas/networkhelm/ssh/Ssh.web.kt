package com.vasmarfas.networkhelm.ssh

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory

actual suspend fun connectSsh(host: String, port: Int, user: String, auth: SshAuth, acceptHostKey: (HostKey) -> Boolean, timeoutMillis: Int): SshSession =
    throw DeviceException(ErrorCategory.Unsupported, "a browser cannot open SSH connections")
