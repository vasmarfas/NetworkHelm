package com.vasmarfas.networkhelm

import com.vasmarfas.networkhelm.server.Gateway
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.netty.util.Version
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TlsTest {

    @Test
    fun theNettyRuntimeUsesThePatchedHttpCodec() {
        val versions = Version.identify().filterKeys { it.startsWith("netty-") }
        assertTrue("netty-codec-http" in versions)
        assertEquals(setOf("4.2.19.Final"), versions.values.map { it.artifactVersion() }.toSet())
    }

    @Test
    fun theGatewayServesHttpsWithItsOwnCertificate() {
        val home = createTempDirectory("rd-test-gateway").toFile()
        val gateway = Gateway.open(home, public = false, code = false, password = "rd-test-password", announce = {})
        val keyStore = gateway.keyStore()
        val certificate = keyStore.getCertificate(Gateway.KEY_ALIAS) as X509Certificate
        assertTrue(certificate.subjectAlternativeNames.orEmpty().any { it[1] == "localhost" })
        assertTrue(certificate.notAfter.time - certificate.notBefore.time > 3000L * 24 * 60 * 60 * 1000, "about ten years")
        assertEquals(certificate, Gateway.open(home, public = false, code = false, password = null, announce = {}).keyStore().getCertificate(Gateway.KEY_ALIAS))

        val server = embeddedServer(Netty, applicationEnvironment {}, {
            sslConnector(keyStore, Gateway.KEY_ALIAS, gateway::keyStorePassword, gateway::keyStorePassword) {
                host = "127.0.0.1"
                port = 0
            }
        }) { gateway(gateway) }.start()
        try {
            val port = runBlocking { server.engine.resolvedConnectors().single().port }
            val trusted = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setCertificateEntry("gateway", certificate)
            }
            val context = SSLContext.getInstance("TLS").apply {
                init(null, TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trusted) }.trustManagers, null)
            }
            val connection = URI.create("https://localhost:$port/login").toURL().openConnection() as HttpsURLConnection
            connection.sslSocketFactory = context.socketFactory
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.outputStream.use { it.write("{\"user\":\"admin\",\"password\":\"wrong\",\"code\":\"000000\"}".encodeToByteArray()) }
            assertEquals(401, connection.responseCode)
        } finally {
            server.stop(0, 0)
        }
    }
}
