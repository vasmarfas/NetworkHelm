package com.vasmarfas.networkhelm.model

private val commandPolicies = mapOf(
    "ping" to listOf("test"),
    "tool/ping" to listOf("test"),
    "tool/traceroute" to listOf("test"),
    "tool/torch" to listOf("test", "sniff"),
    "tool/sniffer/start" to listOf("test", "sniff"),
    "tool/sniffer/stop" to listOf("test", "sniff"),
    "tool/sniffer/quick" to listOf("test", "sniff"),
    "tool/profile" to listOf("test"),
    "tool/ip-scan" to listOf("test"),
    "tool/mac-scan" to listOf("test"),
    "tool/bandwidth-test" to listOf("test"),
    "tool/flood-ping" to listOf("test"),
    "tool/ping-speed" to listOf("test"),
    "tool/wol" to listOf("test"),
    "tool/fetch" to listOf("test"),
    "tool/e-mail/send" to listOf("test"),
    "tool/traffic-generator/start" to listOf("test"),
    "system/backup/save" to listOf("policy", "test"),
    "system/backup/load" to listOf("policy", "test"),
    "system/reboot" to listOf("reboot"),
    "system/shutdown" to listOf("reboot"),
    "password" to listOf("password"),
)

private val changePolicies = mapOf("user" to listOf("policy"))

fun DeviceInfo.lacks(path: MenuPath): List<String> {
    if (platform != Platform.RouterOs) return emptyList()
    val menu = path.toString()
    val needed = commandPolicies[menu] ?: changePolicies.entries.firstOrNull { (top, _) -> menu == top || menu.startsWith("$top/") }?.value.orEmpty()
    return needed.filterNot { it in policies }
}
