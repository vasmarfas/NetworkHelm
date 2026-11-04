package com.vasmarfas.networkhelm.routeros.console

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.LinkState
import com.vasmarfas.networkhelm.routeros.api.ApiConnectionClosedException
import com.vasmarfas.networkhelm.routeros.api.ApiEncoding
import com.vasmarfas.networkhelm.routeros.api.ApiTrapException
import com.vasmarfas.networkhelm.ssh.ShellChannel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PROMPT_TEXT = "\r\r\r\u001b[9999B\u001b[K[\u001b[m\u001b[36mtester\u001b[m@\u001b[m\u001b[32mCHR\u001b[m] > "
private val MARKERS = Regex("^:put \"(rd[0-9a-f]+-\\d+b)\"; (.*); :put \"(rd[0-9a-f]+-\\d+e)\"$")

private fun bytes(vararg parts: ByteArray) = parts.flatMap { it.asList() }.joinToString("") { (it.toInt() and 0xFF).toChar().toString() }

private fun latin1(text: String) = ByteArray(text.length) { text[it].code.toByte() }

private class FakeConsole(stale: String = "", banner: Boolean = true, private val bytesOf: (String) -> ByteArray = String::encodeToByteArray, probing: Boolean = false, private val handler: suspend (String) -> String) : ShellChannel {
    private val out = Channel<ByteArray>(Channel.UNLIMITED)
    var closed = false

    val answers = mutableListOf<String>()

    init {
        if (probing) out.trySend(bytesOf("\r\u001b[9999B\u001b[6n"))
        else if (banner) out.trySend(bytesOf("\r\n  MikroTik RouterOS\r\n$PROMPT_TEXT$stale"))
    }

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val chunk = out.receiveCatching().getOrNull() ?: return -1
        chunk.copyInto(buffer, offset)
        return chunk.size
    }

    override suspend fun write(bytes: ByteArray) {
        if (bytes[0] == 0x1B.toByte()) {
            answers += bytes.decodeToString()
            out.trySend(bytesOf("\r\n  MikroTik RouterOS\r\n$PROMPT_TEXT"))
            return
        }
        if (bytes.contentEquals(byteArrayOf(3))) {
            out.trySend(bytesOf("\rScript Error: action cancelled\n\r\r\r$PROMPT_TEXT"))
            return
        }
        val line = bytes.decodeToString().trimEnd('\r')
        val (begin, command, end) = checkNotNull(MARKERS.matchEntire(line)).destructured
        val text = handler(command)
        val asking = text.startsWith("?")
        val silent = text == "~"
        val body = when {
            asking -> text.drop(1) + ": "
            silent -> ""
            text.startsWith("!") -> text.drop(1) + "\r\n"
            else -> "$text\r\n$end\r\n"
        }
        out.trySend(bytesOf("$line\u001b[12D\r\n\r$begin\r\n$body${if (asking || silent) "" else PROMPT_TEXT}"))
    }

    override fun resize(columns: Int, rows: Int) = Unit

    override fun close() {
        closed = true
        out.close()
    }
}

class ConsoleClientTest {
    private val consoles = mutableListOf<FakeConsole>()

    private fun client(size: Int = 4, handler: suspend (String) -> String) = ConsoleClient(size) { FakeConsole(handler = handler).also { consoles += it } }

    @Test
    fun aPrintBecomesRowsAndTheSettingsAreOneRow() = runTest {
        val client = client { command ->
            when {
                "/ip/address/print" in command -> """[{".id":"*1","address":"192.0.2.1/24","disabled":false}]"""
                "/system/identity/print" in command -> """{"name":"CHR"}"""
                else -> "!unexpected $command"
            }
        }
        assertEquals(listOf(mapOf(".id" to "*1", "address" to "192.0.2.1/24", "disabled" to "false")), client.call(listOf("/ip/address/print", "?disabled=false")).rows)
        assertEquals(listOf(mapOf("name" to "CHR")), client.call(listOf("/system/identity/print")).rows)
    }

