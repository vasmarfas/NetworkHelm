package com.vasmarfas.networkhelm.opnsense

import com.vasmarfas.networkhelm.model.hostPort

internal fun systemLine(values: Map<String, String>): Map<String, String> = values + mapOf(
    "time" to values["timestamp"].orEmpty().replace('T', ' '),
    "program" to values["process_name"].orEmpty(),
    "message" to values["line"].orEmpty().trim(),
)

internal fun packet(values: Map<String, String>, names: Map<String, String>): Map<String, String> {
    val device = values["interface"].orEmpty()
    return values + mapOf(
        "time" to values["__timestamp__"].orEmpty().replace('T', ' '),
        "interface" to (names[device] ?: device),
        "direction" to values["dir"].orEmpty(),
        "protocol" to values["protoname"].orEmpty(),
        "source" to hostPort(values["src"].orEmpty(), values["srcport"]),
        "destination" to hostPort(values["dst"].orEmpty(), values["dstport"]),
        "flags" to values["tcpflags"].orEmpty(),
        "rule" to values["label"].orEmpty(),
    )
}
