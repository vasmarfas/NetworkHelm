package com.vasmarfas.networkhelm.ui.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlightTest {

    private fun tones(text: String, language: Language) = highlight(text, language).map { text.substring(it.start, it.end) to it.tone }

    @Test
    fun theLanguageIsToldByTheNameOfTheFile() {
        assertEquals(Language.RouterOs, languageOf("flash/export.rsc"))
        assertEquals(Language.Json, languageOf("skins/dark.JSON"))
        assertEquals(Language.Yaml, languageOf("a.yml"))
        assertEquals(Language.Config, languageOf("etc/config/network.conf"))
        assertEquals(Language.Config, languageOf("etc/config/network"))
        assertEquals(Language.Shell, languageOf("rc.local"))
        assertEquals(Language.Lua, languageOf("x.lua"))
        assertEquals(Language.Log, languageOf("tmp/messages.log"))
        assertEquals(Language.Plain, languageOf("readme.txt"))
    }

    @Test
    fun aRouterOsExportIsColouredByPathsCommandsNamesAndValues() {
        val text = "# by RouterOS\n/ip firewall filter\nadd action=accept chain=input comment=\"allow ssh\" dst-port=22\n:global x \$name 10.0.0.1/24"
        val found = tones(text, Language.RouterOs)
        assertTrue("# by RouterOS" to Tone.Comment in found, found.toString())
        assertTrue("/ip firewall filter" to Tone.Path in found, found.toString())
        assertTrue("add" to Tone.Keyword in found)
        assertTrue("action" to Tone.Name in found)
        assertTrue("\"allow ssh\"" to Tone.String in found)
        assertTrue("22" to Tone.Number in found)
        assertTrue(":global" to Tone.Keyword in found)
        assertTrue("\$name" to Tone.Variable in found)
        assertTrue("10.0.0.1/24" to Tone.Number in found)
    }

    @Test
    fun aCommandOnTheLineOfItsPathIsSeenApart() {
        val text = "/system script run test"
        val found = tones(text, Language.RouterOs)
        assertTrue("/system script run" to Tone.Path in found || "/system script" to Tone.Path in found, found.toString())
        val one = tones("/ip firewall filter add chain=input", Language.RouterOs)
        assertTrue("/ip firewall filter" to Tone.Path in one && "add" to Tone.Keyword in one, one.toString())
    }

    @Test
    fun jsonSeparatesNamesFromValues() {
        val text = "{\"name\": \"dark\", \"size\": 12, \"on\": true}"
        val found = tones(text, Language.Json)
        assertTrue("\"name\"" to Tone.Name in found)
        assertTrue("\"dark\"" to Tone.String in found)
        assertTrue("12" to Tone.Number in found)
        assertTrue("true" to Tone.Keyword in found)
    }

    @Test
    fun yamlConfigAndShellHaveTheirOwnWords() {
        assertTrue("name" to Tone.Name in tones("name: rd\n# note", Language.Yaml))
        assertTrue("# note" to Tone.Comment in tones("name: rd\n# note", Language.Yaml))
        val uci = tones("config interface 'lan'\n\toption proto 'static'", Language.Config)
        assertTrue("config" to Tone.Keyword in uci && "'lan'" to Tone.String in uci && "option" to Tone.Keyword in uci, uci.toString())
        val shell = tones("if [ -f \$FILE ]; then echo \"\${FILE}\"; fi", Language.Shell)
        assertTrue("if" to Tone.Keyword in shell && "\$FILE" to Tone.Variable in shell && "fi" to Tone.Keyword in shell, shell.toString())
        assertTrue("-- note" to Tone.Comment in tones("local a = 1 -- note", Language.Lua))
    }

    @Test
    fun aLogShowsItsTimeStampsAndTheWordsOfTrouble() {
        val text = "2026-10-27 09:12:01 system,info logged in\n2026-10-27 09:12:05 error login failure for user admin\n"
        val found = tones(text, Language.Log)
        assertTrue("2026-10-27 09:12:01" to Tone.Number in found, found.toString())
        assertTrue("error" to Tone.Error in found, found.toString())
    }

    @Test
    fun spansDoNotOverlapAndAnUnclosedStringEndsWithTheText() {
        for (language in Language.entries) {
            val spans = highlight("a \"b\n/ip x=1 \$c # d\n'e", language)
            for ((first, second) in spans.zipWithNext()) assertTrue(first.end <= second.start, "$language ${first.start}-${first.end} ${second.start}")
            assertTrue(spans.all { it.end <= 22 })
        }
    }

    @Test
    fun aVeryLongTextStaysPlain() {
        assertTrue(highlight("a".repeat(HIGHLIGHT_LIMIT + 1), Language.RouterOs).isEmpty())
    }
}
