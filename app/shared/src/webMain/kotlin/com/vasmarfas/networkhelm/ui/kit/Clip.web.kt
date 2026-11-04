@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlinx.browser.window
import kotlinx.coroutines.await

// ClipEntry wraps browser blobs, so the text is read from navigator.clipboard directly
internal actual suspend fun Clipboard.readText(): String? = try {
    window.navigator.clipboard.readText().await<JsString>().toString()
} catch (e: JsException) {
    throw IllegalStateException(e.message, e)
}

internal actual suspend fun Clipboard.writeText(text: String) = setClipEntry(ClipEntry.withPlainText(text))
