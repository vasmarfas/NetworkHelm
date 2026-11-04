package com.vasmarfas.networkhelm.ui.files

import com.vasmarfas.networkhelm.ui.table.SortKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileTreeTest {

    private fun entry(path: String, type: String = "", size: Long? = null) = Entry(path, path, holdsFiles(type), size, "", type)

    private val entries = listOf(
        entry("flash", "disk"),
        entry("flash/skins", "directory"),
        entry("flash/skins/dark.json", ".json file", 2000),
        entry("flash/skins/light.json", ".json file", 1000),
        entry("flash/export.rsc", "script", 500),
        entry("usb1", "read-only mountpoint"),
        entry("usb1/photo.png", ".png file", 100),
        entry("rc.local", ".local file", 127),
        entry("home/user/notes.txt", "file", 12),
    )
    private val tree = FileTree(entries)

    private fun paths(rows: List<TreeRow>) = rows.map { it.entry.path }

    @Test
    fun everythingIsClosedAtFirstAndFoldersComeBeforeFiles() {
        val rows = tree.rows("", emptySet(), null)
        assertEquals(listOf("flash", "home", "usb1", "rc.local"), paths(rows))
        assertTrue(rows.all { it.depth == 0 && !it.open })
    }

    @Test
    fun aFolderCountsWhatLiesInItAtAllLevelsAndWeighsTheFiles() {
        val rows = tree.rows("", emptySet(), null).associateBy { it.entry.path }
        assertEquals(4, rows.getValue("flash").count)
        assertEquals(3500L, rows.getValue("flash").size)
        assertEquals(2, rows.getValue("home").count)
        assertEquals(12L, rows.getValue("home").size)
        assertEquals(1, rows.getValue("usb1").count)
        assertNull(rows.getValue("rc.local").count)
        assertEquals(11, tree.countIn(""))
        assertEquals(4, tree.countIn("flash"))
    }

    @Test
    fun theFoldersTheNamesImplyAreMadeWithoutAnId() {
        val home = checkNotNull(tree.entry("home"))
        assertTrue(home.directory)
        assertNull(home.id)
        assertTrue(checkNotNull(tree.entry("home/user")).directory)
        assertEquals(setOf("flash", "flash/skins", "usb1", "home", "home/user"), tree.folders)
    }

    @Test
    fun anOpenFolderShowsItsLevelBelowItAndNamesStandOneStepDeeper() {
        val rows = tree.rows("", setOf("flash"), null)
        assertEquals(listOf("flash", "flash/skins", "flash/export.rsc", "home", "usb1", "rc.local"), paths(rows))
        assertEquals(listOf(0, 1, 1, 0, 0, 0), rows.map { it.depth })
        assertTrue(rows[0].open)
        assertTrue(!rows[1].open)
    }

    @Test
    fun threeLevelsOpenOneInsideAnother() {
        val rows = tree.rows("", setOf("flash", "flash/skins"), null)
        assertEquals(listOf("flash", "flash/skins", "flash/skins/dark.json", "flash/skins/light.json", "flash/export.rsc", "home", "usb1", "rc.local"), paths(rows))
        assertEquals(2, rows.first { it.entry.name == "dark.json" }.depth)
    }

    @Test
    fun aFolderOpenInsideAClosedOneStaysHiddenUntilTheOuterOneOpens() {
        assertEquals(listOf("flash", "home", "usb1", "rc.local"), paths(tree.rows("", setOf("flash/skins"), null)))
    }

    @Test
    fun aFolderShownAloneStartsItsRowsAtTheTopLevel() {
        val rows = tree.rows("flash", emptySet(), null)
        assertEquals(listOf("flash/skins", "flash/export.rsc"), paths(rows))
        assertTrue(rows.all { it.depth == 0 })
    }

    @Test
    fun theSortOrdersEachLevelAndKeepsFoldersFirstEitherWay() {
        val opened = setOf("flash", "flash/skins")
        val up = tree.rows("", opened, SortKey("size", true))
        assertEquals(listOf("home", "usb1", "flash"), paths(up).filter { '/' !in it && it != "rc.local" })
        assertEquals(listOf("flash/skins/light.json", "flash/skins/dark.json"), paths(up).filter { it.startsWith("flash/skins/") })
        val down = tree.rows("", opened, SortKey("size", false))
        assertEquals(listOf("flash", "usb1", "home", "rc.local"), paths(down).filter { '/' !in it })
        assertEquals(listOf("flash/skins/dark.json", "flash/skins/light.json"), paths(down).filter { it.startsWith("flash/skins/") })
        val byName = tree.rows("", setOf("flash"), SortKey("name", false))
        assertEquals(listOf("flash/skins", "flash/export.rsc"), paths(byName).filter { it.startsWith("flash/") })
    }

    @Test
    fun aFilterKeepsWhatMatchesAndTheWayToItAllOpen() {
        val rows = tree.rows("", emptySet(), null, "dark")
        assertEquals(listOf("flash", "flash/skins", "flash/skins/dark.json"), paths(rows))
        assertTrue(rows[0].open && rows[1].open)
        val folder = tree.rows("", emptySet(), null, "skins")
        assertEquals(listOf("flash", "flash/skins"), paths(folder))
        assertTrue(folder[0].open)
        assertTrue(!folder[1].open)
        assertEquals(emptyList(), paths(tree.rows("", emptySet(), null, "nothing like it")))
    }

    @Test
    fun whenOnlyALevelIsKnownNoFolderTellsWhatItHoldsInAll() {
        val rows = FileTree(entries, partial = true).rows("", setOf("flash"), null)
        assertTrue(rows.filter { it.entry.directory }.all { it.count == null && it.size == null })
    }

    @Test
    fun foldersBelowAreCollectedAtAllLevels() {
        assertEquals(listOf("flash/skins"), tree.foldersBelow("flash"))
        assertEquals(listOf("home/user"), tree.foldersBelow("home"))
        assertEquals(emptyList(), tree.foldersBelow("usb1"))
    }

    @Test
    fun aListThatChangesKeepsTheFoldersOpen() {
        val open = setOf("flash", "flash/skins")
        val before = FileTree(entries).rows("", open, null)
        val after = FileTree(entries + entry("flash/skins/new.json", ".json file", 5) + entry("rc.local.bak", "file", 1)).rows("", open, null)
        assertTrue(after.first { it.entry.path == "flash" }.open && after.first { it.entry.path == "flash/skins" }.open)
        assertTrue(paths(after).containsAll(paths(before)))
        assertTrue("flash/skins/new.json" in paths(after))
    }

    @Test
    fun numbersInNamesReadAsNumbers() {
        val list = listOf("file10.txt", "file2.txt", "zdir", "Adir").map { entry(it, if (it.endsWith("dir")) "directory" else "") }
        assertEquals(listOf("Adir", "zdir", "file2.txt", "file10.txt"), paths(FileTree(list).rows("", emptySet(), null)))
    }
}
