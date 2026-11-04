package com.vasmarfas.networkhelm.transport

import com.vasmarfas.networkhelm.IosLab
import com.vasmarfas.networkhelm.routeros.api.ApiConnection
import com.vasmarfas.networkhelm.routeros.api.ApiEncoding
import com.vasmarfas.networkhelm.routeros.api.call
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TlsLabTest {

    @Test
    fun aCertificateIsReadThenPinnedAndAnotherOneRefused() = runBlocking {
        for ((prefix, port) in listOf("chr" to 8729, "openwrt" to 443, "openwrt24" to 443)) {
            val host = IosLab.router(prefix, "host")?.getValue("host") ?: continue
            withTimeout(30.seconds) {
                val first = openTls(host, port, null)
                val seen = first.certificateSha256
                first.close()
                assertTrue(Regex("[0-9a-f]{64}").matches(seen), "$prefix: $seen")
                openTls(host, port, seen).close()
                val error = assertFailsWith<CertificateMismatchException>(prefix) { openTls(host, port, seen.reversed()) }
                assertEquals(seen, error.actual)
            }
        }
    }

    @Test
    fun theApiLogsInOverTls() = runBlocking {
        val chr = IosLab.router("chr", "host", "user", "password") ?: return@runBlocking
        withTimeout(30.seconds) {
            val api = ApiConnection(openTls(chr.getValue("host"), 8729, null), ApiEncoding.Utf8, this)
            try {
                api.login(chr.getValue("user"), chr.getValue("password"))
                assertTrue(api.call("/system/identity/print").rows.single().containsKey("name"))
            } finally {
                api.close()
            }
        }
    }
}
