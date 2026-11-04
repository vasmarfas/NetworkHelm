package com.vasmarfas.networkhelm.transport

interface TlsStream : ByteStream {
    val certificateSha256: String
}

class CertificateMismatchException(val expected: String, val actual: String) :
    Exception("certificate SHA-256 $actual, expected $expected")

expect suspend fun openTcp(host: String, port: Int, timeoutMillis: Int = 10_000): ByteStream

// without a pin any certificate is accepted, so the caller asks the user before sending anything
// with a pin, a different certificate fails the handshake before any data goes out
expect suspend fun openTls(host: String, port: Int, pinnedSha256: String?, timeoutMillis: Int = 10_000): TlsStream

interface TcpListener {
    val port: Int

    suspend fun accept(): ByteStream

    fun close()
}

expect suspend fun listenTcp(host: String, port: Int): TcpListener

expect val tcpListening: Boolean

interface UdpLink {
    suspend fun send(datagram: ByteArray)

    suspend fun receive(timeoutMillis: Int): ByteArray?

    fun close()
}

expect suspend fun openUdp(host: String, port: Int): UdpLink