    private fun router(sent: MutableList<String>, refuses: Set<String>, vararg views: String): suspend (String) -> String = { command ->
        sent += command
        val parts = command.split("; ")
        val failing = parts.indexOfFirst { part -> part.split(' ', ']').any { it.substringBefore('=') in refuses } }
        if (failing < 0) views.take(parts.size).joinToString("\n")
        else "!" + (views.take(failing) + "bad parameter ${parts[failing].split(' ', ']').first { it.substringBefore('=') in refuses }.substringBefore('=')} (line 1 column 120)").joinToString("\n")
    }

    @Test
    fun theViewsOfARowAreOneRow() = runTest {
        val client = client(handler = router(mutableListOf(), emptySet(),
            "[{\".id\":\"*1\",\"chain\":\"input\"},{\".id\":\"*2\",\"chain\":\"forward\"}]",
            "[{\".id\":\"*1\",\"dst-port\":\"25\"}]",
            "[{\".id\":\"*1\",\"bytes\":10},{\".id\":\"*2\",\"bytes\":0}]",
        ))
        assertEquals(
            listOf(mapOf(".id" to "*1", "chain" to "input", "dst-port" to "25", "bytes" to "10"), mapOf(".id" to "*2", "chain" to "forward", "bytes" to "0")),
            client.call(listOf("/ip/firewall/filter/print")).rows,
        )
    }

    @Test
    fun aMenuIsAskedAgainWithoutTheWordsItRefusesAndRemembersThem() = runTest {
        val sent = mutableListOf<String>()
        val client = client(size = 1, handler = router(sent, setOf("show-sensitive", "stats-detail"),
            "[{\".id\":\"*2\",\"address\":\"192.0.2.1/24\"}]", "[{\".id\":\"*2\",\"actual-interface\":\"ether1\"}]", "[{\".id\":\"*2\",\"bytes\":5}]",
        ))
        val address = listOf("/ip/address/print")
        val row = mapOf(".id" to "*2", "address" to "192.0.2.1/24", "actual-interface" to "ether1", "bytes" to "5")
        assertEquals(listOf(row), client.call(address).rows)
        assertEquals(3, sent.size)
        assertEquals(listOf(row), client.call(address).rows)
        assertEquals(4, sent.size)
        assertEquals(sent[2], sent[3])
    }

    @Test
    fun aMenuThatRefusesEveryWordIsAskedPlain() = runTest {
        val sent = mutableListOf<String>()
        val client = client(size = 1, handler = router(sent, setOf("detail", "show-sensitive", "stats-detail", "stats"), "{\"name\":\"CHR\"}"))
        val identity = listOf("/system/identity/print")
        assertEquals(listOf(mapOf("name" to "CHR")), client.call(identity).rows)
        assertEquals(5, sent.size)
        assertEquals(listOf(mapOf("name" to "CHR")), client.call(identity).rows)
        assertEquals(6, sent.size)
        assertEquals(":put [:serialize to=json options=json.no-string-conversion [/system/identity/print as-value]]", sent.last())
    }

    @Test
    fun aMenuOfSettingsThatRefusesProplistIsAskedWithoutItAndTheRowIsCutToWhatWasAskedFor() = runTest {
        val sent = mutableListOf<String>()
        val client = client(size = 1, handler = router(sent, setOf("proplist", "detail", "show-sensitive", "stats-detail", "stats"), "{\"uptime\":\"1d\",\"cpu-load\":3,\"version\":\"7.26\"}"))
        val resource = listOf("/system/resource/print", "=.proplist=.id,uptime,cpu-load")
        val row = mapOf("uptime" to "1d", "cpu-load" to "3")
        assertEquals(listOf(row), client.call(resource).rows)
        assertTrue("proplist=uptime,cpu-load" in sent.first())
        assertFalse("proplist" in sent.last())
        val asked = sent.size
        assertEquals(listOf(row), client.call(resource).rows)
        assertEquals(asked + 1, sent.size)
    }

