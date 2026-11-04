package com.vasmarfas.networkhelm.miknative

import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class BigPoint(val x: BigInteger, val y: BigInteger)

private object BigCurve {
    val prime: BigInteger = BigInteger.ONE.shiftLeft(255) - BigInteger.valueOf(19)
    val order: BigInteger = BigInteger.ONE.shiftLeft(252) + BigInteger("27742317777372353535851937790883648493")

    private val montgomeryA = BigInteger.valueOf(486662)
    private val two = BigInteger.valueOf(2)
    private val three = BigInteger.valueOf(3)
    private val shift = montgomeryA * three.modInverse(prime) % prime
    private val squareRootOfMinusOne = two.modPow((prime - BigInteger.ONE).shiftRight(2), prime)

    val a: BigInteger = (BigInteger.ONE - montgomeryA * montgomeryA * three.modInverse(prime)).mod(prime)

    val generator: BigPoint = checkNotNull(lift(BigInteger.valueOf(9), odd = false))

    fun lift(montgomeryX: BigInteger, odd: Boolean): BigPoint? {
        val x = montgomeryX.mod(prime)
        val ySquared = (x.pow(3) + montgomeryA * x * x + x).mod(prime)
        val root = squareRoot(ySquared) ?: return null
        val y = if (root.testBit(0) == odd) root else prime - root
        return BigPoint((x + shift).mod(prime), y)
    }

    fun montgomeryX(point: BigPoint): ByteArray = (point.x - shift).mod(prime).toFixedBytes()

    fun add(left: BigPoint?, right: BigPoint?): BigPoint? {
        if (left == null) return right
        if (right == null) return left
        val slope = if (left.x == right.x) {
            if ((left.y + right.y).mod(prime).signum() == 0) return null
            (three * left.x * left.x + a) * (two * left.y).modInverse(prime)
        } else {
            (right.y - left.y) * (right.x - left.x).mod(prime).modInverse(prime)
        }.mod(prime)
        val x = (slope * slope - left.x - right.x).mod(prime)
        return BigPoint(x, (slope * (left.x - x) - left.y).mod(prime))
    }

    fun multiply(point: BigPoint, scalar: BigInteger): BigPoint? {
        var result: BigPoint? = null
        for (bit in scalar.bitLength() - 1 downTo 0) {
            result = add(result, result)
            if (scalar.testBit(bit)) result = add(result, point)
        }
        return result
    }

    private fun squareRoot(value: BigInteger): BigInteger? {
        val candidate = value.modPow((prime + three).shiftRight(3), prime)
        for (root in listOf(candidate, candidate * squareRootOfMinusOne % prime)) {
            if (root * root % prime == value) return root
        }
        return null
    }
}

private fun BigInteger.toFixedBytes(size: Int = 32): ByteArray {
    val bytes = toByteArray()
    return when {
        bytes.size == size -> bytes
        bytes.size > size -> bytes.copyOfRange(bytes.size - size, bytes.size)
        else -> ByteArray(size - bytes.size) + bytes
    }
}

private class BigServer(private val user: String, password: String, private val salt: ByteArray) {
    private val verifier = BigInteger(1, sha256(salt, sha256("$user:$password".encodeToByteArray())))
    private val verifierPoint = checkNotNull(BigCurve.multiply(BigCurve.generator, verifier))
    private val ephemeral = BigInteger(1, secureRandomBytes(32))
    private val publicPoint = checkNotNull(
        BigCurve.add(BigCurve.multiply(BigCurve.generator, ephemeral), mask(BigCurve.montgomeryX(verifierPoint), odd = false)),
    )
    private val publicX = BigCurve.montgomeryX(publicPoint)
    private var transcript = ByteArray(0)
    private var shared = ByteArray(0)

    fun challenge(hello: ByteArray): ByteArray {
        val end = hello.indexOf(0)
        assertEquals(user, hello.decodeToString(0, end))
        val clientX = hello.copyOfRange(end + 1, end + 33)
        val clientKey = checkNotNull(BigCurve.lift(BigInteger(1, clientX), hello[end + 33].toInt() != 0))
        transcript = sha256(clientX, publicX)
        val point = BigCurve.add(clientKey, BigCurve.multiply(verifierPoint, BigInteger(1, transcript)))
        shared = BigCurve.montgomeryX(checkNotNull(BigCurve.multiply(checkNotNull(point), ephemeral)))
        return publicX + byteArrayOf(if (publicPoint.y.testBit(0)) 1 else 0) + salt
    }

