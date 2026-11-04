package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun Clipboard.readText(): String? = getClipEntry()?.getPlainText()

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun Clipboard.writeText(text: String) = setClipEntry(ClipEntry.withPlainText(text))
