package com.vasmarfas.networkhelm.miknative

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WCurveTest {
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun scalar(little: String): U256 {
        val bytes = hex(little)
        bytes[0] = (bytes[0].toInt() and 248).toByte()
        bytes[31] = ((bytes[31].toInt() and 127) or 64).toByte()
        return U256.fromBytes(bytes.reversedArray())
    }

    private fun uCoordinate(little: String) = Fe.fromBytes(hex(little).reversedArray())

    private fun Fe.power(exponent: U256): Fe {
        var result = Fe.ONE
        for (bit in exponent.bitLength - 1 downTo 0) {
            result = result.square()
            if (exponent.testBit(bit)) result = result * this
        }
        return result
    }

    private val alicePrivate = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
    private val alicePublic = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
    private val bobPrivate = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
    private val bobPublic = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
    private val shared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"

    private val montgomeryA = Fe.of(486662)

    private val b = (Fe.of(2) * montgomeryA * montgomeryA * montgomeryA - Fe.of(9) * montgomeryA) * Fe.of(27).inverse()

    @Test
    fun publicKeysMatchRfc7748() {
        for ((private, public) in listOf(alicePrivate to alicePublic, bobPrivate to bobPublic)) {
            val point = checkNotNull(WCurve.multiply(WCurve.generator, scalar(private)))
            assertContentEquals(hex(public), WCurve.montgomeryX(point).reversedArray())
        }
    }

    @Test
    fun sharedSecretMatchesRfc7748() {
        val bob = checkNotNull(WCurve.lift(uCoordinate(bobPublic), odd = false))
        val point = checkNotNull(WCurve.multiply(bob, scalar(alicePrivate)))
        assertContentEquals(hex(shared), WCurve.montgomeryX(point).reversedArray())
    }

    @Test
    fun theShortFormHasTheKnownParameters() {
        assertContentEquals(hex("2aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa984914a144"), WCurve.a.toBytes())
        assertContentEquals(hex("7b425ed097b425ed097b425ed097b425ed097b425ed097b4260b5e9c7710c864"), b.toBytes())
    }

    @Test
    fun theGeneratorHasThePrimeOrder() {
        assertNull(WCurve.multiply(WCurve.generator, WCurve.order))
        val lessOne = U256.of(0x5CF5D3ECL, 0x5812631AL, 0xA2F79CD6L, 0x14DEF9DEL, 0, 0, 0, 0x10000000L)
        assertNotNull(WCurve.multiply(WCurve.generator, lessOne))
    }

    @Test
    fun liftedPointsLieOnTheCurveWithTheAskedParity() {
        for (x in listOf(9L, 10L, 11L, 12L)) {
            for (odd in listOf(false, true)) {
                val point = WCurve.lift(Fe.of(x), odd) ?: continue
                assertEquals(point.x * point.x * point.x + WCurve.a * point.x + b, point.y * point.y)
                assertEquals(odd, point.y.isOdd)
            }
        }
    }

    @Test
    fun anXOffTheCurveIsNotLifted() {
        val halfOfPrime = U256.of(0xFFFFFFF6L, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0x3FFFFFFFL)
        val off = (1L..50L).map { Fe.of(it) }.filter { WCurve.lift(it, odd = false) == null }
        assertTrue(off.isNotEmpty())
        for (x in off) {
            val ySquared = x * x * x + montgomeryA * x * x + x
            assertEquals(-Fe.ONE, ySquared.power(halfOfPrime))
        }
    }
}
