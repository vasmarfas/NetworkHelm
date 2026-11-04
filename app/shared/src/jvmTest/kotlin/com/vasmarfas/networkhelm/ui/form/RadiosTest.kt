package com.vasmarfas.networkhelm.ui.form

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RadiosTest {
    private val cpu = Argument("cpu", ValueType.Enumeration(), listOf("all", "total", "0", "1"), labels = mapOf("0" to "cpu0", "1" to "cpu1"), apart = setOf("0"), editable = true)

    @Test
    fun anEnumerationTheRouterDrawsAsRadioButtonsStandsInARowWithItsWordsAsTheRouterWritesThem() = runDesktopComposeUiTest(600, 120) {
        val protocol = Argument("protocol", ValueType.Enumeration(closed = true), listOf("tcp", "udp"), radio = true)
        var value = ""
        setContent {
            var shown by remember { mutableStateOf("tcp") }
            NetworkHelmTheme {
                FieldEditor(protocol, shown, {
                    shown = it
                    value = it
                })
            }
        }
        onNodeWithText("tcp").assertIsSelected()
        onNodeWithText("udp").assertIsNotSelected()
        assertEquals(onNodeWithText("tcp").getBoundsInRoot().top, onNodeWithText("udp").getBoundsInRoot().top)
        ImageIO.write(captureToImage().toAwtImage(), "png", File("build/screenshots").also { it.mkdirs() }.resolve("radios.png"))
        onNodeWithText("udp").performClick()
        waitForIdle()
        assertEquals("udp", value)
        onNodeWithText("udp").assertIsSelected()
        onNodeWithText("tcp").assertIsNotSelected()
    }

    @Test
    fun aRadioFieldWithoutAValueShowsItsDefaultAndTheSameWordsWithoutTheFlagStandAsSegments() = runDesktopComposeUiTest(600, 200) {
        val acquisition = Argument("address-acquisition", ValueType.Enumeration(closed = true), listOf("static", "automatic"), default = "automatic", radio = true)
        val plain = Argument("mode", ValueType.Enumeration(closed = true), listOf("static", "automatic"))
        setContent {
            NetworkHelmTheme {
                Column {
                    FieldEditor(acquisition, "", {})
                    FieldEditor(plain, "static", {})
                }
            }
        }
        onNodeWithText("automatic").assertIsSelected()
        onNodeWithText("Static").assertIsSelected()
    }

    @Test
    fun aNumberTypedIntoAFieldOfNamesAndNumbersGoesOutAsTheNumberAndWhatIsNoneOfThemStaysOut() = runDesktopComposeUiTest(600, 200) {
        Locale.setDefault(Locale.ENGLISH)
        val values = mutableListOf<String>()
        setContent {
            var shown by remember { mutableStateOf("all") }
            NetworkHelmTheme {
                FieldEditor(cpu, shown, {
                    shown = it
                    values += it
                })
            }
        }
        onNode(hasSetTextAction()).performTextReplacement("1")
        waitForIdle()
        assertEquals(listOf("1"), values)
        onNode(hasSetTextAction()).assertTextEquals("1")
        onNode(hasSetTextAction()).performTextReplacement("12x")
        waitForIdle()
        assertEquals(listOf("1"), values)
        onNodeWithText("Whole number").assertExists()
        onNode(hasSetTextAction()).performTextReplacement("total")
        waitForIdle()
        assertEquals(listOf("1", "total"), values)
        onNode(hasSetTextAction()).performTextReplacement("cpu0")
        waitForIdle()
        assertEquals(listOf("1", "total", "0"), values)
    }

    @Test
    fun aChoiceOfTheListShowsAsItsLabelAndEscBringsBackTheValueTheFieldHadWhenItGotTheFocus() = runDesktopComposeUiTest(600, 300) {
        Locale.setDefault(Locale.ENGLISH)
        val values = mutableListOf<String>()
        setContent {
            var shown by remember { mutableStateOf("all") }
            NetworkHelmTheme {
                FieldEditor(cpu, shown, {
                    shown = it
                    values += it
                })
            }
        }
        onNodeWithContentDescription("Variants").performClick()
        onNode(hasText("cpu1") and !hasSetTextAction()).performClick()
        waitForIdle()
        assertEquals(listOf("1"), values)
        onNode(hasSetTextAction()).assertTextEquals("cpu1")
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextReplacement("0")
        waitForIdle()
        assertEquals(listOf("1", "0"), values)
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(listOf("1", "0", "1"), values)
        onNode(hasSetTextAction()).assertTextEquals("cpu1")
    }
}