    @Test
    fun aRefusalOfAWordThatTheLineDidNotOfferEndsTheAttempts() = runTest {
        val sent = mutableListOf<String>()
        val client = client { command ->
            sent += command
            if ("/ip/address" in command) "!bad parameter foo (line 1 column 112)" else "!expected end of command (line 1 column 20)"
        }
        assertEquals(1, assertFailsWith<ApiTrapException> { client.call(listOf("/ip/address/print", "=foo=1")) }.category)
        assertEquals(0, assertFailsWith<ApiTrapException> { client.call(listOf("/ip/route/print")) }.category)
        assertEquals(2, sent.size)
    }

    @Test
    fun theMessagesOfTheLogThatTheRouterPrintsIntoTheConsoleAreNotTakenForAnAnswer() = runTest {
        val message = "22:10:36 echo: system,error,critical login failure for user admin from 192.0.2.9 via winbox"
        val client = ConsoleClient(1) { FakeConsole("\r\n$message\r\n$PROMPT_TEXT") { "$message\r\n[{\"name\":\"CHR\"}]" }.also { consoles += it } }
        assertEquals(listOf(mapOf("name" to "CHR")), client.call(listOf("/system/identity/print")).rows)
        assertEquals(listOf(mapOf("name" to "CHR")), client.call(listOf("/system/identity/print")).rows)
    }

    @Test
    fun anAddAnswersWithItsIdAndACountWithItsNumber() = runTest {
        val client = client { command -> if ("/add " in command) "*4A" else "3" }
        assertEquals(mapOf("ret" to "*4A"), client.call(listOf("/ip/address/add", "=address=192.0.2.1/24")).done)
        assertEquals(mapOf("ret" to "3"), client.call(listOf("/ip/address/print", "=count-only=")).done)
        assertEquals(emptyMap(), client.call(listOf("/ip/address/remove", "=.id=*4A")).done)
    }

    @Test
    fun theRoutersMessagesBecomeTraps() = runTest {
        val client = client { command -> if ("*FF" in command) "!no such item (4)" else "!syntax error (line 1 column 16)" }
        assertEquals(0, assertFailsWith<ApiTrapException> { client.call(listOf("/ip/address/set", "=.id=*FF", "=comment=x")) }.category)
        assertEquals("syntax error (line 1 column 16)", assertFailsWith<ApiTrapException> { client.call(listOf("/nope/print")) }.message)
    }

    @Test
    fun aLineThatLacksAnArgumentIsGivenUpAndTheConsoleGoesOn() = runTest {
        val client = client(size = 1) { command -> if ("/add " in command) "?address" else "[]" }
        val refusal = assertFailsWith<ApiTrapException> { client.call(listOf("/ip/address/add", "=interface=ether1")) }
        assertEquals("missing value(s) of argument(s) address", refusal.message)
        assertEquals(1, refusal.category)
        assertEquals(emptyList(), client.call(listOf("/ip/address/print")).rows)
        assertEquals(1, consoles.size)
    }

    @Test
    fun aBooleanTheRouterRefusesIsSentAgainAsYesOrNo() = runTest {
        val sent = mutableListOf<String>()
        val client = client { command ->
            sent += command
            when {
                "flag=\"true\"" in command -> "!invalid value of flag, must be either yes or no"
                else -> ""
            }
        }
        client.call(listOf("/ip/x/set", "=.id=*1", "=flag=true"))
        client.call(listOf("/ip/x/set", "=.id=*1", "=flag=false"))
        assertEquals(listOf("/ip/x/set numbers=*1 flag=\"true\"", "/ip/x/set numbers=*1 flag=yes", "/ip/x/set numbers=*1 flag=no"), sent)
    }

    @Test
    fun aCommandThatTakesNoAsValueIsRunPlain() = runTest {
        val sent = mutableListOf<String>()
        val client = client { command ->
            sent += command
            if ("as-value" in command) "!bad parameter as-value (line 1 column 57)" else "done"
        }
        assertEquals(mapOf("ret" to "done"), client.call(listOf("/ip/address/make-static", "=.id=*1")).done)
        assertEquals(2, sent.size)
        assertEquals("/ip/address/make-static numbers=*1", sent.last())
    }

