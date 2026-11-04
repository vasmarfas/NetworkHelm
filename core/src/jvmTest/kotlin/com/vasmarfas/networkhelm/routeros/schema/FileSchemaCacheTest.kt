package com.vasmarfas.networkhelm.routeros.schema

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSchemaCacheTest {

    @Test
    fun answersSurviveAndAnotherBuildStartsEmpty() = runTest {
        val directory = Files.createTempDirectory("rd-test-schema").toFile()
        val build = "7.25.1 x86_64 container,routeros"
        val answers = mapOf("child:ip,dns" to listOf(mapOf("type" to "self", "node-type" to "dir", "name" to "dns")))
        FileSchemaCache(directory).store(build, answers)
        assertEquals(answers, FileSchemaCache(directory).load(build))
        assertEquals(emptyMap(), FileSchemaCache(directory).load("7.25 x86_64 container,routeros"))

        FileSchemaCache(directory).store(build, answers + ("child:ip" to emptyList()))
        assertEquals(setOf("child:ip,dns", "child:ip"), FileSchemaCache(directory).load(build).keys)
        assertEquals(1, directory.listFiles().orEmpty().size)
    }

    @Test
    fun aFileThatCannotBeReadIsNoAnswer() = runTest {
        val directory = Files.createTempDirectory("rd-test-schema").toFile()
        val build = "7.25.1 x86_64 container,routeros"
        val answers = mapOf("child:ip,dns" to listOf(mapOf("type" to "self")))
        FileSchemaCache(directory).store(build, answers)
        directory.listFiles().orEmpty().single().writeText("{\"build\": ")
        assertEquals(emptyMap(), FileSchemaCache(directory).load(build))
        FileSchemaCache(directory).store(build, answers)
        assertEquals(answers, FileSchemaCache(directory).load(build))
    }

    @Test
    fun onlyTheNewestBuildsStay() = runTest {
        val directory = Files.createTempDirectory("rd-test-schema").toFile()
        val cache = FileSchemaCache(directory)
        repeat(30) { cache.store("7.$it x86_64 routeros", mapOf("child:ip" to emptyList())) }
        assertEquals(24, directory.listFiles().orEmpty().size)
    }

    @Test
    fun forgettingABuildLeavesTheOthers() = runTest {
        val directory = Files.createTempDirectory("rd-test-schema").toFile()
        val cache = FileSchemaCache(directory)
        val answers = mapOf("child:ip" to emptyList<Map<String, String>>())
        cache.store("A1B2 7.26 x86_64 routeros", answers)
        cache.store("C3D4 7.26 x86_64 routeros", answers)
        cache.forget("A1B2 7.26 x86_64 routeros")
        assertEquals(emptyMap(), cache.load("A1B2 7.26 x86_64 routeros"))
        assertEquals(answers, cache.load("C3D4 7.26 x86_64 routeros"))
    }
}
