package com.vasmarfas.networkhelm.ui.files

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual val folderDialogs = true

internal actual suspend fun pickFolderDialog(): LocalFolder? {
    val directory = FileKit.openDirectoryPicker()?.file ?: return null
    val files = directory.listFiles { file -> file.isFile }.orEmpty().sortedBy { it.name }
        .map { file -> LocalFile(file.name) { withContext(Dispatchers.IO) { file.readBytes() } } }
    return LocalFolder(directory.name, files) { name, bytes -> withContext(Dispatchers.IO) { File(directory, name).writeBytes(bytes) } }
}