    private fun classic(handler: suspend (String) -> String) = ConsoleClient(1, Dialect.Classic) { FakeConsole(handler = handler).also { consoles += it } }

    @Test
    fun aConsoleOfRouterOs6IsGivenTheFunctionOfTheClientAndItsRowsAreRecords() = runTest {
        val sent = mutableListOf<String>()
        val client = classic { command ->
            sent += command
            when {
                command.startsWith(":global rdrow") -> ""
                "find" in command -> "\u0001.id\u0002id\u0002*1\n\u0001address\u0002str\u0002192.0.2.1/24\n\u0001disabled\u0002bool\u0002false\n\u0003"
                else -> "!unexpected $command"
            }
        }
        assertEquals(listOf(mapOf(".id" to "*1", "address" to "192.0.2.1/24", "disabled" to "false")), client.call(listOf("/ip/address/print")).rows)
        assertEquals(2, sent.size)
        assertEquals(PRELUDE, sent.first())
    }

    @Test
    fun aMenuOfSettingsIsRememberedAfterItsRefusalOfFind() = runTest {
        val sent = mutableListOf<String>()
        val client = classic { command ->
            sent += command
            when {
                command.startsWith(":global rdrow") -> ""
                "find" in command -> "!bad command name find (line 1 column 27)"
                else -> "\u0001allow-remote-requests\u0002bool\u0002false\n\u0001servers\u0002array\u0002\n\u0003"
            }
        }
        val dns = listOf("/ip/dns/print")
        assertEquals(listOf(mapOf("allow-remote-requests" to "false", "servers" to "")), client.call(dns).rows)
        assertEquals(1, client.call(dns).rows.size)
        assertEquals(1, sent.count { "find" in it })
        assertEquals(2, sent.count { it.endsWith("[/ip dns get]") })
    }

    @Test
    fun aConsoleOfRouterOs6ThatFallsSilentIsGivenUpAndTheQuickLineIsAskedAgain() = runTest {
        var silent = true
        val client = classic { command ->
            when {
                command.startsWith(":global rdrow") -> ""
                silent -> "~".also { silent = false }
                else -> "\u0001.id\u0002id\u0002*1\n\u0003"
            }
        }
        assertEquals(listOf(mapOf(".id" to "*1")), client.call(listOf("/ip/address/print")).rows)
        assertEquals(2, consoles.size)
        assertTrue(consoles.first().closed)
        assertEquals(LinkState.Up, client.status.first())
    }

    @Test
    fun aConsoleThatSaysNothingWhenItIsOpenedIsGivenUpAndAnotherIsOpened() = runTest {
        var first = true
        val client = ConsoleClient(1, Dialect.Classic) {
            FakeConsole(banner = !first.also { first = false }) { command -> if (command.startsWith(":global rdrow")) "" else "\u0001.id\u0002id\u0002*1\n\u0003" }.also { consoles += it }
        }
        assertEquals(listOf(mapOf(".id" to "*1")), client.call(listOf("/ip/address/print")).rows)
        assertEquals(2, consoles.size)
        assertTrue(consoles.first().closed)
    }

    @Test
    fun aCountOfAMenuWithoutAListIsAnUnknownParameterAsTheApiSaysIt() = runTest {
        val client = classic { command -> if (command.startsWith(":global")) "" else "!expected end of command (line 1 column 27)" }
        assertEquals("unknown parameter", assertFailsWith<ApiTrapException> { client.call(listOf("/ip/dns/print", "=count-only=")) }.message)
    }

