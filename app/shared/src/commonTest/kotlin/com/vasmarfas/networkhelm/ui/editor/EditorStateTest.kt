package com.vasmarfas.networkhelm.ui.editor

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorStateTest {

    private fun editor() = EditorState(checkNotNull(decodeText("original\n".encodeToByteArray())), languageOf("a.txt"))

    @Test
    fun savingAnEarlierSnapshotKeepsLaterTextModified() {
        val editor = editor()
        editor.replaceEverything("sent\n")
        val snapshot = editor.saveSnapshot()
        editor.replaceEverything("sent and edited\n")
        editor.markSaved(snapshot)
        assertContentEquals("sent\n".encodeToByteArray(), snapshot.bytes)
        assertTrue(editor.dirty)
        editor.replaceEverything("sent\n")
        assertFalse(editor.dirty)
    }

    @Test
    fun changingOnlyEncodingOrLineEndsMakesTheDocumentModified() {
        val editor = editor()
        assertFalse(editor.dirty)
        editor.cp1251 = true
        assertTrue(editor.dirty)
        editor.cp1251 = false
        assertFalse(editor.dirty)
        editor.ending = LineEnding.CrLf
        assertTrue(editor.dirty)
        editor.markSaved(editor.saveSnapshot())
        assertFalse(editor.dirty)
        editor.ending = LineEnding.Lf
        assertTrue(editor.dirty)
    }

    @Test
    fun savingAnEarlierFormatKeepsLaterFormatChangesModified() {
        val editor = editor()
        val snapshot = editor.saveSnapshot()
        editor.ending = LineEnding.CrLf
        editor.cp1251 = true
        editor.markSaved(snapshot)
        assertTrue(editor.dirty)
        assertContentEquals("original\n".encodeToByteArray(), snapshot.bytes)
    }
}
