package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.HttpResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedTestTest {

    // every stream hands over perChunk bytes each everyMillis of the clock and never ends; a request for the small answers takes 10 ms
    private class Link(private val perChunk: Int, private val everyMillis: Long) : PublicHttp {
        var started = 0
        var active = 0

        override suspend fun get(url: String, headers: Map<String, String>, timeoutMillis: Int, limit: Int): HttpResponse {
            delay(10)
            return if (url.endsWith("/meta")) cannedReply(body = META) else cannedReply()
        }

        override suspend fun download(url: String, timeoutMillis: Int, onBytes: (Int) -> Unit): Int = stream(onBytes)

        override suspend fun upload(url: String, size: Int, timeoutMillis: Int, onBytes: (Int) -> Unit): Int = stream(onBytes)

        private suspend fun stream(onBytes: (Int) -> Unit): Nothing {
            started++
            active++
            try {
                while (true) {
                    delay(everyMillis)
                    onBytes(perChunk)
                }
            } finally {
                active--
            }
        }
    }

    @Test
    fun aSpeedIsBitsOverTheTimeTheyTook() {
        assertEquals(10.0, speedOf(1_250_000, 1_000))
        assertEquals(0.0, speedOf(0, 1_000))
        assertEquals(0.0, speedOf(1_000, 0))
        assertEquals(0.0, speedOf(1_000, -5))
    }

    @Test
    fun theMeterLeavesTheWarmupOutOfTheResult() {
        val meter = SpeedMeter(warmupMillis = 500)
        meter.add(100, 500_000)
        meter.add(500, 125_000)
        meter.add(1_000, 625_000)
        meter.add(1_500, 625_000)
        assertEquals(1_875_000L, meter.total)
        assertEquals(11.0, meter.result(), "1 375 000 bytes in the second between the end of the warm-up and the last piece")
        assertEquals(0.0, SpeedMeter(500).result())
        val early = SpeedMeter(500)
        early.add(200, 1_000_000)
        assertEquals(0.0, early.result(), "nothing came after the warm-up")
    }

    @Test
    fun theLiveSpeedLooksAtTheLastSecond() {
        val samples = listOf(0L to 0L, 250L to 1_250_000L, 500L to 2_500_000L, 750L to 3_750_000L, 1_000L to 5_000_000L, 1_250L to 5_000_000L)
        assertEquals(30.0, recentSpeed(samples))
        assertEquals(0.0, recentSpeed(samples.take(1)))
        assertEquals(0.0, recentSpeed(emptyList()))
        assertEquals(40.0, recentSpeed(samples.take(5), windowMillis = 500))
    }

    @Test
    fun theLatencyDropsTheFirstSampleAndTakesTheMedianAndTheMeanStep() {
        val latency = assertIs<SpeedLatency>(latencyOf(listOf(50.0, 10.0, 12.0, 11.0, 30.0, 10.0)))
        assertEquals(11.0, latency.medianMillis)
        assertEquals(10.5, latency.jitterMillis, "steps of 2, 1, 19 and 20")
        val even = assertIs<SpeedLatency>(latencyOf(listOf(99.0, 10.0, 20.0)))
        assertEquals(15.0, even.medianMillis)
        assertEquals(10.0, even.jitterMillis)
        assertEquals(0.0, assertIs<SpeedLatency>(latencyOf(listOf(99.0, 7.0))).jitterMillis)
        assertNull(latencyOf(listOf(99.0)))
        assertNull(latencyOf(emptyList()))
    }

    @Test
    fun theMetaNamesTheDataCentreAndTheNetwork() {
        val parsed = assertIs<SpeedMeta>(parseSpeedMeta(META))
        assertEquals("FRA", parsed.colo)
        assertEquals("AS64496 Example ISP", parsed.network)
        val bare = assertIs<SpeedMeta>(parseSpeedMeta("""{"colo":"AMS"}"""))
        assertEquals("AMS", bare.colo)
        assertNull(bare.network)
        assertNull(parseSpeedMeta("<html>not a speed server</html>"))
    }

    @Test
    fun aBaseGetsItsSchemeAndLosesTheSlash() {
        assertEquals("https://speed.cloudflare.com", speedBase(" speed.cloudflare.com/ "))
        assertEquals("http://192.168.1.10:3000", speedBase("http://192.168.1.10:3000/"))
    }

    @Test
    fun aTestMeasuresLatencyThenDownloadThenUploadAndLeavesNothingRunning() = runTest {
        val link = Link(perChunk = 12_500, everyMillis = 10)
        val events = speedTest(link, "https://speed.example.test", streams = 2, warmupMillis = 500, windowMillis = 2_000, clock = testTimeSource).toList()
        assertEquals(listOf("Latency"), events.filterIsInstance<SpeedEvent.Running>().take(1).map { it.phase.name })
        assertEquals("FRA", events.filterIsInstance<SpeedEvent.Place>().single().meta.colo)
        val latency = events.filterIsInstance<SpeedEvent.Latency>().single().latency
        assertEquals(10.0, latency.medianMillis)
        assertEquals(0.0, latency.jitterMillis)
        val download = events.filterIsInstance<SpeedEvent.Download>().single().mbps
        val upload = events.filterIsInstance<SpeedEvent.Upload>().single().mbps
        assertTrue(download in 19.5..20.5, "two streams of 10 Mbit/s, $download")
        assertTrue(upload in 19.5..20.5, "two streams of 10 Mbit/s, $upload")
        val order = events.mapNotNull {
            when (it) {
                is SpeedEvent.Place -> "place"
                is SpeedEvent.Running -> null
                is SpeedEvent.Latency -> "latency"
                is SpeedEvent.Download -> "download"
                is SpeedEvent.Upload -> "upload"
            }
        }
        assertEquals(listOf("place", "latency", "download", "upload"), order)
        val live = events.filterIsInstance<SpeedEvent.Running>().filter { it.phase == SpeedPhase.Download }.mapNotNull { it.mbps }
        assertTrue(20.0 in live, "the live speed of the download, $live")
        assertEquals(4, link.started, "two streams in each of the two phases")
        assertEquals(0, link.active)
    }

    @Test
    fun stoppingTheCollectorCutsEveryStream() = runTest {
        val link = Link(perChunk = 12_500, everyMillis = 10)
        val running = launch { speedTest(link, "https://speed.example.test", streams = 3, windowMillis = 5_000, clock = testTimeSource).collect { } }
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, link.active)
        running.cancelAndJoin()
        assertEquals(0, link.active)
    }

    @Test
    fun aServerThatRefusesTheTransferIsNamedWithItsStatus() = runTest {
        val refusing = object : PublicHttp {
            override suspend fun get(url: String, headers: Map<String, String>, timeoutMillis: Int, limit: Int) = cannedReply()

            override suspend fun download(url: String, timeoutMillis: Int, onBytes: (Int) -> Unit) = 403

            override suspend fun upload(url: String, size: Int, timeoutMillis: Int, onBytes: (Int) -> Unit) = 403
        }
        val failure = assertFailsWith<HttpStatusException> { speedTest(refusing, "https://speed.example.test", clock = testTimeSource).toList() }
        assertEquals("speed.example.test", failure.host)
        assertEquals(403, failure.status)
    }

    @Test
    fun aServerWithoutMetaIsMeasuredAllTheSame() = runTest {
        val bare = object : PublicHttp {
            override suspend fun get(url: String, headers: Map<String, String>, timeoutMillis: Int, limit: Int) = if (url.endsWith("/meta")) cannedReply(404) else cannedReply()

            override suspend fun download(url: String, timeoutMillis: Int, onBytes: (Int) -> Unit) = 200

            override suspend fun upload(url: String, size: Int, timeoutMillis: Int, onBytes: (Int) -> Unit): Int {
                delay(100)
                return 200
            }
        }
        val events = speedTest(bare, "https://speed.example.test", streams = 1, clock = testTimeSource).toList()
        assertTrue(events.none { it is SpeedEvent.Place })
        assertEquals(0.0, events.filterIsInstance<SpeedEvent.Download>().single().mbps, "a transfer that carries nothing ends at once")
        assertEquals(0.0, events.filterIsInstance<SpeedEvent.Upload>().single().mbps)
    }

    private companion object {
        const val META = """{"hostname":"speed.cloudflare.com","clientIp":"203.0.113.9","httpProtocol":"HTTP/2","asn":64496,"asOrganization":"Example ISP","colo":"FRA","country":"DE","city":"Frankfurt"}"""
    }
}