    @Test
    fun theFlagsOfRowsThatWereSeenAreSentAsYesAndNo() = runTest {
        val sent = mutableListOf<String>()
        val client = classic { command ->
            sent += command
            if ("find" in command) "\u0001.id\u0002id\u0002*1\n\u0001passive\u0002bool\u0002false\n\u0003" else ""
        }
        client.call(listOf("/ip/x/set", "=.id=*1", "=passive=true"))
        client.call(listOf("/ip/x/print"))
        client.call(listOf("/ip/x/set", "=.id=*1", "=passive=true"))
        assertEquals(listOf(":put [/ip x set numbers=*1 passive=\"true\"]", ":put [/ip x set numbers=*1 passive=yes]"), sent.filter { it.startsWith(":put") })
    }

    @Test
    fun callsRunOnSeveralConsolesAtOnceAndNoMoreThanTheSizeOfThePool() = runTest {
        val client = client(size = 3) { command ->
            delay(50)
            "[{\"n\":\"$command\"}]"
        }
        val answers = (1..9).map { index -> async { client.call(listOf("/menu$index/print")).rows.single()["n"] } }.awaitAll()
        assertEquals(9, answers.toSet().size)
        assertEquals(3, consoles.size)
    }

    @Test
    fun aConsoleThatEndsFailsItsCallAndTheNextCallOpensAnother() = runTest {
        var first = true
        val client = ConsoleClient(1) {
            FakeConsole { "[]" }.also {
                consoles += it
                if (first) {
                    first = false
                    it.close()
                }
            }
        }
        assertFailsWith<ApiConnectionClosedException> { client.call(listOf("/ip/address/print")) }
        assertEquals(LinkState.Reconnecting, client.status.first())
        assertEquals(emptyList(), client.call(listOf("/ip/address/print")).rows)
        assertEquals(LinkState.Up, client.status.first())
        assertEquals(2, consoles.size)
    }

    @Test
    fun aTableThatTheRouterKeepsInCp1251IsReadByTheEncodingOfTheRouter() = runTest {
        val table = bytes("[{\".id\":\"*1\",\"comment\":\"".encodeToByteArray(), ApiEncoding.Windows1251.encode("Привет"), "\"}]".encodeToByteArray())
        val client = ConsoleClient(1, encoding = ApiEncoding.Windows1251) { FakeConsole(bytesOf = ::latin1) { table }.also { consoles += it } }
        assertEquals("Привет", client.call(listOf("/ip/address/print")).rows.single()["comment"])
    }

    @Test
    fun aByteThatIsNotUtf8IsCp1251UnderTheUtf8EncodingWithoutSpoilingTheRestOfTheTable() = runTest {
        val table = bytes("[{\"a\":\"".encodeToByteArray(), "при".encodeToByteArray(), "\",\"b\":\"".encodeToByteArray(), ApiEncoding.Windows1251.encode("при"), "\"}]".encodeToByteArray())
        val client = ConsoleClient(1) { FakeConsole(bytesOf = ::latin1) { table }.also { consoles += it } }
        assertEquals(mapOf("a" to "при", "b" to "при"), client.call(listOf("/ip/address/print")).rows.single())
    }

    @Test
    fun aConsoleThatAsksWhereTheCursorIsGetsTheAnswerBeforeItDrawsItsPrompt() = runTest {
        val client = ConsoleClient(1) { FakeConsole(probing = true) { "{\"name\":\"CHR\"}" }.also { consoles += it } }
        assertEquals("CHR", client.call(listOf("/system/identity/print")).rows.single()["name"])
        assertEquals(listOf("\u001b[100;1R"), consoles.single().answers)
    }

    @Test
    fun aValueIsTypedByTheCodesOfTheEncodingOfTheRouter() = runTest {
        val sent = mutableListOf<String>()
        val client = ConsoleClient(1, encoding = ApiEncoding.Windows1251) { FakeConsole { command -> sent += command; "*1" }.also { consoles += it } }
        assertEquals("*1", client.call(listOf("/ip/address/add", "=comment=Привет")).done["ret"])
        assertTrue("comment=\"\\CF\\F0\\E8\\E2\\E5\\F2\"" in sent.single())
    }

