package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory

actual suspend fun openTcp(host: String, port: Int, timeoutMillis: Int): ByteStream =
    if (browserHttp) FetchStream(host, port, tls = false, timeoutMillis) else throw DeviceException(ErrorCategory.Unsupported, "a browser cannot open TCP connections")

actual suspend fun openTls(host: String, port: Int, pinnedSha256: String?, timeoutMillis: Int): TlsStream =
    if (browserHttp) FetchStream(host, port, tls = true, timeoutMillis) else throw DeviceException(ErrorCategory.Unsupported, "a browser cannot open TCP connections")

actual suspend fun listenTcp(host: String, port: Int): TcpListener =
    throw DeviceException(ErrorCategory.Unsupported, "a browser cannot listen on TCP ports")

actual val tcpListening = false

actual suspend fun openUdp(host: String, port: Int): UdpLink =
    throw DeviceException(ErrorCategory.Unsupported, "a browser cannot send UDP datagrams")
