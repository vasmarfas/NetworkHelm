package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.Item
import kotlin.math.abs

internal class Rutos(private val shell: SshUbus?, tools: Set<String>) {

    val lists: Map<String, Live> = listOfNotNull(
        ("status/mobile" to Live(FIELDS, { listOf(Item("modem", modem())) }, settings = true)).takeIf { shell != null && "gsmctl" in tools },
    ).toMap()

    private suspend fun modem(): Map<String, String> {
        val answers = shell?.sh(SCRIPT)?.output.orEmpty().split(SECTION).drop(1).associate { it.substringBefore("--") to it.substringAfter("--").trim() }
        return buildMap {
            for ((option, field) in NAMED) answers[option]?.takeIf { it.isNotEmpty() }?.let { put(field, it) }
            for (line in answers["q"].orEmpty().lines()) {
                val name = line.substringBefore(':', "").trim().lowercase()
                if (name.isNotEmpty()) put(name, line.substringAfter(':').trim())
            }
            answers["F"]?.substringAfter(':')?.split(',')?.getOrNull(2)?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }?.let { put("primary-band", it) }
            answers["c"]?.takeIf { it.isNotEmpty() }?.let { put("temperature", it.toIntOrNull()?.let(::degrees) ?: it) }
        }
    }

    private fun degrees(tenths: Int) = "${if (tenths < 0) "-" else ""}${abs(tenths) / 10}.${abs(tenths) % 10}"

    private companion object {
        const val SECTION = "--rd-"
        const val SCRIPT = "for o in o t g z q C F i J x m y c; do echo \"$SECTION\$o--\"; gsmctl -\$o 2>/dev/null; done"
        val NAMED = listOf(
            "o" to "current-operator", "t" to "data-class", "g" to "registration-status", "z" to "sim-status", "C" to "current-cellid",
            "i" to "imei", "J" to "iccid", "x" to "imsi", "m" to "model", "y" to "revision",
        )
        val FIELDS = listOf(
            "current-operator", "data-class", "registration-status", "sim-status", "rssi", "rsrp", "rsrq", "sinr", "rscp", "ecio", "current-cellid",
            "primary-band", "imei", "iccid", "imsi", "model", "revision", "temperature",
        )
    }
}
