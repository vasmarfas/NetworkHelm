package com.vasmarfas.networkhelm.offline

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArchiveTest {

    @Test
    fun expandedBytesAndTarEntriesHaveSeparateLimits() {
        for (level in listOf(0, 1, 6, 9)) {
            assertFailsWith<IllegalArgumentException> { gunzip(gzip(ByteArray(20), level), maxBytes = 19) }
            assertContentEquals(ByteArray(20), gunzip(gzip(ByteArray(20), level), maxBytes = 20))
        }
        val files = tar("one" to "abc", "two" to "def")
        assertFailsWith<IllegalArgumentException> { untar(files, maxBytes = 5) }
        assertFailsWith<IllegalArgumentException> { untar(files, maxEntries = 1) }
        assertEquals(2, untar(files, maxBytes = 6, maxEntries = 2).size)
        assertFailsWith<IllegalArgumentException> { untar(tar("one" to "", "one" to ""), maxEntries = 1) }
    }

    private fun gzip(data: ByteArray, level: Int = 6): ByteArray {
        val out = ByteArrayOutputStream()
        object : GZIPOutputStream(out) {
            init {
                def.setLevel(level)
            }
        }.use { it.write(data) }
        return out.toByteArray()
    }

    private fun tar(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, text) in files) {
            val content = text.encodeToByteArray()
            val header = ByteArray(512)
            name.encodeToByteArray().copyInto(header)
            content.size.toString(8).padStart(11, '0').encodeToByteArray().copyInto(header, 124)
            header[156] = '0'.code.toByte()
            "ustar".encodeToByteArray().copyInto(header, 257)
            out.write(header)
            out.write(content)
            out.write(ByteArray((512 - content.size % 512) % 512))
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    @Test
    fun gzipOfTheJvmInflatesBack() {
        val random = Random(1)
        val text = (1..2000).joinToString("\n") { "config rule ${random.nextInt(50)}\n\toption name 'rd-test-$it'" }.encodeToByteArray()
        val noise = ByteArray(70_000).also(random::nextBytes)
        for (data in listOf(ByteArray(0), "a".encodeToByteArray(), text, noise)) {
            for (level in listOf(0, 1, 6, 9)) assertContentEquals(data, gunzip(gzip(data, level)), "level $level, ${data.size} bytes")
        }
    }

    @Test
    fun theConfigsOfABackupComeAsUciExport() {
        val backup = gzip(
            tar(
                "etc/config/network" to "config interface 'lan'\n\toption proto 'static'\n",
                "etc/shadow" to "root:rd-test-secret",
                "etc/config/system" to "config system\n\toption hostname 'rd-test'\n",
            ),
        )
        val text = backupExport(backup)
        assertEquals("package network\n\nconfig interface 'lan'\n\toption proto 'static'\n\npackage system\n\nconfig system\n\toption hostname 'rd-test'\n", text)
        assertFalse("rd-test-secret" in text)
        assertEquals("rd-test", assertNotNull(openConfig("backup.tar.gz", backup)).device.identity)
        assertNull(openConfig("backup.tar.gz", backup.copyOf(backup.size / 2)))
    }
}
