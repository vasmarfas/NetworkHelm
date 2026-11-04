package com.vasmarfas.networkhelm.transport

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileTrustStoreTest {

    @Test
    fun entriesSurviveANewStoreAndReplaceOldOnes() = runTest {
        val file = Files.createTempDirectory("rd-test-trust").resolve("known").toFile()
        FileTrustStore(file).put("fe80::1%eth0", 8729, TrustKind.Tls, "aa")
        FileTrustStore(file).put("10.0.0.1", 22, TrustKind.Ssh, "SHA256:x")
        FileTrustStore(file).put("10.0.0.1", 22, TrustKind.Ssh, "SHA256:y")
        val reopened = FileTrustStore(file)
        assertEquals("aa", reopened.get("fe80::1%eth0", 8729, TrustKind.Tls))
        assertEquals("SHA256:y", reopened.get("10.0.0.1", 22, TrustKind.Ssh))
        assertNull(reopened.get("10.0.0.1", 22, TrustKind.Tls))
        assertEquals(2, file.readLines().size)
    }
}
