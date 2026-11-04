package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.PackForm
import com.vasmarfas.networkhelm.packs.PackFormTab
import com.vasmarfas.networkhelm.packs.PackPath
import kotlin.test.Test
import kotlin.test.assertEquals

class UnlistedTest {

    private val form = PackForm(
        tabs = listOf(PackFormTab("General", listOf("name", "port", "-", "available-from")), PackFormTab("Status", listOf("running"), readonly = true)),
        renamed = mapOf("address" to "available-from"),
    )
    private val shown = PackPath(
        columns = listOf("name", "port"),
        readonly = listOf("bytes", "running"),
        pairs = mapOf("max-limit" to listOf("upload", "download")),
        gauges = mapOf("cache-used" to "cache-size"),
    )
    private val schema = MenuSchema(MenuPath.parse("ip/service"), NodeKind.List, emptyList(), emptyList(), listOf(Argument("certificate", ValueType.Text())))

    @Test
    fun aPropertyNothingNamesIsListedInTheOrderTheRouterPrintsIt() {
        val printed = listOf(
            ".id", ".about", "name", "port", "address", "available-from", "bytes", "running", "max-limit", "upload", "cache-used", "cache-size",
            "certificate", "dynamic", "invalid", "comment", "disabled", "default", "x-patch-level", "proto",
        )
        assertEquals(listOf("x-patch-level", "proto"), unlisted(shown, form, schema, printed))
    }

    @Test
    fun anElementOfAPathWithoutColumnsListsWhatIsNotInItsForm() {
        assertEquals(listOf("bytes"), unlisted(null, form, schema, listOf("name", "bytes", ".id")))
    }
}