    fun confirm(clientConfirmation: ByteArray): ByteArray? {
        val number = shared.withoutLeadingZeros()
        return if (clientConfirmation.contentEquals(sha256(transcript, number))) sha256(transcript, clientConfirmation, number) else null
    }

    fun keys() = streamKeys(sha256(shared.withoutLeadingZeros()), server = true)

    private fun mask(verifierX: ByteArray, odd: Boolean): BigPoint {
        var seed = sha256(verifierX)
        while (true) {
            BigCurve.lift(BigInteger(1, sha256(seed)), odd)?.let { return it }
            seed = (BigInteger(1, seed) + BigInteger.ONE).toFixedBytes()
        }
    }
}

class CurveCrossCheckTest {
    private val random = Random(8291)

    private fun number() = BigInteger(1, random.nextBytes(32))

    @Test
    fun fieldArithmeticMatchesBigInteger() {
        val prime = BigCurve.prime
        repeat(300) {
            val a = number()
            val b = number()
            val x = Fe.fromBytes(a.toFixedBytes())
            val y = Fe.fromBytes(b.toFixedBytes())
            assertContentEquals(a.mod(prime).toFixedBytes(), x.toBytes())
            assertContentEquals((a + b).mod(prime).toFixedBytes(), (x + y).toBytes())
            assertContentEquals((a - b).mod(prime).toFixedBytes(), (x - y).toBytes())
            assertContentEquals((a * b).mod(prime).toFixedBytes(), (x * y).toBytes())
            assertContentEquals(a.mod(prime).modInverse(prime).toFixedBytes(), x.inverse().toBytes())
            val root = (x * x).squareRoot()
            assertTrue(root != null && root.square() == x * x)
        }
    }

    @Test
    fun aProductPlusANumberModuloTheOrderMatchesBigInteger() {
        repeat(300) {
            val a = number()
            val b = number()
            val c = number()
            val result = mulAddMod(U256.fromBytes(a.toFixedBytes()), U256.fromBytes(b.toFixedBytes()), U256.fromBytes(c.toFixedBytes()), WCurve.order)
            assertEquals(U256.fromBytes((a * b + c).mod(BigCurve.order).toFixedBytes()), result)
        }
    }

    @Test
    fun liftingAnXMatchesBigInteger() {
        repeat(100) {
            val x = number()
            for (odd in listOf(false, true)) {
                val expected = BigCurve.lift(x, odd)
                val actual = WCurve.lift(Fe.fromBytes(x.toFixedBytes()), odd)
                if (expected == null) {
                    assertNull(actual)
                } else {
                    assertContentEquals(expected.x.toFixedBytes(), checkNotNull(actual).x.toBytes())
                    assertContentEquals(expected.y.toFixedBytes(), actual.y.toBytes())
                }
            }
        }
    }

    @Test
    fun multiplyingAPointMatchesBigInteger() {
        repeat(8) {
            val scalar = number()
            val expected = checkNotNull(BigCurve.multiply(BigCurve.generator, scalar))
            val actual = checkNotNull(WCurve.multiply(WCurve.generator, U256.fromBytes(scalar.toFixedBytes())))
            assertContentEquals(BigCurve.montgomeryX(expected), WCurve.montgomeryX(actual))
            assertEquals(expected.y.testBit(0), actual.y.isOdd)
        }
    }

    @Test
    fun theClientLogsInToARouterWrittenOnBigInteger() {
        val salt = ByteArray(16) { (it * 5 + 3).toByte() }
        for (password in listOf("secret", "", "pässwörd with spaces")) {
            val client = EcSrp5Client("admin", password)
            val server = BigServer("admin", password, salt)
            val confirmation = checkNotNull(server.confirm(client.confirm(server.challenge(client.hello()))))
            assertTrue(client.verify(confirmation))
            val clientKeys = client.keys()
            val serverKeys = server.keys()
            assertContentEquals(clientKeys.send.aes, serverKeys.receive.aes)
            assertContentEquals(clientKeys.receive.mac, serverKeys.send.mac)
        }
    }
}
