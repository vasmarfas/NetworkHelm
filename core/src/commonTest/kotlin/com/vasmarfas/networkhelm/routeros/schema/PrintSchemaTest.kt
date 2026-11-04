package com.vasmarfas.networkhelm.routeros.schema

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.routeros.api.ApiReply
import com.vasmarfas.networkhelm.routeros.api.ApiResult
import com.vasmarfas.networkhelm.routeros.api.ApiTrapException
import com.vasmarfas.networkhelm.routeros.api.RouterOsLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PrintSchemaTest {

    private class Router : RouterOsLink {
        val asked = mutableListOf<String>()
        private val address = mapOf(".id" to "*2", "address" to "192.0.2.73/24", "interface" to "ether1", "dynamic" to "true", "disabled" to "false")

        override suspend fun call(words: List<String>): ApiResult {
            asked += words.joinToString(" ")
            return when (words.joinToString(" ")) {
                "/ip/address/print =count-only=" -> ApiResult(emptyList(), mapOf("ret" to "1"))
                "/ip/address/print" -> ApiResult(listOf(address), emptyMap())
                "/log/print =count-only=" -> ApiResult(emptyList(), mapOf("ret" to "100000"))
                "/ip/dns/print =count-only=" -> throw ApiTrapException(null, "unknown parameter")
                "/ip/dns/print" -> ApiResult(listOf(mapOf("servers" to "", "allow-remote-requests" to "false")), emptyMap())
                "/tool/torch/print =count-only=" -> throw ApiTrapException(null, "no such command")
                "/ip/print =count-only=" -> throw ApiTrapException(0, "no such command or directory (print)")
                "/interface/wifi/print =count-only=" -> throw ApiTrapException(0, "no such command or directory (wifi)")
                "/container/print =count-only=" -> throw ApiTrapException(null, "no such command prefix")
                else -> error("unexpected ${words.joinToString(" ")}")
            }
        }

        override fun command(words: List<String>, sent: CompletableDeferred<Unit>?): Flow<ApiReply> = error("not used")
    }

    @Test
    fun countOnlyTellsWhatAPathIs() = runTest {
        val router = Router()
        val schemas = PrintSchema(router)

        val list = schemas.describe(MenuPath.parse("ip/address"))
        assertEquals(NodeKind.List, list.kind)
        assertEquals(listOf("address", "interface", "dynamic", "disabled"), list.arguments.map { it.name })
        assertIs<ValueType.YesNo>(list.argument("disabled")?.type)
        assertIs<ValueType.Text>(list.argument("address")?.type)
        assertEquals(listOf("print", "add", "set", "remove", "enable", "disable"), list.commands)

        val settings = schemas.describe(MenuPath.parse("ip/dns"))
        assertEquals(NodeKind.Settings, settings.kind)
        assertEquals(listOf("servers", "allow-remote-requests"), settings.arguments.map { it.name })

        assertEquals(NodeKind.Command, schemas.describe(MenuPath.parse("tool/torch")).kind)
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { schemas.describe(MenuPath.parse("ip")) }.category)
        for (missing in listOf("interface/wifi", "container")) {
            assertEquals(ErrorCategory.MissingItem, assertFailsWith<DeviceException> { schemas.describe(MenuPath.parse(missing)) }.category, missing)
        }

        assertEquals(emptyList(), schemas.describe(MenuPath.parse("log")).arguments)

        schemas.describe(MenuPath.parse("ip/address"))
        assertEquals(1, router.asked.count { it == "/ip/address/print" }, "a path is asked once")
    }
}
