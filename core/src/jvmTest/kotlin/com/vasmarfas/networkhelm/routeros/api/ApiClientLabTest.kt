package com.vasmarfas.networkhelm.routeros.api

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.transport.ByteStream
import com.vasmarfas.networkhelm.transport.openTcp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ApiClientLabTest {

    private val list = "rd-test-client"

    @Test
    fun subscriptionOutlivesALostConnectionAndTheOldSessionIsGone() = runBlocking {
        val lab = Lab.require("chr.host", "chr.user", "chr.password")
        val scope = CoroutineScope(Dispatchers.Default)
        val streams = mutableListOf<ByteStream>()
        suspend fun open() = ApiConnection(openTcp(lab.getValue("chr.host"), 8728).also { streams += it }, ApiEncoding.Utf8, scope)
            .apply { login(lab.getValue("chr.user"), lab.getValue("chr.password")) }
        val client = ApiClient(open(), scope) { open() }
        suspend fun sessions() = client.call("/user/active/print", "=count-only=", "?via=api", "?address").done.getValue("ret")
        suspend fun cleanUp() = client.call("/ip/firewall/address-list/print", "=.proplist=.id", "?list=$list").rows
            .forEach { client.call("/ip/firewall/address-list/remove", "=.id=${it.getValue(".id")}") }
        try {
            withTimeout(60.seconds) {
                cleanUp()
                val baseline = sessions()
                val seen = Channel<String>(Channel.UNLIMITED)
                scope.launch {
                    client.each { api ->
                        api.command(listOf("/ip/firewall/address-list/listen")).collect {
                            if (it.attributes["list"] == list && it.attributes[".dead"] == null) emit(it.attributes.getValue("address"))
                        }
                    }.collect { seen.send(it) }
                }
                delay(500)
                client.call("/ip/firewall/address-list/add", "=list=$list", "=address=192.0.2.10")
                assertEquals("192.0.2.10", seen.receive())

                val lost = client.state.value
                streams.first().close()
                client.state.first { it is ApiLink.Up && it !== lost }
                delay(500)
                client.call("/ip/firewall/address-list/add", "=list=$list", "=address=192.0.2.11")
                assertEquals("192.0.2.11", seen.receive())

                while (sessions() != baseline) delay(200)
            }
        } finally {
            withTimeout(10.seconds) { cleanUp() }
            client.close()
            scope.cancel()
        }
    }
}
