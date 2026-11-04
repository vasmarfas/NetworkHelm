package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SecretFieldTest {

    @Test
    fun theTreeGetsDotsUntilTheEyeShowsThePassword() = runDesktopComposeUiTest(400, 120) {
        Locale.setDefault(Locale.ENGLISH)
        val state = TextFieldState()
        setContent { NetworkHelmTheme { SecretField(state, Modifier.testTag("secret")) } }
        onNodeWithTag("secret").performTextInput("rd-test-pw")
        waitForIdle()
        assertEquals("rd-test-pw", state.text.toString())
        fun tree() = onNodeWithTag("secret").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("••••••••••", tree())
        onNodeWithContentDescription("Show the value").performClick()
        waitForIdle()
        assertEquals("rd-test-pw", tree())
    }
}
