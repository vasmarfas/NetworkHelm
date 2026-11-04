package com.vasmarfas.networkhelm.transport

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustPolicyTest {

    @Test
    fun acceptedFingerprintIsRememberedPerHostPortAndKind() = runTest {
        val store = MemoryTrustStore()
        val asked = mutableListOf<TrustRequest>()
        val policy = TrustPolicy(store) { asked += it; true }
        assertTrue(policy.confirm("10.0.0.1", 8729, TrustKind.Tls, "aa", null))
        assertEquals("aa", policy.known("10.0.0.1", 8729, TrustKind.Tls))
        assertNull(policy.known("10.0.0.1", 22, TrustKind.Ssh))
        assertNull(policy.known("10.0.0.1", 8730, TrustKind.Tls))
        assertEquals(1, asked.size)
    }

    @Test
    fun declinedFingerprintIsNotStoredAndThePreviousOneStays() = runTest {
        val store = MemoryTrustStore()
        store.put("10.0.0.1", 22, TrustKind.Ssh, "SHA256:old")
        var request: TrustRequest? = null
        val policy = TrustPolicy(store) { request = it; false }
        assertFalse(policy.confirm("10.0.0.1", 22, TrustKind.Ssh, "SHA256:new", "SHA256:old"))
        assertEquals("SHA256:old", request?.previous)
        assertEquals("SHA256:old", policy.known("10.0.0.1", 22, TrustKind.Ssh))
    }
}
