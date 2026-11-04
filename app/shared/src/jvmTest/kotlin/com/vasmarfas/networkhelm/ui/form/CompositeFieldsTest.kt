package com.vasmarfas.networkhelm.ui.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CompositeFieldsTest {

    private val days = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

    private fun SkikoComposeUiTest.edit(argument: Argument, start: String): List<String> {
        val sent = mutableListOf<String>()
        setContent {
            var shown by remember { mutableStateOf(start) }
            NetworkHelmTheme {
                FieldEditor(argument, shown, {
                    shown = it
                    sent += it
                })
            }
        }
        return sent
    }

    private fun SkikoComposeUiTest.part(name: String) = onNode(hasContentDescription(name))

    private fun SkikoComposeUiTest.picture(name: String) {
        ImageIO.write(captureToImage().toAwtImage(), "png", File("build/screenshots").also { it.mkdirs() }.resolve("$name.png"))
    }

    @Test
    fun aWindowOfTheDayKeepsItsDays() = runDesktopComposeUiTest(600, 120) {
        val sent = edit(Argument("time", ValueType.TimeWindow(days), negatable = true), "8h-17h,mon")
        part("Start").assertTextEquals("8h")
        part("End").performTextReplacement("17h30m")
        onNodeWithText("tue").performClick()
        waitForIdle()
        picture("field-time-window")
        assertEquals("8h-17h30m,mon,tue", sent.last())
    }

    @Test
    fun aWindowOfTheDayMayHaveNoDays() = runDesktopComposeUiTest(600, 120) {
        val sent = edit(Argument("mon", ValueType.TimeWindow(emptyList())), "18h30m-22h")
        part("Start").assertTextEquals("18h30m")
        part("End").performTextReplacement("23h")
        waitForIdle()
        assertEquals("18h30m-23h", sent.last())
        part("Start").performTextClearance()
        part("End").performTextClearance()
        waitForIdle()
        assertEquals("", sent.last())
    }

    // a time left empty is a second, and it is written: over 10/1h,5:bit the router would keep the hour
    @Test
    fun aRateWritesItsTimeEvenWhereItIsASecond() = runDesktopComposeUiTest(600, 120) {
        val sent = edit(Argument("limit", ValueType.Rate(listOf("bit", "packet")), negatable = true), "10/1h,5:bit")
        part("Time").performTextClearance()
        waitForIdle()
        assertEquals("10/1s,5:bit", sent.last())
        part("Burst").performTextReplacement("8")
        onNodeWithText("Packet").performClick()
        waitForIdle()
        picture("field-rate")
        assertEquals("10/1s,8:packet", sent.last())
        part("Count").performTextInput(",")
        waitForIdle()
        part("Count").assertTextEquals("10")
        assertEquals("", part("Time").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals("10/1s,8:packet", sent.last())
    }

    @Test
    fun aRateForEachFlowWritesItsExpire() = runDesktopComposeUiTest(600, 120) {
        val modes = listOf("dst-address", "dst-address-and-port", "src-address", "src-and-dst-addresses", "addresses-and-dst-port")
        val sent = edit(Argument("dst-limit", ValueType.Rate(modes, perFlow = true)), "20,10,dst-address/5m")
        part("Count").performTextReplacement("30")
        waitForIdle()
        assertEquals("30/1s,10,dst-address/5m", sent.last())
        part("Expire").performTextClearance()
        waitForIdle()
        picture("field-flow-rate")
        assertEquals("30/1s,10,dst-address/1m", sent.last())
    }

    @Test
    fun aConnectionLimitIsACountForANetwork() = runDesktopComposeUiTest(600, 120) {
        val sent = edit(Argument("connection-limit", ValueType.ConnectionLimit(32), negatable = true), "100,32")
        part("Netmask").performTextReplacement("24")
        waitForIdle()
        assertEquals("100,24", sent.last())
        part("Limit").performTextClearance()
        part("Netmask").performTextClearance()
        waitForIdle()
        assertEquals("", sent.last())
    }

    @Test
    fun aClassifierTakesItsHashAndItsShare() = runDesktopComposeUiTest(600, 120) {
        val hashes = listOf("both-addresses", "both-addresses-and-ports", "src-address")
        val sent = edit(Argument("per-connection-classifier", ValueType.Classifier, hashes, negatable = true), "both-addresses:2/0")
        part("Remainder").performTextReplacement("1")
        waitForIdle()
        picture("field-classifier")
        assertEquals("both-addresses:2/1", sent.last())
    }

    @Test
    fun anIcmpTypeIsPickedByItsName() = runDesktopComposeUiTest(600, 300) {
        Locale.setDefault(Locale.ENGLISH)
        val sent = edit(Argument("icmp-options", ValueType.Icmp(), negatable = true), "")
        onNode(hasContentDescription("Variants")).performClick()
        onNodeWithText("8 echo-request").performClick()
        waitForIdle()
        assertEquals("8", sent.last())
        part("Type").assertTextEquals("8")
        onNodeWithText("echo-request").assertExists()
        part("Code").performTextInput("0")
        waitForIdle()
        assertEquals("8:0", sent.last())
        picture("field-icmp")
    }

    @Test
    fun anIcmpv6TypeHasItsOwnNumber() = runDesktopComposeUiTest(600, 300) {
        edit(Argument("icmp-options", ValueType.Icmp(six = true)), "128:0")
        part("Type").assertTextEquals("128")
        part("Code").assertTextEquals("0")
        onNodeWithText("echo-request").assertExists()
    }

    @Test
    fun aValueThePartsDoNotFitStaysText() = runDesktopComposeUiTest(600, 120) {
        edit(Argument("limit", ValueType.Rate(listOf("bit", "packet"))), "10/1m")
        onNodeWithText("10/1m").assertTextEquals("10/1m")
        assertTrue(onAllNodes(hasContentDescription("Burst")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aSpanHasTwoEnds() = runDesktopComposeUiTest(600, 120) {
        Locale.setDefault(Locale.ENGLISH)
        val sent = edit(Argument("packet-size", ValueType.Span(ValueType.Integer(0, 65535, scaled = true), listOf("min", "max"), open = true)), "100")
        part("Min").assertTextEquals("100")
        part("Max").performTextInput("1k")
        waitForIdle()
        assertEquals("100-1k", sent.last())
        part("Max").performTextClearance()
        waitForIdle()
        picture("field-span")
        assertEquals("100", sent.last())
    }

    @Test
    fun aSpanOfNegativeEndsPartsThemByTwoDots() = runDesktopComposeUiTest(600, 120) {
        Locale.setDefault(Locale.ENGLISH)
        val signal = ValueType.Span(ValueType.Integer(-120, 120, scaled = true), listOf("min", "max"), open = true, separator = "..")
        val sent = edit(Argument("signal-range", signal), "-80..-40")
        part("Min").assertTextEquals("-80")
        part("Max").assertTextEquals("-40")
        part("Max").performTextReplacement("-60")
        waitForIdle()
        assertEquals("-80..-60", sent.last())
    }

    @Test
    fun aNumberAndItsMaskStandApartByASlash() = runDesktopComposeUiTest(600, 120) {
        Locale.setDefault(Locale.ENGLISH)
        val tos = ValueType.Span(ValueType.Integer(0, 255, scaled = true), listOf("tos", "mask"), open = true, separator = "/")
        val sent = edit(Argument("tos", tos, negatable = true), "0x10/0xFC")
        part("Mask").assertTextEquals("0xFC")
        part("Mask").performTextInput("/")
        waitForIdle()
        part("Mask").assertTextEquals("0xFC")
        part("Mask").performTextReplacement("0xF0")
        waitForIdle()
        assertEquals("0x10/0xF0", sent.last())
    }

    @Test
    fun theHeadersOfAnIpv6RuleAreTickedWithTheirMode() = runDesktopComposeUiTest(600, 160) {
        Locale.setDefault(Locale.ENGLISH)
        val headers = ValueType.Headers(listOf("hop", "dst", "route", "frag", "ah", "esp", "none", "proto"), listOf("contains", "exact"))
        val sent = edit(Argument("headers", headers, negatable = true), "frag")
        onNodeWithText("hop").performClick()
        waitForIdle()
        assertEquals("hop,frag", sent.last())
        onNodeWithText("Exact").performClick()
        waitForIdle()
        picture("field-headers")
        assertEquals("hop,frag:exact", sent.last())
    }

    @Test
    fun theTtlIsAModeAndANumber() = runDesktopComposeUiTest(600, 120) {
        Locale.setDefault(Locale.ENGLISH)
        val ttl = ValueType.Adjustment(listOf("set", "increment", "decrement"), ValueType.Integer(0, 255))
        val sent = edit(Argument("new-ttl", ttl), "increment:1")
        part("Value").assertTextEquals("1")
        onNodeWithText("Decrement").performClick()
        waitForIdle()
        assertEquals("decrement:1", sent.last())
        part("Value").performTextReplacement("2")
        waitForIdle()
        picture("field-adjustment")
        assertEquals("decrement:2", sent.last())
    }

    @Test
    fun aNumberAloneSetsTheTtlAndAModeAloneWritesNothing() = runDesktopComposeUiTest(600, 120) {
        Locale.setDefault(Locale.ENGLISH)
        val sent = edit(Argument("new-ttl", ValueType.Adjustment(listOf("set", "increment", "decrement"), ValueType.Integer(0, 255))), "")
        part("Value").performTextInput("64")
        waitForIdle()
        assertEquals("set:64", sent.last())
        part("Value").performTextClearance()
        onNodeWithText("Increment").performClick()
        waitForIdle()
        assertEquals("", sent.last())
        part("Value").performTextInput("1")
        waitForIdle()
        assertEquals("increment:1", sent.last())
    }

    @Test
    fun aDayIsPickedInTheCalendar() = runDesktopComposeUiTest(600, 420) {
        Locale.setDefault(Locale.ENGLISH)
        val sent = edit(Argument("start-date", ValueType.Date), "2026-10-25")
        part("Calendar").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("October 2026").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("15").performClick()
        waitForIdle()
        assertEquals("2026-10-15", sent.last())
        part("Calendar").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("October 2026").fetchSemanticsNodes().isNotEmpty() }
        part("Next month").performClick()
        onNodeWithText("November 2026").assertExists()
        picture("field-date")
        onNodeWithText("1").performClick()
        waitForIdle()
        assertEquals("2026-11-01", sent.last())
    }

    @Test
    fun anEntryOfAListIsNegatedOnItsOwn() = runDesktopComposeUiTest(600, 120) {
        val sent = edit(Argument("src_ip", ValueType.Many(ValueType.Ip, "\n", negatable = true)), "!10.0.0.0/8\n192.0.2.1")
        val bangs = onAllNodes(hasContentDescription("Negate"))
        bangs[0].assertIsSelected()
        bangs[1].assertIsNotSelected()
        onNodeWithText("10.0.0.0/8").assertExists()
        bangs[1].performClick()
        waitForIdle()
        assertEquals("!10.0.0.0/8\n!192.0.2.1", sent.last())
        bangs[0].performClick()
        waitForIdle()
        assertEquals("10.0.0.0/8\n!192.0.2.1", sent.last())
    }

    @Test
    fun aFlagShowsItsDefaultAndWritesItsWords() = runDesktopComposeUiTest(600, 120) {
        val sent = edit(Argument("PasswordAuth", ValueType.Flag, listOf("on", "off"), default = "on"), "")
        onNode(isToggleable()).assertIsOn()
        onNode(isToggleable()).performClick()
        waitForIdle()
        assertEquals("off", sent.last())
        onNode(isToggleable()).performClick()
        waitForIdle()
        assertEquals("on", sent.last())
    }

    @Test
    fun aDayAndATimeAreTwoParts() = runDesktopComposeUiTest(600, 120) {
        Locale.setDefault(Locale.ENGLISH)
        val sent = edit(Argument("dst-start", ValueType.DateTime), "1970-01-01 00:00:00")
        part("Date").performTextReplacement("2026-03-29")
        part("Time").performTextReplacement("03:00:00")
        waitForIdle()
        assertEquals("2026-03-29 03:00:00", sent.last())
    }
}
