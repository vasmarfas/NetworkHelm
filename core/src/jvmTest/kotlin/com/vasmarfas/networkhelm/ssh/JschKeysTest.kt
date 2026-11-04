package com.vasmarfas.networkhelm.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JschKeysTest {

    @Test
    fun theKeyFilesOfJschAreToldApart() {
        val pair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)
        val clear = ByteArrayOutputStream().also { pair.writePrivateKey(it) }.toByteArray()
        val locked = ByteArrayOutputStream().also { pair.writePrivateKey(it, "rd-test".encodeToByteArray()) }.toByteArray()
        assertFalse(sshKeyEncrypted(clear))
        assertTrue(sshKeyEncrypted(locked))
        assertTrue(KeyPair.load(JSch(), locked, null).decrypt("rd-test".encodeToByteArray()), "JSch opens it with the passphrase")
    }
}