    @Test
    fun aScriptWithACharacterThatTheEncodingHasNoCodeForIsRefusedBeforeItIsTyped() = runTest {
        val client = ConsoleClient(1, encoding = ApiEncoding.Windows1251) { FakeConsole { "" }.also { consoles += it } }
        val refusal = assertFailsWith<DeviceException> { client.call(listOf("/execute", "=script=:put \"😀\"")) }
        assertEquals(ErrorCategory.ArgumentValue, refusal.category)
        assertEquals(LinkState.Up, client.status.first())
    }

    @Test
    fun aCallerThatGivesUpIsNotTakenForTheLossOfTheLink() = runTest {
        val client = client { delay(10_000); "[]" }
        assertFailsWith<TimeoutCancellationException> { withTimeout(1_000) { client.call(listOf("/ip/address/print")) } }
        assertEquals(LinkState.Up, client.status.first())
    }

    @Test
    fun aTransportThatDroppedIsWaitedForAndTheClientIsUpWhenItIsBack() = runTest {
        var back = false
        val client = ConsoleClient(1) {
            if (!back) throw DeviceException(ErrorCategory.ConnectionLost, "connection closed")
            FakeConsole { "[]" }.also { consoles += it }
        }
        assertFailsWith<ApiConnectionClosedException> { client.call(listOf("/ip/address/print")) }
        assertEquals(LinkState.Reconnecting, client.status.first())
        back = true
        assertEquals(emptyList(), client.call(listOf("/ip/address/print")).rows)
        assertEquals(LinkState.Up, client.status.first())
    }

    @Test
    fun listenIsRefusedSoTheDriverPollsAndAClosedClientRefusesEverything() = runTest {
        val client = client { "[]" }
        assertFailsWith<ApiTrapException> { client.command(listOf("/ip/address/listen")).toList() }
        assertEquals(emptyList(), client.command(listOf("/tool/traceroute", "=address=127.0.0.1")).toList())
        client.call(listOf("/ip/address/print"))
        client.close()
        assertTrue(consoles.all { it.closed })
        assertFailsWith<ApiConnectionClosedException> { client.call(listOf("/ip/address/print")) }
    }

    @Test
    fun aCommandThatNeverEndsIsAskedARoundAtATime() = runTest {
        val sent = mutableListOf<String>()
        val client = client { command ->
            sent += command
            """[{".id":"*FFFFFFFE","name":"total","usage":${listOf(253, 170, 5)[sent.size - 1]}}]"""
        }
        val replies = client.command(listOf("/tool/profile")).take(3).toList()
        assertEquals(listOf("0", "1", "2"), replies.map { it.attributes[".section"] })
        assertEquals(listOf("25.3", "17", "0.5"), replies.map { it.attributes["usage"] })
        assertEquals(List(3) { ":put [:serialize to=json options=json.no-string-conversion [/tool/profile duration=3 as-value]]" }, sent)
    }

    @Test
    fun aPingKeepsTheMicrosecondsTheSerializedRowLoses() = runTest {
        val sent = mutableListOf<String>()
        val client = client { command ->
            sent += command
            ".id=*0;host=127.0.0.1;seq=0;size=56;time=00:00:00.0000${60 + sent.size};ttl=64"
        }
        val replies = client.command(listOf("/tool/ping", "=address=127.0.0.1", "=count=2")).toList()
        assertEquals(List(2) { ":put [/tool/ping address=127.0.0.1 count=1 as-value]" }, sent)
        assertEquals(listOf("61us", "62us"), replies.map { it.attributes["time"] })
        assertEquals(listOf("0", "1"), replies.map { it.attributes["seq"] })
        assertEquals("2", replies.last().attributes["received"])
    }

    @Test
    fun aLineTooLongForTheConsoleIsRefusedBeforeItIsSent() = runTest {
        val client = client { "[]" }
        val refusal = assertFailsWith<DeviceException> { client.call(listOf("/ip/address/add", "=comment=" + "x".repeat(20_000))) }
        assertEquals(ErrorCategory.ArgumentValue, refusal.category)
        assertTrue(consoles.isEmpty())
    }
}
