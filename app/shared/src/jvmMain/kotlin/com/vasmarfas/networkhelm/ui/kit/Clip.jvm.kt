package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun Clipboard.readText(): String? =
    getClipEntry()?.asAwtTransferable?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }?.getTransferData(DataFlavor.stringFlavor) as? String

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun Clipboard.writeText(text: String) = setClipEntry(ClipEntry(StringSelection(text)))
