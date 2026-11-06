package com.vasmarfas.networkhelm.nettools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.TimeSource

private const val LATENCY_SAMPLES = 6
private const val META_BYTES = 64 * 1024
private const val DOWNLOAD_BYTES = 50_000_000
private const val UPLOAD_BYTES = 2_000_000
private const val TICK_MILLIS = 250L
private const val LIVE_MILLIS = 1_000L

enum class SpeedPhase { Latency, Download, Upload }

// what the server says of itself: the data centre that serves the test and the network it sees us in
class SpeedMeta(val colo: String?, val network: String?)

class SpeedLatency(val medianMillis: Double, val jitterMillis: Double)

sealed interface SpeedEvent {
    class Place(val meta: SpeedMeta) : SpeedEvent
    class Running(val phase: SpeedPhase, val mbps: Double?) : SpeedEvent
    class Latency(val latency: SpeedLatency) : SpeedEvent
    class Download(val mbps: Double) : SpeedEvent
    class Upload(val mbps: Double) : SpeedEvent
}

// the streams report their pieces from several threads: every byte counts into the live speed,
// those that came after the warm-up into the result, which spans from the end of the warm-up to the last piece
class SpeedMeter(private val warmupMillis: Long) {
    private class Kept(val bytes: Long, val lastMillis: Long)

    private val seen = MutableStateFlow(0L)
    private val kept = MutableStateFlow(Kept(0, 0))

    val total: Long get() = seen.value

    fun add(atMillis: Long, bytes: Int) {
        seen.update { it + bytes }
        if (atMillis >= warmupMillis) kept.update { Kept(it.bytes + bytes, maxOf(it.lastMillis, atMillis)) }
    }

    fun result(): Double = kept.value.let { speedOf(it.bytes, it.lastMillis - warmupMillis) }
}

fun speedOf(bytes: Long, millis: Long): Double = if (bytes <= 0 || millis <= 0) 0.0 else bytes * 8.0 / millis / 1000.0

// samples are (millis, bytes seen so far); the speed is taken over the last second
fun recentSpeed(samples: List<Pair<Long, Long>>, windowMillis: Long = LIVE_MILLIS): Double {
    val last = samples.lastOrNull() ?: return 0.0
    val first = samples.first { last.first - it.first <= windowMillis }
    return speedOf(last.second - first.second, last.first - first.first)
}

// the first sample pays for the TCP and TLS handshake and is left out; the jitter is the mean step between neighbours
fun latencyOf(samples: List<Double>): SpeedLatency? {
    val steady = samples.drop(1).takeIf { it.isNotEmpty() } ?: return null
    val sorted = steady.sorted()
    val middle = sorted.size / 2
    val median = if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    val steps = steady.zipWithNext { a, b -> abs(b - a) }
    return SpeedLatency(median, if (steps.isEmpty()) 0.0 else steps.average())
}

fun speedBase(text: String): String = text.trim().trimEnd('/').let { if ("://" in it) it else "https://$it" }

fun parseSpeedMeta(text: String): SpeedMeta? {
    val json = parseObject(text) ?: return null
    val network = listOfNotNull(json.string("asn")?.let { "AS$it" }, json.string("asOrganization")).joinToString(" ").ifEmpty { null }
    return SpeedMeta(json.string("colo"), network)
}

// the endpoints of speed.cloudflare.com under any base: /meta, /__down?bytes=N and /__up;
// the streams run for the warm-up plus the window and are cut off then, so nothing is left running behind a stopped test
fun speedTest(
    http: PublicHttp,
    base: String,
    streams: Int = 4,
    warmupMillis: Long = 500,
    windowMillis: Long = 8_000,
    clock: TimeSource = TimeSource.Monotonic,
): Flow<SpeedEvent> = channelFlow {
    val host = hostOf(base)
    send(SpeedEvent.Running(SpeedPhase.Latency, null))
    // /meta is an extra: a server without it, or with a web page in its place, is measured all the same
    val meta = try {
        http.get("$base/meta", limit = META_BYTES).takeIf { it.status in 200..299 }?.let { parseSpeedMeta(it.body.decodeToString()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    meta?.let { send(SpeedEvent.Place(it)) }
    val samples = List(LATENCY_SAMPLES) {
        val started = clock.markNow()
        val reply = http.get("$base/__down?bytes=0")
        if (reply.status !in 200..299) throw HttpStatusException(host, reply.status)
        started.elapsedNow().inWholeMicroseconds / 1000.0
    }
    latencyOf(samples)?.let { send(SpeedEvent.Latency(it)) }
    measure(SpeedPhase.Download, streams, warmupMillis, windowMillis, clock, SpeedEvent::Download) { onBytes ->
        while (true) {
            var got = 0L
            val status = http.download("$base/__down?bytes=$DOWNLOAD_BYTES") {
                got += it
                onBytes(it)
            }
            if (status !in 200..299) throw HttpStatusException(host, status)
            if (got == 0L) break
        }
    }
    measure(SpeedPhase.Upload, streams, warmupMillis, windowMillis, clock, SpeedEvent::Upload) { onBytes ->
        while (true) {
            val status = http.upload("$base/__up", UPLOAD_BYTES, onBytes = onBytes)
            if (status !in 200..299) throw HttpStatusException(host, status)
        }
    }
}

private suspend fun SendChannel<SpeedEvent>.measure(
    phase: SpeedPhase,
    streams: Int,
    warmupMillis: Long,
    windowMillis: Long,
    clock: TimeSource,
    done: (Double) -> SpeedEvent,
    stream: suspend (onBytes: (Int) -> Unit) -> Unit,
) {
    val started = clock.markNow()
    val meter = SpeedMeter(warmupMillis)
    send(SpeedEvent.Running(phase, null))
    coroutineScope {
        val ticker = launch {
            val samples = ArrayList<Pair<Long, Long>>()
            while (true) {
                delay(TICK_MILLIS)
                samples += started.elapsedNow().inWholeMilliseconds to meter.total
                send(SpeedEvent.Running(phase, recentSpeed(samples)))
            }
        }
        withTimeoutOrNull(warmupMillis + windowMillis) {
            coroutineScope {
                repeat(streams) { launch { stream { bytes -> meter.add(started.elapsedNow().inWholeMilliseconds, bytes) } } }
            }
        }
        ticker.cancel()
    }
    send(done(meter.result()))
}
