package com.vasmarfas.networkhelm.offline

import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.openwrt.LIST
import com.vasmarfas.networkhelm.openwrt.UciSection
import com.vasmarfas.networkhelm.routeros.api.ApiEncoding

fun uciConfig(configs: Map<String, List<UciSection>>): Config = Config(
    configs.flatMap { (config, sections) ->
        sections.groupBy { it.type }.map { (type, ofType) ->
            MenuPath(listOf(config, type)) to ConfigMenu(
                NodeKind.List,
                ofType.map { section ->
                    val values = section.options + section.lists.mapValues { it.value.joinToString(LIST) }
                    Item(section.name, if (section.anonymous) values else values + (NAME to section.name))
                },
            )
        }
    }.toMap(),
)

// the rest of the archive, passwords included, is not read
fun backupExport(bytes: ByteArray): String =
    untar(gunzip(bytes)).filterKeys { it.startsWith("etc/config/") && it.count { char -> char == '/' } == 2 }.entries
        .joinToString("\n") { (path, content) -> "package ${path.substringAfterLast('/')}\n\n" + ApiEncoding.Utf8.decode(content).trimEnd() + "\n" }

fun openWrtDevice(config: Config, file: String) = DeviceInfo(
    Platform.OpenWrt,
    config.menus[MenuPath.parse("system/system")]?.items?.firstOrNull()?.get("hostname") ?: file.substringBefore('.'),
    "",
    "",
    "",
    emptySet(),
    setOf("read"),
)

private const val NAME = ".name"
