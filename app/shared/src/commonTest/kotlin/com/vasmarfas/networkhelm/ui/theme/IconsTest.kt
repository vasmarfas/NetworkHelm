package com.vasmarfas.networkhelm.ui.theme

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IconsTest {

    @Test
    fun aSpecNamesAnIconAndMayNameAColourOfThePalette() {
        assertEquals(IconTint.Success, themeIcon("check:success")?.tint)
        assertNotNull(themeIcon("check"))
        assertNull(themeIcon("check")?.tint)
        assertNull(themeIcon("check:pink"))
        assertNull(themeIcon("no-such-icon"))
        assertNull(themeIcon(""))
    }

    @Test
    fun aThemeDrawsItsOwnIconsOverTheDefaults() {
        val icons = Legacy.copy(icons = mapOf("value/action=accept" to "", "command/flush" to "trash-2:danger")).drawnIcons()
        assertEquals(IconTint.Accent, icons["button/new"]?.tint)
        assertNull(icons["value/action=accept"])
        assertEquals(IconTint.Danger, icons["command/flush"]?.tint)
        assertNull(Modern.drawnIcons()["button/new"]?.tint)
    }

    @Test
    fun everyDefaultIsAnIconOfTheSet() {
        for ((key, spec) in DefaultIcons + ClassicIcons) assertNotNull(themeIcon(spec), key)
    }

    @Test
    fun aPictureBecomesTheMaskOfItsShape() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val dark = IntArray(16 * 16) { if (it % 16 in 4..11 && it / 16 in 4..11) black else white }
        val mask = maskOf(dark, 16, 16, 8)
        assertEquals(255, mask[3 * 8 + 3].toInt() and 0xFF)
        assertEquals(0, mask[0].toInt() and 0xFF)
        assertContentEquals(mask, maskOf(IntArray(dark.size) { if (dark[it] == black) white else black }, 16, 16, 8))
        assertEquals(IconTint.Danger, themeIcon("image:8:" + Base64.encode(mask) + ":danger")?.tint)
        assertNull(themeIcon("image:8:not-base64"))
        assertNull(themeIcon("image:9:" + Base64.encode(mask)))
    }
}
