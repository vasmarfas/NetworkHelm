package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class EcSrp5Server(private val user: String, password: String, private val salt: ByteArray) {
    private val verifier = passwordVerifier(user.substringBefore('+'), password, salt)
    private val verifierPoint = checkNotNull(WCurve.multiply(WCurve.generator, verifier))
    private val ephemeral = U256.fromBytes(secureRandomBytes(32))
    private val publicPoint = checkNotNull(WCurve.add(
        WCurve.multiply(WCurve.generator, ephemeral),
        passwordMask(WCurve.montgomeryX(verifierPoint), odd = false),
    ))
    private val publicX = WCurve.montgomeryX(publicPoint)
    private var transcript = ByteArray(0)
    private var shared = ByteArray(0)

    fun challenge(hello: ByteArray): ByteArray {
        val end = hello.indexOf(0)
        assertEquals(user, hello.decodeToString(0, end))
        val clientX = hello.copyOfRange(end + 1, end + 33)
        val clientKey = checkNotNull(WCurve.lift(Fe.fromBytes(clientX), hello[end + 33].toInt() != 0))
        transcript = sha256(clientX, publicX)
        val point = WCurve.add(clientKey, WCurve.multiply(verifierPoint, U256.fromBytes(transcript)))
        shared = WCurve.montgomeryX(checkNotNull(WCurve.multiply(checkNotNull(point), ephemeral)))
        return publicX + byteArrayOf(if (publicPoint.y.isOdd) 1 else 0) + salt
    }

    fun confirm(clientConfirmation: ByteArray): ByteArray? {
        val number = shared.withoutLeadingZeros()
        return if (clientConfirmation.contentEquals(sha256(transcript, number))) sha256(transcript, clientConfirmation, number) else null
    }

    fun keys() = streamKeys(sha256(shared.withoutLeadingZeros()), server = true)
}

internal class ShortEcSrp5Server(private val user: String, password: String, private val ephemeral: U256 = U256.fromBytes(secureRandomBytes(32))) {
    private val verifierPoint = checkNotNull(WCurve.multiply(WCurve.generator, passwordVerifier(user, password, ByteArray(0))))
    private val publicPoint = checkNotNull(WCurve.multiply(WCurve.generator, ephemeral))
    private val publicKey = WCurve.montgomeryX(publicPoint) + byteArrayOf(if (publicPoint.y.isOdd) 1 else 0)
    private var shared = ByteArray(0)
    private var clientKey = ByteArray(0)

    fun challenge(hello: ByteArray): ByteArray {
        val end = hello.indexOf(0)
        assertEquals(user, hello.decodeToString(0, end))
        clientKey = hello.copyOfRange(end + 1, hello.size)
        shared = WCurve.montgomeryX(checkNotNull(WCurve.multiply(verifierPoint, ephemeral))).withoutLeadingZeros()
        return publicKey
    }

    fun confirm(proof: ByteArray): ByteArray? =
        if (proof.contentEquals(labelled(shared + publicKey, "This is for peer challenge"))) labelled(shared + clientKey, "This is for auther challenge") else null

    fun keys() = streamKeys(shared, server = true)
}

class EcSrp5Test {
    private val salt = ByteArray(16) { (it * 3 + 1).toByte() }

    @Test
    fun theShortLoginIsProvedByBothSidesWithTheKeyOfTheOtherAndKeepsTheSecretWhole() {
        val client = EcSrp5Client("admin", "secret")
        val server = ShortEcSrp5Server("admin", "secret")
        val challenge = server.challenge(client.hello())
        assertEquals(33, challenge.size)
        val proof = client.confirm(challenge)
        assertEquals(16, proof.size)
        val answer = checkNotNull(server.confirm(proof))
        assertEquals(16, answer.size)
        assertTrue(client.verify(answer))
        val clientKeys = client.keys()
        val serverKeys = server.keys()
        assertContentEquals(clientKeys.send.aes, serverKeys.receive.aes)
        assertContentEquals(clientKeys.receive.mac, serverKeys.send.mac)
    }

