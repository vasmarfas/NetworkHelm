package com.vasmarfas.networkhelm.ui.files

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RouterFilesTest {

    @Test
    fun theFilesComeDownAtTheFirstRequestOnly() {
        val asked = mutableListOf<String>()
        val files = RouterFiles(listOf("backup.rsc", "pub/notes.txt")) { name -> asked += name; "content of $name".encodeToByteArray() }
        assertTrue(asked.isEmpty(), "nothing comes down before the drop")
        assertTrue(files.isDataFlavorSupported(DataFlavor.javaFileListFlavor))

        val first = files.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
        val second = files.getTransferData(DataFlavor.javaFileListFlavor)
        assertEquals(listOf("backup.rsc", "notes.txt"), first.map { (it as File).name })
        assertEquals("content of pub/notes.txt", (first[1] as File).readText())
        assertSame(first, second)
        assertEquals(listOf("backup.rsc", "pub/notes.txt"), asked)
        assertFailsWith<UnsupportedFlavorException> { files.getTransferData(DataFlavor.stringFlavor) }
    }

    @Test
    fun aDragTakesAwayTheFoldersOfDragsAnHourOld() {
        val old = Files.createTempDirectory("networkhelm-drag").toFile()
        File(old, "old.rsc").writeText("old")
        old.setLastModified(System.currentTimeMillis() - 2 * 3_600_000)
        val recent = Files.createTempDirectory("networkhelm-drag").toFile()
        File(recent, "recent.rsc").writeText("recent")

        val dropped = RouterFiles(listOf("backup.rsc")) { "new".encodeToByteArray() }.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
        assertFalse(old.exists())
        assertTrue(File(recent, "recent.rsc").exists())
        assertEquals("new", (dropped.single() as File).readText())
        recent.deleteRecursively()
    }
}
