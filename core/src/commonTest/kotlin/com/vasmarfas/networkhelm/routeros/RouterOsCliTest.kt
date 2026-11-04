package com.vasmarfas.networkhelm.routeros

import com.vasmarfas.networkhelm.journal.EditKind
import com.vasmarfas.networkhelm.journal.EditRecord
import com.vasmarfas.networkhelm.model.MenuPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RouterOsCliTest {

    private fun record(kind: EditKind, ids: List<String>, after: Map<String, String?>, path: String = "ip/address", moveBefore: String? = null) =
        EditRecord(Instant.fromEpochSeconds(0), "lab", MenuPath.parse(path), kind, ids, emptyMap(), after, moveBefore)

    @Test
    fun addListsEveryWrittenProperty() {
        assertEquals(
            "/ip address add address=10.0.0.1/24 interface=bridge comment=\"lab network\"",
            record(EditKind.Add, listOf("*5"), mapOf("address" to "10.0.0.1/24", "interface" to "bridge", "comment" to "lab network")).toCli(),
        )
    }

    @Test
    fun setWritesAndUnsetsSeparately() {
        assertEquals(
            "/ip firewall filter set *1A action=drop\n/ip firewall filter unset *1A value-name=comment",
            record(EditKind.Set, listOf("*1A"), linkedMapOf("action" to "drop", "comment" to null), "ip/firewall/filter").toCli(),
        )
    }

    @Test
    fun settingsMenuHasNoTarget() {
        assertEquals("/ip dns set servers=1.1.1.1,9.9.9.9", record(EditKind.Set, emptyList(), mapOf("servers" to "1.1.1.1,9.9.9.9"), "ip/dns").toCli())
    }

    @Test
    fun removeAndMoveAddressElementsById() {
        assertEquals("/ip address remove *1,*2", record(EditKind.Remove, listOf("*1", "*2"), emptyMap()).toCli())
        assertEquals("/ip firewall filter move *7 destination=*2", record(EditKind.Move, listOf("*7"), emptyMap(), "ip/firewall/filter", "*2").toCli())
        assertEquals("/ip firewall filter move *7", record(EditKind.Move, listOf("*7"), emptyMap(), "ip/firewall/filter").toCli())
    }

    @Test
    fun valuesAreQuotedOnlyWhenTheConsoleNeedsIt() {
        assertEquals("!10.0.0.0/8", cliValue("!10.0.0.0/8"))
        assertEquals("ether1,ether2", cliValue("ether1,ether2"))
        assertEquals("\"\"", cliValue(""))
        assertEquals("\"a b\"", cliValue("a b"))
        assertEquals("\"say \\\"hi\\\" \\\$HOME \\\\ \\?\"", cliValue("say \"hi\" \$HOME \\ ?"))
        assertEquals("\"line\\nnext\"", cliValue("line\nnext"))
        assertEquals("Привет", cliValue("Привет"))
    }
}
