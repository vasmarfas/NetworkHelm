package com.vasmarfas.networkhelm.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaletteTest {

    private fun contrast(first: Color, second: Color): Float {
        val (light, dark) = listOf(first.luminance(), second.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun theDarkLookOfLegacyKeepsItsTextAndItsColoursReadable() {
        val p = LegacyDarkPalette
        val minimums = mapOf(
            "text on fields" to Triple(p.text, p.surface, 7f),
            "text on faces" to Triple(p.text, p.raised, 7f),
            "text on the chosen" to Triple(p.text, p.selection, 4.5f),
            "muted on faces" to Triple(p.muted, p.raised, 4.5f),
            "muted on fields" to Triple(p.muted, p.surface, 4.5f),
            "faint on fields" to Triple(p.faint, p.surface, 4.5f),
            "faint on faces" to Triple(p.faint, p.raised, 3.5f),
            "links on faces" to Triple(p.accent, p.raised, 4.5f),
            "links on fields" to Triple(p.accent, p.surface, 4.5f),
            "errors on fields" to Triple(p.danger, p.surface, 4.5f),
            "errors on faces" to Triple(p.danger, p.raised, 4f),
            "success on fields" to Triple(p.success, p.surface, 4.5f),
            "warnings on fields" to Triple(p.warning, p.surface, 4.5f),
            "the active title" to Triple(p.onAccent, p.title ?: p.accent, 4.5f),
            "the inactive title" to Triple(p.onAccent, p.inactiveTitle ?: p.raised, 4.5f),
            "the band of the menu" to Triple(p.onAccent, p.band ?: p.accent, 4.5f),
            "the frame of a window on the workspace" to Triple(p.frame ?: p.line, p.workspace, 4.5f),
            "the edge of a control on its face" to Triple(p.line, p.raised, 2.5f),
        )
        val low = minimums.filter { (_, pair) -> contrast(pair.first, pair.second) < pair.third }.keys
        assertTrue(low.isEmpty(), "too little contrast: $low")
    }

    @Test
    fun theLitEdgeIsLighterThanTheFaceAndTheShadedOneDarkerInBothLooksOfLegacy() {
        for (p in listOf(LegacyPalette, LegacyDarkPalette)) {
            assertTrue(p.lit.luminance() > p.raised.luminance())
            assertTrue(p.shaded.luminance() < p.raised.luminance())
        }
    }

    @Test
    fun aThemeThatDoesNotFollowTheSystemIsLightUntilTheSettingsAskForDark() {
        assertFalse(Legacy.isDark(null, system = true))
        assertTrue(Legacy.isDark(true, system = false))
        assertFalse(Legacy.isDark(false, system = true))
        assertTrue(Modern.isDark(null, system = true))
        assertFalse(Modern.isDark(null, system = false))
        assertFalse(Modern.isDark(false, system = true))
        assertEquals(LegacyPalette, Legacy.palette(false))
        assertEquals(LegacyDarkPalette, Legacy.palette(true))
    }
}
