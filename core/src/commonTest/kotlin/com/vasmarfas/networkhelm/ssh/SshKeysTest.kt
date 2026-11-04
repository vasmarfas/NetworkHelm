package com.vasmarfas.networkhelm.ssh

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class SshKeysTest {

    private fun armoured(type: String, body: String) = "-----BEGIN $type-----\n$body\n-----END $type-----\n"

    private fun openSsh(cipher: String): String {
        val magic = "openssh-key-v1".encodeToByteArray() + 0
        val name = cipher.encodeToByteArray()
        val length = byteArrayOf(0, 0, 0, name.size.toByte())
        return armoured("OPENSSH PRIVATE KEY", Base64.encode(magic + length + name + "rd-test".encodeToByteArray()).chunked(70).joinToString("\n"))
    }

    @Test
    fun anEncryptedFileIsToldByItsFormat() {
        val locked = listOf(
            openSsh("aes256-ctr"),
            armoured("RSA PRIVATE KEY", "Proc-Type: 4,ENCRYPTED\nDEK-Info: AES-128-CBC,00\n\nrd-test"),
            armoured("ENCRYPTED PRIVATE KEY", "rd-test"),
            "PuTTY-User-Key-File-3: ssh-ed25519\nEncryption: aes256-cbc\nComment: rd-test\n",
        )
        assertEquals(locked, locked.filter { sshKeyEncrypted(it.encodeToByteArray()) })
        val clear = listOf(
            openSsh("none"),
            armoured("RSA PRIVATE KEY", "rd-test"),
            "PuTTY-User-Key-File-3: ssh-ed25519\nEncryption: none\nComment: rd-test\n",
            armoured("OPENSSH PRIVATE KEY", "%%%"),
            "rd-test",
        )
        assertEquals(emptyList(), clear.filter { sshKeyEncrypted(it.encodeToByteArray()) })
    }
}
