package com.vasmarfas.networkhelm.ui.files

import com.vasmarfas.networkhelm.model.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileKindsTest {

    @Test
    fun aFileIsKnownByItsNameAndTheTypeTheRouterGives() {
        assertEquals(FileKind.Folder, kindOf("flash/skins", "directory"))
        assertEquals(FileKind.Disk, kindOf("disk-s-10724519", "disk"))
        assertEquals(FileKind.Mount, kindOf("usb1", "read-only mountpoint"))
        assertEquals(FileKind.Script, kindOf("flash/backup.rsc", "script"))
        assertEquals(FileKind.Package, kindOf("routeros-arm64-7.24.npk", "package"))
        assertEquals(FileKind.Backup, kindOf("rd-test.backup", "backup"))
        assertEquals(FileKind.Image, kindOf("PHOTO.JPG"))
        assertEquals(FileKind.Log, kindOf("tmp/messages.log"))
        assertEquals(FileKind.Config, kindOf("etc/config/network"))
        assertEquals(FileKind.Other, kindOf("rc.local.d/x.bin"))
        assertEquals("rsc", extensionOf("a/b/c.RSC"))
    }

    @Test
    fun aDiskAndAMountPointHoldFilesAsAFolderDoes() {
        assertTrue(holdsFiles("directory"))
        assertTrue(holdsFiles("disk"))
        assertTrue(holdsFiles("read-only mountpoint"))
        assertTrue(holdsFiles("mountpoint"))
        assertFalse(holdsFiles("script"))
        assertFalse(holdsFiles(""))
        assertTrue(checkNotNull(entryOf(Item("*1", mapOf("name" to "usb1", "type" to "read-only mountpoint")))).directory)
    }

    @Test
    fun rowsBecomeEntriesWithTheTimeThatTheRouterHas() {
        val row = Item("*7", mapOf("name" to "flash/skins/dark.json", "type" to ".json file", "size" to "1234", "creation-time" to "2026-10-27 09:12:01"))
        val entry = checkNotNull(entryOf(row))
        assertEquals("dark.json", entry.name)
        assertEquals("flash/skins", entry.parent)
        assertEquals(1234L, entry.size)
        assertEquals("2026-10-27 09:12:01", entry.modified)
        assertEquals(FileKind.Config, entry.kind)
        assertNull(entryOf(Item("*8", mapOf("type" to "directory"))))
    }

    @Test
    fun aTimeThatIsNoDateIsLeftOut() {
        assertEquals("2026-10-27 09:12:01", cleanTime("2026-10-27 09:12:01"))
        assertEquals("2026-10-27 09:12:01", cleanTime("oct/27/2026 09:12:01"))
        assertEquals("", cleanTime("3h23s"))
        assertEquals("", cleanTime("1w2d3h4m5s"))
        assertEquals("", cleanTime("1970-01-01 03:00:05"))
        assertEquals("", cleanTime("jan/01/1970 03:00:05"))
        assertEquals("", cleanTime(""))
        val folder = checkNotNull(entryOf(Item("*9", mapOf("name" to "skins", "type" to "directory", "last-modified" to "3h23s"))))
        assertEquals("", folder.modified)
    }

    @Test
    fun breadcrumbsGoFromTheRootToTheFolder() {
        assertEquals(listOf("" to ""), crumbs(""))
        assertEquals(listOf("" to "", "flash" to "flash", "skins" to "flash/skins"), crumbs("flash/skins"))
    }

    @Test
    fun sizesReadInTheUnitsOfTheRouter() {
        assertEquals("", sizeText(null))
        assertEquals("12 B", sizeText(12))
        assertEquals("1024 B", sizeText(1024))
        assertEquals("5.0 KiB", sizeText(5 * 1024L))
        assertEquals("2048.0 KiB", sizeText(2 * 1024 * 1024L))
    }
}
