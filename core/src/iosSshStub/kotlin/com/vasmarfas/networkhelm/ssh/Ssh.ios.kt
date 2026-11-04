package com.vasmarfas.networkhelm.ssh

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory

// libssh2 comes in through cinterop, which Apple targets run only on a Mac; elsewhere a klib gets this stub
actual suspend fun connectSsh(host: String, port: Int, user: String, auth: SshAuth, acceptHostKey: (HostKey) -> Boolean, timeoutMillis: Int): SshSession =
    throw DeviceException(ErrorCategory.Unsupported, "SSH for iOS is built only on a Mac")
