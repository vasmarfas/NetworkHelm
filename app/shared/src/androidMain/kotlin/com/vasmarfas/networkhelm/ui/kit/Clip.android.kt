package com.vasmarfas.networkhelm.ui.kit

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

internal actual suspend fun Clipboard.readText(): String? = getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

internal actual suspend fun Clipboard.writeText(text: String) = setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
