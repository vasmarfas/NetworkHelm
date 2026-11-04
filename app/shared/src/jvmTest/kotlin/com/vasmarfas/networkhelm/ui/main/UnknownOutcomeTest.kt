package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.openwrt.RequestOutcomeUnknownException
import com.vasmarfas.networkhelm.ui.files.fileFailure
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.test.runTest
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UnknownOutcomeTest {
    @Test
    fun aGatewayErrorWithAnUnknownOutcomeShowsTheLocalizedWarning() = runDesktopComposeUiTest(900, 300) {
        Locale.setDefault(Locale.ENGLISH)
        val error = DeviceException(ErrorCategory.ConnectionLost, RequestOutcomeUnknownException.MESSAGE)
        setContent { NetworkHelmTheme { Text(deviceMessage(error.message.orEmpty())) } }
        onNodeWithText("The connection was lost after sending the command. Its outcome is unknown; check the router before trying again.").assertExists()
    }

    @Test
    fun fileErrorsWithAnUnknownOutcomeAreLocalizedAndRouterErrorsStayIntact() = runTest {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("The connection was lost after sending the command. Its outcome is unknown; check the router before trying again.", fileFailure(RequestOutcomeUnknownException()))
        assertEquals("router trap", fileFailure(DeviceException(ErrorCategory.Api, "router trap")))
    }
}
