package com.vasmarfas.networkhelm.ui.log

import com.vasmarfas.networkhelm.ui.table.TableLayout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogLayoutTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theViewOfALogSurvivesTheFileOfTheLayouts() {
        val text = json.encodeToString(TableLayout.serializer(), TableLayout(log = LogLayout(TimeStyle.Relative, wrap = true, collapse = true, onlyMatches = false, pane = 280f, panel = true)))
        val layout = json.decodeFromString(TableLayout.serializer(), text).log
        assertEquals(TimeStyle.Relative, layout?.times)
        assertEquals(listOf(true, true, false, 280f, true), listOf(layout?.wrap, layout?.collapse, layout?.onlyMatches, layout?.pane, layout?.panel))
    }

    @Test
    fun aLayoutWrittenBeforeThePanelHadItsButtonReadsInAWindow() {
        val layout = json.decodeFromString(TableLayout.serializer(), """{"log": {"times": "Full", "wrap": true}}""").log
        assertEquals(false, layout?.panel)
    }

    @Test
    fun aLayoutOfAnOlderBuildHasNoViewOfALog() {
        assertNull(json.decodeFromString(TableLayout.serializer(), """{"columns": ["time"], "detail": true}""").log)
    }

    @Test
    fun aStateTakesBackWhatItWasAskedToKeep() {
        val state = LogState()
        state.restore(LogLayout(TimeStyle.Full, wrap = true, collapse = false, onlyMatches = false, pane = 300f, panel = true))
        val layout = state.layout(300f)
        assertEquals(listOf(TimeStyle.Full, true, false, false, true), listOf(layout.times, layout.wrap, layout.collapse, layout.onlyMatches, layout.panel))
    }
}
