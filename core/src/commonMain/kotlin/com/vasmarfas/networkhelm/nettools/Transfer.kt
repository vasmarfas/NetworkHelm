package com.vasmarfas.networkhelm.nettools

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

enum class SizeUnit(val symbol: String, val bits: Double) {
    B("B", 8.0),
    KB("kB", 8e3),
    MB("MB", 8e6),
    GB("GB", 8e9),
    TB("TB", 8e12),
    KiB("KiB", 8.0 * 1024),
    MiB("MiB", 8.0 * 1024 * 1024),
    GiB("GiB", 8.0 * 1024 * 1024 * 1024),
    TiB("TiB", 8.0 * 1024 * 1024 * 1024 * 1024),
}

enum class SpeedUnit(val symbol: String, val bitsPerSecond: Double) {
    Bit("bit/s", 1.0),
    Kbit("kbit/s", 1e3),
    Mbit("Mbit/s", 1e6),
    Gbit("Gbit/s", 1e9),
    Bps("B/s", 8.0),
    KBps("kB/s", 8e3),
    MBps("MB/s", 8e6),
    GBps("GB/s", 8e9),
}

// overhead is the share of the line speed, in per cent, that goes to headers and retransmissions instead of data
object Transfer {
    val references: List<Pair<Double, SpeedUnit>> = listOf(10.0 to SpeedUnit.Mbit, 100.0 to SpeedUnit.Mbit, 1.0 to SpeedUnit.Gbit)

    private val number = Regex("""\d+[.,]?\d*|[.,]\d+""")

    private fun valid(overhead: Double, vararg values: Double) = values.all { it > 0 } && overhead >= 0 && overhead < 100

    private fun payload(overhead: Double) = (100 - overhead) / 100

    fun seconds(size: Double, sizeUnit: SizeUnit, speed: Double, speedUnit: SpeedUnit, overhead: Double = 0.0): Double? =
        if (valid(overhead, size, speed)) size * sizeUnit.bits / (speed * speedUnit.bitsPerSecond * payload(overhead)) else null

    fun speed(size: Double, sizeUnit: SizeUnit, seconds: Double, speedUnit: SpeedUnit, overhead: Double = 0.0): Double? =
        if (valid(overhead, size, seconds)) size * sizeUnit.bits / seconds / payload(overhead) / speedUnit.bitsPerSecond else null

    fun size(speed: Double, speedUnit: SpeedUnit, seconds: Double, sizeUnit: SizeUnit, overhead: Double = 0.0): Double? =
        if (valid(overhead, speed, seconds)) speed * speedUnit.bitsPerSecond * payload(overhead) * seconds / sizeUnit.bits else null

    // a plain decimal with a point or a comma; what a double parser also takes (1e5, NaN, 5d) is refused
    fun amount(text: String): Double? =
        text.trim().takeIf { number.matches(it) }?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }

    // empty fields count as nothing, but all three empty is no time at all
    fun time(hours: String, minutes: String, seconds: String): Double? {
        val parts = listOf(hours to 3600.0, minutes to 60.0, seconds to 1.0).filter { it.first.isNotBlank() }
        if (parts.isEmpty()) return null
        var total = 0.0
        for ((text, unit) in parts) total += (amount(text) ?: return null) * unit
        return total
    }

    // four significant digits, never fewer than all the whole ones; for numbers above zero
    fun rounded(value: Double): String {
        if (!(value > 0) || value >= 1e15) return value.toString()
        val decimals = (3 - floor(log10(value)).toInt()).coerceIn(0, 12)
        val scale = 10.0.pow(decimals).toLong()
        val scaled = (value * scale).roundToLong()
        val fraction = (scaled % scale).toString().padStart(decimals, '0').trimEnd('0')
        return if (fraction.isEmpty()) "${scaled / scale}" else "${scaled / scale}.$fraction"
    }

    // hours, minutes and seconds from a minute on; below that the seconds alone
    fun timeText(seconds: Double, hour: String, minute: String, second: String): String {
        if (seconds < 60 || seconds >= 1e15) return "${rounded(seconds)} $second"
        val whole = seconds.roundToLong()
        val hours = whole / 3600
        return listOfNotNull(if (hours > 0) "$hours $hour" else null, "${whole % 3600 / 60} $minute", "${whole % 60} $second").joinToString(" ")
    }
}
