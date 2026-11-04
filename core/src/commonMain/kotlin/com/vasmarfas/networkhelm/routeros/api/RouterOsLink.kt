package com.vasmarfas.networkhelm.routeros.api

import com.vasmarfas.networkhelm.model.LinkState
import com.vasmarfas.networkhelm.session.SafeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

interface RouterOsLink {
    suspend fun call(words: List<String>): ApiResult

    fun command(words: List<String>, sent: CompletableDeferred<Unit>? = null): Flow<ApiReply>
}

interface RouterOsClient : RouterOsLink {
    fun <T> each(block: suspend FlowCollector<T>.(RouterOsLink) -> Unit): Flow<T>

    val status: Flow<LinkState>

    val safeMode: SafeMode? get() = null

    fun close()
}

suspend fun RouterOsLink.call(vararg words: String): ApiResult = call(words.asList())
