package com.vasmarfas.networkhelm.ui.theme

import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PictureTest {

    private fun assertCircle(spec: String?) {
        assertNotNull(spec)
        assertNotNull(themeIcon(spec))
        val mask = Base64.decode(spec.split(':')[2])
        assertEquals(255, mask[MASK / 2 * MASK + MASK / 2].toInt() and 0xFF)
        assertEquals(0, mask[0].toInt() and 0xFF)
    }

    private fun encoded(transparent: Boolean, format: String): ByteArray {
        val image = BufferedImage(300, 300, if (transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        if (!transparent) {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, 300, 300)
        }
        graphics.color = Color.BLACK
        graphics.fillOval(40, 40, 220, 220)
        graphics.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    @Test
    fun picturesOfTheUsualFormatsBecomeIcons() = runBlocking {
        assertCircle(pictureSpec(encoded(transparent = true, "png")))
        assertCircle(pictureSpec(encoded(transparent = false, "jpg")))
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/></svg>"""
        assertCircle(pictureSpec(svg.encodeToByteArray()))
        assertNull(pictureSpec("not a picture".encodeToByteArray()))
    }
}