    @Test
    fun theShortLoginHashesTheSharedXWithoutItsLeadingZeros() {
        val client = EcSrp5Client("admin", "secret")
        val server = ShortEcSrp5Server("admin", "secret", U256.of(90))
        val answer = checkNotNull(server.confirm(client.confirm(server.challenge(client.hello()))))
        assertTrue(client.verify(answer))
        assertContentEquals(client.keys().send.aes, server.keys().receive.aes)
    }

    @Test
    fun aWrongPasswordGivesNoShortConfirmation() {
        val client = EcSrp5Client("admin", "guess")
        val server = ShortEcSrp5Server("admin", "secret")
        assertEquals(null, server.confirm(client.confirm(server.challenge(client.hello()))))
        assertFalse(client.verify(ByteArray(16)))
    }

    @Test
    fun theProofsOfTheShortLoginAreSha1OfTheSecretAndAKeyBetweenFixedPadding() {
        val shared = ByteArray(32) { it.toByte() }
        val key = ByteArray(33) { (100 + it).toByte() }
        assertEquals("d1c656a07c9b6eb251d6ed0351a7d303", hexOf(labelled(shared + key, "This is for peer challenge")))
        assertEquals("0ef3555eb1aa466278d4f8171db9db52", hexOf(labelled(shared + key, "This is for auther challenge")))
    }

    private fun hexOf(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun clientAndRouterAgreeOnConfirmationsAndKeys() {
        val client = EcSrp5Client("admin", "secret")
        val server = EcSrp5Server("admin", "secret", salt)
        val confirmation = checkNotNull(server.confirm(client.confirm(server.challenge(client.hello()))))
        assertTrue(client.verify(confirmation))
        val clientKeys = client.keys()
        val serverKeys = server.keys()
        assertContentEquals(clientKeys.send.aes, serverKeys.receive.aes)
        assertContentEquals(clientKeys.receive.aes, serverKeys.send.aes)
        assertContentEquals(clientKeys.send.mac, serverKeys.receive.mac)
        assertContentEquals(clientKeys.receive.mac, serverKeys.send.mac)
    }

    @Test
    fun aLoginWrittenWithASuffixIsProvedForTheBareAccount() {
        val client = EcSrp5Client("admin+ct", "secret")
        val server = EcSrp5Server("admin+ct", "secret", salt)
        assertTrue(client.verify(checkNotNull(server.confirm(client.confirm(server.challenge(client.hello()))))))
        assertEquals("admin+ct", client.hello().decodeToString(0, 8))
    }

    @Test
    fun anEmptyPasswordWorksLikeAnyOther() {
        val client = EcSrp5Client("admin", "")
        val server = EcSrp5Server("admin", "", salt)
        assertTrue(client.verify(checkNotNull(server.confirm(client.confirm(server.challenge(client.hello()))))))
    }

    @Test
    fun aWrongPasswordGivesNoConfirmation() {
        val client = EcSrp5Client("admin", "guess")
        val server = EcSrp5Server("admin", "secret", salt)
        assertEquals(null, server.confirm(client.confirm(server.challenge(client.hello()))))
        assertFalse(client.verify(ByteArray(32)))
    }

    @Test
    fun theHelloNamesTheUserAndCarriesTheKey() {
        val hello = EcSrp5Client("router", "x").hello()
        assertEquals(6 + 1 + 32 + 1, hello.size)
        assertEquals("router", hello.decodeToString(0, 6))
        assertEquals(0, hello[6].toInt())
    }

    @Test
    fun aChallengeOfTheWrongSizeIsRefused() {
        for (size in listOf(0, 32, 48, 50)) {
            val error = assertFailsWith<DeviceException> { EcSrp5Client("admin", "x").confirm(ByteArray(size)) }
            assertEquals(ErrorCategory.Unsupported, error.category, "size $size")
        }
    }

    @Test
    fun keysOfTheTwoDirectionsDiffer() {
        val keys = streamKeys(ByteArray(32) { it.toByte() }, server = false)
        assertEquals(16, keys.send.aes.size)
        assertEquals(20, keys.send.mac.size)
        assertFalse(keys.send.aes.contentEquals(keys.receive.aes))
        assertFalse(keys.send.mac.contentEquals(keys.receive.mac))
    }
}
