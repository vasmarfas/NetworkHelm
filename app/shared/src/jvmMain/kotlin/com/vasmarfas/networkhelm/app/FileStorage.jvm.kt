package com.vasmarfas.networkhelm.app

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal actual fun replaceFile(temporary: File, target: File) {
    Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
}
