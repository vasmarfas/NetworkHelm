package com.vasmarfas.networkhelm.app

import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.IOException

internal actual fun replaceFile(temporary: File, target: File) {
    try {
        Os.rename(temporary.path, target.path)
    } catch (e: ErrnoException) {
        throw IOException("could not replace the stored file", e)
    }
}
