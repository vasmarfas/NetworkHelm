package com.vasmarfas.networkhelm.ui.log

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.session.watchSettings
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

enum class TimeStyle { Short, Full, Relative }

@Serializable
class LogLayout(
    val times: TimeStyle = TimeStyle.Short,
    val wrap: Boolean = false,
    val collapse: Boolean = false,
    val onlyMatches: Boolean = true,
    val pane: Float = 200f,
    val panel: Boolean = false,
)

@Stable
class LogState {
    var live by mutableStateOf(true)
    var severities by mutableStateOf(emptySet<Severity>())
    var topics by mutableStateOf(emptySet<String>())
    var hidden by mutableStateOf(emptySet<String>())
    var message by mutableStateOf<String?>(null)
    var span by mutableStateOf(Span.Any)
    var times by mutableStateOf(TimeStyle.Short)
    var wrap by mutableStateOf(false)
    var collapse by mutableStateOf(false)

    var onlyMatches by mutableStateOf(true)

    var details by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<String>())

    var anchor by mutableStateOf<String?>(null)
    var caret by mutableStateOf<String?>(null)

    fun restore(layout: LogLayout) {
        times = layout.times
        wrap = layout.wrap
        collapse = layout.collapse
        onlyMatches = layout.onlyMatches
        details = layout.panel
    }

    fun layout(pane: Float) = LogLayout(times, wrap, collapse, onlyMatches, pane, details)

    val narrowed: Boolean get() = severities.isNotEmpty() || topics.isNotEmpty() || hidden.isNotEmpty() || message != null || span != Span.Any

    fun clearNarrowing() {
        severities = emptySet()
        topics = emptySet()
        hidden = emptySet()
        message = null
        span = Span.Any
    }

    fun choose(keys: Set<String>, anchor: String?, caret: String? = anchor) {
        selected = keys
        this.anchor = anchor
        this.caret = caret
    }

    fun toggle(severity: Severity) {
        severities = if (severity in severities) severities - severity else severities + severity
    }

    fun toggleTopic(topic: String) {
        topics = if (topic in topics) topics - topic else topics + topic
        hidden -= topic
    }

    fun toggleHidden(topic: String) {
        hidden = if (topic in hidden) hidden - topic else hidden + topic
        topics -= topic
    }
}

class RouterClock(val today: String, val skew: Long) {
    val now: Long get() = Clock.System.now().epochSeconds + skew
}

private val CLOCK = MenuPath.parse("system/clock")
private val RESOURCE = MenuPath.parse("status/resource")

internal fun localClock(): RouterClock {
    val instant = Clock.System.now()
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(value: Int) = value.toString().padStart(2, '0')
    val today = local.date.toString()
    val here = stampOf("$today ${two(local.time.hour)}:${two(local.time.minute)}:${two(local.time.second)}", null)
    return RouterClock(today, (here?.seconds ?: instant.epochSeconds) - instant.epochSeconds)
}

internal suspend fun readClock(session: DeviceSession): RouterClock {
    // OpenWrt has no system/clock, and asking for it costs the whole timeout
    for (path in if (session.device.platform == Platform.OpenWrt) listOf(RESOURCE, CLOCK) else listOf(CLOCK, RESOURCE)) {
        val reported = try {
            withTimeoutOrNull(3.seconds) { session.watchSettings(path, listOf("date", "time")).firstOrNull() }
        } catch (e: DeviceException) {
            null
        }
        val stamp = reported?.let { values -> stampOf(listOfNotNull(values["date"], values["time"]).joinToString(" "), null) } ?: continue
        return RouterClock(stamp.date, stamp.seconds - Clock.System.now().epochSeconds)
    }
    return localClock()
}
