package com.vasmarfas.networkhelm.app

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StartupFailureTest {

    @Test
    fun aFailureAtTheStartLeavesItsStackTraceInTheLogFile() {
        val directory = Files.createTempDirectory("startup").toFile()
        try {
            val file = assertNotNull(reportStartupFailure(IllegalStateException("no window"), directory))
            assertEquals(directory, file.parentFile)
            val text = file.readText()
            assertContains(text, "IllegalStateException: no window")
            assertContains(text, "StartupFailureTest")
            assertContains(text, "Java ${System.getProperty("java.version")}")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aLinkUnderTheNameOfTheLogIsNotFollowed() {
        val directory = Files.createTempDirectory("startup").toFile()
        try {
            val target = File(directory, "target.txt").also { it.writeText("kept") }
            val link = File(directory, "NetworkHelm-startup-error.log")
            assumeTrue("links cannot be made here", runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isSuccess)
            assertNull(reportStartupFailure(IllegalStateException("no window"), directory))
            assertEquals("kept", target.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aDirectoryThatCannotBeWrittenGivesNoFileAndNoException() {
        val directory = Files.createTempDirectory("startup").toFile()
        try {
            assertNull(reportStartupFailure(IllegalStateException("no window"), File(directory, "missing/inside")))
        } finally {
            directory.deleteRecursively()
        }
    }
}
