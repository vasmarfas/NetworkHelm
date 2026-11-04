package com.vasmarfas.networkhelm.routeros

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.Pack
import com.vasmarfas.networkhelm.packs.PackNative
import com.vasmarfas.networkhelm.packs.PackFallback
import com.vasmarfas.networkhelm.packs.PackNativeField
import com.vasmarfas.networkhelm.packs.PackNativeRef
import com.vasmarfas.networkhelm.packs.PackPath
import com.vasmarfas.networkhelm.packs.PackQuery
import com.vasmarfas.networkhelm.packs.PackTab
import com.vasmarfas.networkhelm.packs.PackWindow
import com.vasmarfas.networkhelm.session.NativeObjects
import com.vasmarfas.networkhelm.session.NativeRead
import com.vasmarfas.networkhelm.session.NativeRun
import com.vasmarfas.networkhelm.session.NativeSide
import com.vasmarfas.networkhelm.session.QUERY_END
import com.vasmarfas.networkhelm.session.QUERY_PASS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class NativeQueryWindowsTest {

    private val spec = PackNative(
        listOf(22),
        listOf(PackNativeField("seq", 0xF, "number")),
        query = PackQuery(
            false,
            listOf(PackNativeField("address", 0x16, "addr", writable = true), PackNativeField("count", 0x11, "number", writable = true)),
            0xFE000F, 0xFE0011, 0xFE0004,
            optional = listOf("count"),
            required = listOf("address"),
        ),
    )

    private class Run(private val calls: MutableList<String>, private val cancelFails: Boolean = false, private vararg val reads: () -> NativeRead) : NativeRun {
        private var at = 0

        override suspend fun read(): NativeRead {
            calls += "read"
            return reads[minOf(at++, reads.lastIndex)]()
        }

        override suspend fun cancel() {
            calls += "cancel"
            if (cancelFails) throw DeviceException(ErrorCategory.ConnectionLost, "gone")
        }
    }

    private class Objects(private val calls: MutableList<String>, private val run: NativeRun? = null, private val answers: Boolean = true, private val refusal: DeviceException? = null) : NativeObjects {
        var values: Map<String, String>? = null
        var rows: Map<Long, String> = emptyMap()

        override suspend fun names(ref: PackNativeRef): Map<Long, String> = rows

        override suspend fun read(spec: PackNative, limit: Int?): List<Item>? = emptyList()

        override suspend fun answers(spec: PackNative) = answers

        override suspend fun start(spec: PackNative, values: Map<String, String>): NativeRun {
            calls += "start"
            this.values = values
            refusal?.let { throw it }
            return checkNotNull(run)
        }

        override fun close() = Unit
    }

    // a channel of its own at each opening: it dies when the windows close it, and a run it started goes with it
    private class Link(private val calls: MutableList<String>, private val name: String, private val gate: CompletableDeferred<Unit>? = null) : NativeObjects {
        var dead = false
        var failTable = false
        var breaksLines = false
        var failStart = false

        override suspend fun read(spec: PackNative, limit: Int?): List<Item>? {
            if (dead || failTable) throw DeviceException(ErrorCategory.ConnectionLost, "gone")
            return emptyList()
        }

        override suspend fun start(spec: PackNative, values: Map<String, String>): NativeRun {
            if (dead || failStart) throw DeviceException(ErrorCategory.ConnectionLost, "connection closed")
            gate?.await()
            calls += "$name start"
            return object : NativeRun {
                override suspend fun read(): NativeRead {
                    if (dead) throw DeviceException(ErrorCategory.ConnectionLost, "gone")
                    return NativeRead(listOf(Item("*0", mapOf("seq" to "0"))), snapshot = false, finished = false)
                }

                override suspend fun cancel() {
                    calls += "$name cancel"
                }
            }
        }

        override suspend fun lines(spec: PackNative): NativeRun = object : NativeRun {
            override suspend fun read(): NativeRead {
                if (dead || breaksLines) throw DeviceException(ErrorCategory.ConnectionLost, "gone")
                return NativeRead(listOf(Item("*1", mapOf("seq" to "1"))), snapshot = true, finished = false)
            }

            override suspend fun cancel() = Unit
        }

        override fun close() {
            dead = true
        }
    }

    private fun links(calls: MutableList<String>, made: MutableList<Link>, gate: CompletableDeferred<Unit>? = null, setup: (Int, Link) -> Unit = { _, _ -> }) =
        NativeSide { Link(calls, "c${made.size + 1}", gate).also { setup(made.size, it); made += it } }

    private fun windows(objects: NativeObjects) = NativeWindows(NativeSide { objects }, { 8291 }, 1.seconds)

    private fun log(vararg rows: Item) = NativeRead(rows.toList(), snapshot = false, finished = false)

    private fun table(vararg rows: Item, finished: Boolean = false) = NativeRead(rows.toList(), snapshot = true, finished = finished)

    private fun row(id: String, seq: String) = Item(id, mapOf("seq" to seq))

    private fun packOf(path: String) = Pack(menu = listOf("Tools", "Echo"), window = PackWindow("Echo", listOf(PackTab("Echo", path))), paths = mapOf(path to PackPath(native = spec)))

    @Test
    fun theWindowStartsReadsOnceARefreshAndCancelsWhenItStops() = runTest {
        val calls = ArrayList<String>()
        val objects = Objects(calls, Run(calls, false, { log(row("*0", "0")) }))
        val seen = ArrayList<Item>()
        val job = launch { windows(objects).query(spec, mapOf("address" to "10.0.2.2")).collect { seen += it } }
        runCurrent()
        assertEquals(listOf("start", "read"), calls)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(listOf("start", "read", "read", "read"), calls)
        job.cancel()
        runCurrent()
        assertEquals("cancel", calls.last())
        assertEquals(mapOf("address" to "10.0.2.2"), objects.values)
        assertEquals(3, seen.size)
    }

    @Test
    fun aTableComesInPassesAndATableThatDidNotChangeIsNotSentAgain() = runTest {
        val calls = ArrayList<String>()
        val first = table(row("*1", "1"))
        val changed = table(row("*1", "2"), finished = true)
        val run = Run(calls, false, { first }, { first }, { changed })
        val seen = windows(Objects(calls, run)).query(spec, emptyMap()).toList()
        assertEquals(listOf("*1" to "1", QUERY_END to null, "*1" to "2", QUERY_END to null), seen.map { it.id to it["seq"] })
        assertEquals(listOf("1", "1", "2", "2"), seen.map { it[QUERY_PASS] })
        assertEquals(listOf("start", "read", "read", "read", "cancel"), calls)
    }

    @Test
    fun theRowsOfALogAreSentAsTheyComeWithoutMarks() = runTest {
        val calls = ArrayList<String>()
        val run = Run(calls, false, { log(row("*0", "0"), row("*1", "1")) }, { table(row("*2", "2"), finished = true) })
        val seen = windows(Objects(calls, run)).query(spec, emptyMap()).toList()
        assertEquals(listOf("*0", "*1", "*2", QUERY_END), seen.map { it.id })
        assertEquals(listOf(null, null, "1", "1"), seen.map { it[QUERY_PASS] })
    }

    @Test
    fun aRouterThatSaysTheRunIsOverEndsTheFlowAndTheRunIsCancelledAnyway() = runTest {
        val calls = ArrayList<String>()
        val seen = windows(Objects(calls, Run(calls, false, { table(row("*1", "1"), finished = true) }))).query(spec, emptyMap()).toList()
        assertEquals(2, seen.size)
        assertEquals(listOf("start", "read", "cancel"), calls)
    }

    @Test
    fun aChannelThatDropsEndsTheFlowWithTheErrorAndTheCancelThatFailsAfterItIsLetGo() = runTest {
        val calls = ArrayList<String>()
        val lost = DeviceException(ErrorCategory.ConnectionLost, "dropped")
        val failure = assertFailsWith<DeviceException> {
            windows(Objects(calls, Run(calls, true, { log(row("*0", "0")) }, { throw lost }))).query(spec, emptyMap()).toList()
        }
        assertEquals(ErrorCategory.ConnectionLost, failure.category)
        assertEquals(listOf("start", "read", "read", "cancel"), calls)
    }

    @Test
    fun aStartTheRouterRefusesLeavesNothingToCancel() = runTest {
        val calls = ArrayList<String>()
        val refusal = DeviceException(ErrorCategory.General, "device-mode forbids it")
        val failure = assertFailsWith<DeviceException> { windows(Objects(calls, refusal = refusal)).query(spec, emptyMap()).toList() }
        assertEquals("device-mode forbids it", failure.message)
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun theSchemaOfAQueryIsACommandWhoseArgumentsAreTheRequest() = runTest {
        val schema = windows(Objects(ArrayList())).schema(MenuPath.parse("native/22/q1"), spec)
        assertEquals(NodeKind.Command, schema.kind)
        assertEquals(listOf("address", "count"), schema.arguments.map { it.name })
        assertEquals(listOf(false, true), schema.arguments.map { it.optional })
    }

    @Test
    fun theDropdownsOfARequestAreTheRowsOfTheTablesItsFieldsReferToAndAnAddressStaysALine() = runTest {
        val interfaces = PackNativeRef(listOf(20, 0), listOf(0x10006))
        val request = listOf(
            PackNativeField("interface", 1, "enm", names = mapOf(0L to "all"), letter = "u", refers = interfaces, writable = true),
            PackNativeField("several", 3, "multinumber", letter = "U", refers = interfaces, writable = true),
            PackNativeField("address", 0x16, "addr", letter = "m", refers = interfaces, writable = true),
            PackNativeField("count", 0x11, "number", letter = "u", writable = true),
        )
        val query = PackQuery(false, request, 0xFE000F, 0xFE0011, 0xFE0004)
        val windows = windows(Objects(ArrayList()).also { it.rows = mapOf(2L to "ether1", 1L to "lo") })
        val schema = windows.schema(MenuPath.parse("native/22/q1"), PackNative(listOf(22), emptyList(), query = query), lists = true)
        assertEquals(listOf("all", "lo", "ether1"), schema.argument("interface")?.choices)
        assertEquals(ValueType.Enumeration(closed = true), schema.argument("interface")?.type)
        assertEquals(mapOf("lo" to "lo", "ether1" to "ether1"), schema.argument("interface")?.labels)
        assertEquals(listOf("lo", "ether1"), schema.argument("several")?.choices)
        assertEquals(ValueType.Enumeration(multiple = true, closed = true), schema.argument("several")?.type)
        assertTrue(schema.argument("address")?.choices.orEmpty().isEmpty())
        assertTrue(schema.argument("count")?.choices.orEmpty().isEmpty())
        assertEquals(listOf("all"), windows.schema(MenuPath.parse("native/22/q1"), PackNative(listOf(22), emptyList(), query = query)).argument("interface")?.choices)
    }

    @Test
    fun aRouterThatDoesNotImplementTheQueryGivesTheConsolePathBackAndAHiddenWindowKeepsItsPath() = runTest {
        val console = PackFallback(PackPath(columns = listOf("seq")))
        val refused = DeviceException(ErrorCategory.Unsupported, "not implemented")
        val replaced = spec.copy(query = spec.query?.copy(fallback = console))
        val windows = windows(Objects(ArrayList(), refusal = refused))
        windows.keep(listOf(Pack(menu = listOf("Tools", "Echo"), window = PackWindow("Echo", listOf(PackTab("Echo", "tool/echo"))), paths = mapOf("tool/echo" to PackPath(native = replaced), "native/9/q1" to PackPath(native = spec)))))
        assertTrue(windows.stands(MenuPath.parse("tool/echo")))
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { windows.query(spec, emptyMap()).toList() }.category)
        assertTrue(windows.stands(MenuPath.parse("tool/echo")), "a window without a console path is the only one there is")
        assertFailsWith<DeviceException> { windows.query(replaced, emptyMap()).toList() }
        assertFalse(windows.stands(MenuPath.parse("tool/echo")))
        assertTrue(windows.stands(MenuPath.parse("native/9/q1")))
    }

    @Test
    fun aRunIsNotReadOnTheChannelThatReplacedTheOneItStartedOnAndTheNewChannelIsLeftAlone() = runTest {
        val calls = ArrayList<String>()
        val made = ArrayList<Link>()
        val windows = NativeWindows(links(calls, made), { 8291 }, 1.seconds, testTimeSource)
        val ended = async { runCatching { windows.query(spec, emptyMap()).collect { } }.exceptionOrNull() }
        runCurrent()
        assertEquals(listOf("c1 start"), calls)
        made[0].failTable = true
        assertFailsWith<DeviceException> { windows.count(spec) }
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(ErrorCategory.ConnectionLost, (ended.await() as DeviceException).category)
        assertEquals(1, made.size)
        assertEquals(0, windows.count(spec))
        assertFalse(made[1].dead)
    }

    @Test
    fun aStartThatFindsTheChannelClosedByTheRouterIsMadeOnceMoreOnANewChannel() = runTest {
        val calls = ArrayList<String>()
        val made = ArrayList<Link>()
        val windows = NativeWindows(links(calls, made) { at, link -> link.failStart = at == 0 }, { 8291 }, 1.seconds, testTimeSource)
        assertEquals(listOf("*0"), windows.query(spec, emptyMap()).take(1).toList().map { it.id })
        assertEquals(2, made.size)
        assertEquals(listOf("c2 start", "c2 cancel"), calls)
    }

    @Test
    fun aDropIsForgottenWhenTheRouterAnswersTheHandlerAgain() = runTest {
        val made = ArrayList<Link>()
        val windows = NativeWindows(links(ArrayList(), made) { at, link -> link.failTable = at == 0 }, { 8291 }, 1.seconds, testTimeSource)
        assertFailsWith<DeviceException> { windows.count(spec) }
        windows.query(spec, emptyMap()).take(1).toList()
        made[1].failTable = true
        assertFailsWith<DeviceException> { windows.count(spec) }
        advanceTimeBy(6_000)
        assertEquals(0, windows.count(spec))
    }

    @Test
    fun aLogThatLosesItsChannelOnceIsReadFromANewOneAndNotGivenToTheConsole() = runTest {
        val made = ArrayList<Link>()
        val windows = NativeWindows(links(ArrayList(), made) { at, link -> link.breaksLines = at == 0 }, { 8291 }, 1.seconds, testTimeSource)
        val shown = ArrayList<ListEvent>()
        val console = flowOf<ListEvent>(ListEvent.Snapshot(listOf(Item("*9", mapOf("seq" to "console")))))
        val job = launch { windows.lines(spec, console) { 0 }.collect { shown += it } }
        advanceTimeBy(2_500)
        runCurrent()
        job.cancel()
        assertEquals(2, made.size)
        assertTrue(shown.isNotEmpty() && shown.all { event -> (event as ListEvent.Snapshot).items.map { it.id } == listOf("*1") })
    }

    @Test
    fun aWindowStoppedBeforeTheRouterAnswersTheStartCancelsTheRunWhenTheAnswerComes() = runTest {
        val calls = ArrayList<String>()
        val gate = CompletableDeferred<Unit>()
        val windows = NativeWindows(links(calls, ArrayList(), gate), { 8291 }, 1.seconds, testTimeSource)
        val job = launch { windows.query(spec, emptyMap()).collect { } }
        runCurrent()
        job.cancel()
        runCurrent()
        gate.complete(Unit)
        job.join()
        assertEquals(listOf("c1 start", "c1 cancel"), calls)
    }

    @Test
    fun aQueryTheRouterDoesNotAnswerForDoesNotTakeTheTableOfTheSameHandlerWithIt() = runTest {
        val objects = object : NativeObjects {
            override suspend fun read(spec: PackNative, limit: Int?): List<Item>? = emptyList()

            override suspend fun answers(spec: PackNative) = spec.query == null

            override fun close() = Unit
        }
        val table = Pack(
            menu = listOf("Table"), window = PackWindow("Table", listOf(PackTab("Table", "native/22/1"))),
            paths = mapOf("native/22/1" to PackPath(native = PackNative(listOf(22), listOf(PackNativeField("seq", 0xF, "number"))))),
        )
        assertEquals(listOf(listOf("Table")), windows(objects).alive(listOf(packOf("native/22/q1"), table)).map { it.menu })
    }

    @Test
    fun aQueryStandsUnderThePathOfTheConsoleWindowItTookThePlaceOf() = runTest {
        val windows = windows(Objects(ArrayList()))
        windows.keep(listOf(packOf("tool/echo")))
        assertTrue(windows.stands(MenuPath.parse("tool/echo")))
        assertFalse(windows.stands(MenuPath.parse("tool/other")))
        windows.keep(listOf(Pack(menu = listOf("IP"), window = PackWindow("IP", listOf(PackTab("IP", "ip/pool"))), paths = mapOf("ip/pool" to PackPath(native = PackNative(listOf(1), emptyList()))))))
        assertFalse(windows.stands(MenuPath.parse("ip/pool")))
    }

    @Test
    fun aQueryTheRouterDoesNotKnowIsLeftOutAndTheConsoleWindowStays() = runTest {
        val alive = windows(Objects(ArrayList(), answers = true)).alive(listOf(packOf("native/22/q1")))
        assertEquals(1, alive.size)
        assertTrue(windows(Objects(ArrayList(), answers = false)).alive(listOf(packOf("native/22/q1"))).isEmpty())
    }
}
