package com.vasmarfas.networkhelm.model

private val deviceModeMenus = mapOf(
    "bandwidth-test" to listOf("tool/bandwidth-test", "tool/bandwidth-server", "tool/speed-test"),
    "container" to listOf("container"),
    "email" to listOf("tool/e-mail"),
    "fetch" to listOf("tool/fetch"),
    "hotspot" to listOf("ip/hotspot"),
    "ipsec" to listOf("ip/ipsec"),
    "l2tp" to listOf("interface/l2tp-client", "interface/l2tp-server", "interface/l2tp-ether"),
    "pptp" to listOf("interface/pptp-client", "interface/pptp-server"),
    "proxy" to listOf("ip/proxy"),
    "romon" to listOf("tool/romon"),
    "scheduler" to listOf("system/scheduler"),
    "smb" to listOf("ip/smb"),
    "sniffer" to listOf("tool/sniffer"),
    "socks" to listOf("ip/socks"),
    "traffic-gen" to listOf("tool/traffic-generator", "tool/flood-ping", "tool/ping-speed"),
    "zerotier" to listOf("zerotier"),
)

fun DeviceInfo.closedBy(path: MenuPath): String? {
    val menu = path.toString()
    return deviceModeMenus.entries.firstOrNull { (property, menus) ->
        deviceMode[property].let { it == "false" || it == "no" } && menus.any { menu == it || menu.startsWith("$it/") }
    }?.key
}
