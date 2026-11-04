package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.ui.platform.Clipboard

// reading may throw where the system refuses it, as a browser without the permission does
internal expect suspend fun Clipboard.readText(): String?

internal expect suspend fun Clipboard.writeText(text: String)
