package com.vasmarfas.networkhelm.openwrt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MixomoTest {

    private class Router(private val missing: Set<String> = emptySet()) : Ubus {
        private val answers = mapOf(
            "mihomo-profiles list" to """{"active":"default","profiles":[{"name":"default","url":"","interval":"","updated":"","active":1}],"rules":[{"name":"ads.yaml","url":"https://example.org/ads.yaml","interval":"24","updated":"1792273538"}],"ok":true}""",
            "mihomo-schedule list" to """{"default":"","time":[{"name":"night","enabled":true,"profile":"default"}],"trigger":[{"name":"fallback","enabled":false,"profile":"default"}],"ok":true}""",
            "mihomo-dns status" to """{"ok":true,"servers":["1.1.1.1","1.0.0.1"],"custom":[{"name":"Mihomo","value":"127.0.0.1#7880"},{"name":"Cloudflare","value":"1.1.1.1 1.0.0.1"}],"order":["Cloudflare","Mihomo"]}""",
            "mihomo-routing status" to """{"ok":true,"router":false,"variant":"redir-tproxy","rules":[{"id":"1","enabled":1,"priority":1,"label":"tv","source":"192.0.2.5","backend":"redir-tproxy"},{"id":"2","enabled":0,"priority":2,"label":"pc","source":"192.0.2.6","backend":"redir-tproxy"}],"exclusions":[]}""",
            "mixomo-backup status" to """{"ok":true,"backups":[],"settings":{"retention":1,"auto_mihomo":true,"auto_magitrickle":false}}""",
            "rpc-sys packagelist" to """{"packages":{"magitrickle":"0.8.2-r1","mihomo":"1.19.33-r1"}}""",
        )

        override suspend fun call(obj: String, method: String, args: JsonObject): JsonObject {
            val key = "$obj $method"
            if (obj in missing) throw UbusException(UbusStatus.NOT_FOUND)
            if (key == "file read") {
                val data = when (args.getValue("path").jsonPrimitive.content) {
                    "/etc/mixomo/versions/magitrickle" -> "mod\n"
                    "/etc/mixomo/versions/mihomo" -> "v1.19.33\n"
                    "/etc/mixomo/versions/mixomo" -> "v0.3.4\n2bad70c4\n"
                    "/etc/mixomo/templates/magitrickle-template.active" -> "from-mixomo"
                    else -> throw UbusException(UbusStatus.NOT_FOUND)
                }
                return JsonObject(mapOf("data" to JsonPrimitive(data)))
            }
            if (key == "service list") {
                return if (args.getValue("name").jsonPrimitive.content == "mihomo") Json.parseToJsonElement("""{"mihomo":{"instances":{"main":{"running":true}}}}""").jsonObject else JsonObject(emptyMap())
            }
            if (key == "file list") {
                return Json.parseToJsonElement("""{"entries":[{"name":"ads.yaml","type":"file","size":120},{"name":"keep","type":"directory","size":4096}]}""").jsonObject
            }
            return Json.parseToJsonElement(answers[key] ?: throw UbusException(UbusStatus.NOT_FOUND)).jsonObject
        }

        override suspend fun objects() = setOf("mihomo-profiles", "mihomo-schedule", "mihomo-dns", "mihomo-routing", "mixomo-backup", "service", "file", "rpc-sys")

        override fun listen(vararg events: String): Flow<JsonObject> = emptyFlow()

        override fun subscribe(obj: String): Flow<JsonObject> = emptyFlow()
    }

    private suspend fun mixomo(router: Router = Router()) = Mixomo(router, router.objects()) { "t$it" }

    @Test
    fun theOverviewSaysWhatRunsAndHowManyThereAre() = runTest {
        val overview = mixomo().lists.getValue("mixomo/overview").read().single()
        assertEquals(
            mapOf(
                "mixomo-version" to "v0.3.4", "mihomo" to "running", "mihomo-version" to "v1.19.33", "active-config" to "default", "magitrickle" to "stopped",
                "magitrickle-version" to "v0.8.2", "schedules" to "1", "rule-lists" to "1", "routing-clients" to "1", "dns-servers" to "2", "automatic-backups" to "enabled",
            ),
            overview.values,
        )
        val magitrickle = mixomo().lists.getValue("mixomo/magitrickle").read().single()
        assertEquals(mapOf("status" to "stopped", "version" to "v0.8.2", "variant" to "mod", "template" to "default"), magitrickle.values)
    }

    @Test
    fun theRuleListsTheSchedulesAndTheDnsServersAreListedAsThePageHasThem() = runTest {
        val lists = mixomo().lists
        val rules = lists.getValue("mixomo/rules").read().single()
        assertEquals(mapOf("name" to "ads.yaml", "size" to "120", "url" to "https://example.org/ads.yaml", "interval" to "24", "updated" to "t1792273538"), rules.values)
        val schedules = lists.getValue("mixomo/schedule").read()
        assertEquals(listOf("time:night" to "false", "trigger:fallback" to "true"), schedules.map { it.id to it["disabled"] })
        val servers = lists.getValue("mixomo/dns").read()
        assertEquals(listOf("Cloudflare" to "true", "Mihomo" to "false"), servers.map { it.id to it["active"] })
        assertEquals("1.1.1.1, 1.0.0.1", servers.first()["servers"])
    }

    @Test
    fun anOlderMixomoHasFewerLists() = runTest {
        val router = Router(missing = setOf("mihomo-schedule", "mihomo-dns"))
        val lists = Mixomo(router, setOf("mihomo-profiles", "mihomo-routing", "mixomo-backup", "service", "file")) { "t$it" }.lists
        assertFalse("mixomo/schedule" in lists || "mixomo/dns" in lists)
        val overview = lists.getValue("mixomo/overview").read().single()
        assertEquals(listOf("0", "0"), listOf(overview["schedules"], overview["dns-servers"]))
    }
}
