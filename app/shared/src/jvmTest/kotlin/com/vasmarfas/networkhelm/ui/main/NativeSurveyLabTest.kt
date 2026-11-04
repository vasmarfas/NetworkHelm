package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.miknative.connectNative
import com.vasmarfas.networkhelm.miknative.connectNativeHost
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsProtocol
import com.vasmarfas.networkhelm.routeros.connectRouterOs
import com.vasmarfas.networkhelm.routeros.console.Dialect
import com.vasmarfas.networkhelm.routeros.console.connectRouterOsConsole
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import java.util.Properties
import kotlin.test.Test
import kotlin.time.TimeSource

class NativeSurveyLabTest {
    private val volatile = Regex("byte|packet|bits|rate|uptime|time|last|load|free|used|idle|drop|error|count|expire|age|seen|since|signal|cpu|memory|rx|tx|fp-|cache|ttl|timeout|bytes|queue|status|bssid|noise|tcp|udp|rtt|loss|jitter|sent|received|min|max|avg")

    private fun lab(): Properties {
        val file = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("lab.properties is missing", file != null)
        assumeTrue("not asked for", System.getProperty("networkhelm.survey") != null)
        Locale.setDefault(Locale.ENGLISH)
        return Properties().apply { file?.inputStream()?.use(::load) }
    }

    private fun paths(): List<String> = bundledPacks().values.filter { it.platform == "routeros" }
        .flatMap { it.menu + it.paths.keys }.map { it.trim('/') }.filter { it.isNotEmpty() }.distinct().sorted()

    private suspend fun items(session: DeviceSession, path: String): Result<List<Item>?> = runCatching {
        withTimeout(40_000) {
            val menu = MenuPath.parse(path)
            when (session.describe(menu).kind) {
                NodeKind.List, NodeKind.Settings -> (session.watch(menu).first() as ListEvent.Snapshot).items
                else -> null
            }
        }
    }

    private fun around(api: String, console: String): String {
        val at = api.zip(console).indexOfFirst { it.first != it.second }.let { if (it < 0) minOf(api.length, console.length) else it }
        fun cut(text: String) = text.drop(maxOf(0, at - 12)).take(32).replace("\r", "\\r").replace("\n", "\\n")
        return "[${cut(api)}] vs [${cut(console)}]"
    }

    private fun report(path: String, api: Result<List<Item>?>, console: Result<List<Item>?>): String {
        val a = api.getOrNull()
        val c = console.getOrNull()
        return when {
            api.isFailure && console.isFailure -> "ok   (both refuse) $path | api: ${api.exceptionOrNull()?.message?.take(70)} | console: ${console.exceptionOrNull()?.message?.take(70)}"
            api.isFailure -> "DIFF only the console answers: $path | api: ${api.exceptionOrNull()?.message?.take(100)}"
            console.isFailure -> "DIFF only the API answers: $path | console: ${console.exceptionOrNull()?.message?.take(140)}"
            a == null || c == null -> "ok   (no rows) $path"
            else -> {
                val problems = mutableListOf<String>()
                if (a.size != c.size) problems += "count ${a.size} vs ${c.size}"
                val apiById = a.associateBy { it.id }
                val consoleById = c.associateBy { it.id }
                val ids = apiById.keys - consoleById.keys
                if (ids.isNotEmpty()) problems += "ids only in the API: ${ids.take(3)}"
                val onlyConsole = consoleById.keys - apiById.keys
                if (onlyConsole.isNotEmpty()) problems += "ids only in the console: ${onlyConsole.take(3)}"
                val keysApi = a.flatMap { it.values.keys }.toSet()
                val keysConsole = c.flatMap { it.values.keys }.toSet()
                if ((keysApi - keysConsole).isNotEmpty()) problems += "missing in the console: ${(keysApi - keysConsole).sorted().map { key -> key + "=" + a.firstNotNullOfOrNull { it.values[key] }?.take(24) }}"
                if ((keysConsole - keysApi).isNotEmpty()) problems += "extra in the console: ${(keysConsole - keysApi).sorted().take(12)}"
                val values = mutableListOf<String>()
                for ((id, item) in apiById) {
                    val other = consoleById[id] ?: continue
                    for ((key, value) in item.values) {
                        if (volatile.containsMatchIn(key)) continue
                        val seen = other.values[key] ?: continue
                        if (seen != value && values.size < 4) values += "$key: " + around(value, seen)
                    }
                }
                if (values.isNotEmpty()) problems += "values $values"
                if (problems.isEmpty()) "ok   ${a.size} rows $path" else "DIFF $path (${a.size} rows): " + problems.joinToString("; ")
            }
        }
    }

    private fun survey(prefix: String, protocol: RouterOsProtocol, view: String, open: suspend (Properties, CoroutineScope) -> DeviceSession) = runBlocking {
        val lab = lab()
        val scope = CoroutineScope(Dispatchers.Default)
        val api = connectRouterOs(RouterOsLogin(lab.getProperty("$prefix.host"), lab.getProperty("$prefix.user"), lab.getProperty("$prefix.password"), protocol), TrustPolicy(MemoryTrustStore()) { true }, scope)
        val console = open(lab, scope)
        try {
            println("SURVEY [$prefix $view] ${console.device.version}: ${paths().size} paths")
            for (path in paths()) {
                val started = TimeSource.Monotonic.markNow()
                val finding = report(path, items(api, path), items(console, path))
                val took = started.elapsedNow().inWholeMilliseconds
                println("SURVEY [$prefix $view] " + finding + if (took > 5_000) " [took $took ms]" else "")
            }
        } finally {
            api.close()
            console.close()
            scope.cancel()
        }
    }

    private suspend fun overPort8291(lab: Properties, prefix: String, scope: CoroutineScope) =
        connectNativeHost(lab.getProperty("$prefix.host"), 8291, lab.getProperty("$prefix.user"), lab.getProperty("$prefix.password"), scope)

    private suspend fun overPort8291ByGet(lab: Properties, prefix: String, scope: CoroutineScope): DeviceSession {
        val user = lab.getProperty("$prefix.user")
        val ssh = connectNative(lab.getProperty("$prefix.host"), user, lab.getProperty("$prefix.password"), scope)
        return connectRouterOsConsole(ssh, user, scope, dialects = listOf(Dialect.Classic)) ?: error("the console of $prefix does not speak the Classic dialect")
    }

    @Test
    fun theMenusOfRouterOs7ReadAsTheApiReadsThem() = survey("chr", RouterOsProtocol.ApiSsl, "8291") { lab, scope -> overPort8291(lab, "chr", scope) }

    @Test
    fun theMenusOfRouterOs6ReadAsTheApiReadsThem() = survey("chr6", RouterOsProtocol.Api, "8291") { lab, scope -> overPort8291(lab, "chr6", scope) }

    @Test
    fun theMenusOfRouterOs7ReadByGetAsTheApiReadsThem() = survey("chr", RouterOsProtocol.ApiSsl, "get") { lab, scope -> overPort8291ByGet(lab, "chr", scope) }
}
