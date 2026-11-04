package com.vasmarfas.networkhelm.server

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TotpTest {

    private val secret = "12345678901234567890".encodeToByteArray()

    @Test
    fun codesAreThoseOfTheRfc() {
        val totp = Totp(secret)
        assertEquals(listOf("287082", "081804", "050471", "005924", "279037"), listOf(59L, 1111111109L, 1111111111L, 1234567890L, 2000000000L).map { totp.code(it / 30) })
    }

    @Test
    fun aCodePassesOnceInItsStepAndTheNextOnes() {
        var now = 1111111109_000L
        val totp = Totp(secret) { now }
        assertTrue(totp.accept("081804"))
        assertFalse(totp.accept("081804"), "the same step twice")
        now += 30_000
        assertTrue(totp.accept(totp.code(now / 30_000)))
        assertFalse(totp.accept(totp.code(now / 30_000 - 1)), "a step before the one used")
        assertFalse(totp.accept(totp.code(now / 30_000 + 5)))
    }

    @Test
    fun base32GoesBothWays() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", base32(secret))
        assertContentEquals(secret, fromBase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"))
        val odd = byteArrayOf(-1, 0, 7)
        assertContentEquals(odd, fromBase32(base32(odd)))
    }
}
