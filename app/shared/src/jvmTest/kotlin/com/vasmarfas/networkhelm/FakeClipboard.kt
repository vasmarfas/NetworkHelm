package com.vasmarfas.networkhelm

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

class FakeClipboard : Clipboard {
    var entry: ClipEntry? = null

    override suspend fun getClipEntry() = entry

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        entry = clipEntry
    }
}
