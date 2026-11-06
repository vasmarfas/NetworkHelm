package com.vasmarfas.networkhelm.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.vasmarfas.networkhelm.app.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MaterialThemeTest {

    private fun contrast(first: Color, second: Color): Float {
        val (light, dark) = listOf(first.luminance(), second.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun materialComesAfterTheOtherBundledThemesAndFollowsTheSystem() {
        assertEquals(listOf(Modern, Legacy, Material), BundledThemes)
        assertTrue(Material.material)
        assertFalse(Modern.material || Legacy.material)
        assertTrue(Material.isDark(null, system = true))
        assertFalse(Material.isDark(null, system = false))
        assertEquals(MaterialPalette, Material.palette(false))
        assertEquals(MaterialDarkPalette, Material.palette(true))
        assertNotEquals(MaterialPalette, MaterialDarkPalette)
    }

    @Test
    fun everyTextOfBothMaterialLooksHasAtLeastFourAndAHalfToOneOnItsBackground() {
        for (p in listOf(MaterialPalette, MaterialDarkPalette)) {
            val grounds = listOf(p.workspace, p.surface, p.raised, p.selection, p.badge, p.panel, p.title ?: p.accent, p.inactiveTitle ?: p.raised)
            val texts = listOf(p.text, p.muted, p.accent, p.danger, p.success, p.warning)
            val pairs = grounds.flatMap { ground -> texts.map { it to ground } } + listOf(p.onAccent to p.accent, p.onAccent to p.danger, p.onSnack to p.snack)
            val low = pairs.filter { (fore, back) -> contrast(fore, back) < 4.5f }.map { (fore, back) -> "${fore.hex()} on ${back.hex()}" }
            assertTrue(low.isEmpty(), "too little contrast: $low")
        }
    }

    @Test
    fun aThemeFileKeepsTheMaterialFlagAndItsNewColours() {
        val text = AppSettings.json.encodeToString(ThemeFile.serializer(), Material)
        assertTrue("\"material\": true" in text, text)
        assertTrue("\"inverse\": \"#322F35\"" in text, text)
        assertEquals(Material, AppSettings.json.decodeFromString(ThemeFile.serializer(), text))
    }

    @Test
    fun aThemeFileWrittenBeforeTheFlagStillLoads() {
        val text = """
            {"name": "Old", "light": {"workspace": "#E9ECEF", "surface": "#FFFFFF", "raised": "#F3F5F7", "line": "#D5DAE0", "text": "#1E2329",
            "muted": "#5F6873", "faint": "#A3ABB4", "accent": "#1F6FD9", "onAccent": "#FFFFFF", "selection": "#CFE2FC", "badge": "#E3E7EB",
            "danger": "#D23B33", "success": "#23895A", "warning": "#B97A12", "grid": "#DFE3E7"}, "classic": false}
        """
        val old = AppSettings.json.decodeFromString(ThemeFile.serializer(), text)
        assertEquals(ThemeFile("Old", light = LightPalette), old)
        assertFalse(old.material)
        assertEquals(LightPalette.surface, LightPalette.panel)
        assertEquals(LightPalette.text, LightPalette.snack)
    }
}
