package com.vasmarfas.networkhelm.pfsense

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.httpRequest
import com.vasmarfas.networkhelm.transport.openTlsTrusted
import com.vasmarfas.networkhelm.transport.urlEncoded
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.io.encoding.Base64

fun interface PfsenseCall {
    suspend fun call(method: String, path: String, query: Map<String, String>, body: JsonObject?): JsonElement
}

class HttpsPfsense(private val host: String, private val port: Int, user: String, secret: String, private val trust: TrustPolicy) : PfsenseCall {
    private val authorization = if (user.isEmpty()) "X-API-Key" to secret else "Authorization" to "Basic " + Base64.encode("$user:$secret".encodeToByteArray())

    override suspend fun call(method: String, path: String, query: Map<String, String>, body: JsonObject?): JsonElement {
        val headers = buildMap {
            put(authorization.first, authorization.second)
            put("Accept", "application/json")
            if (body != null) put("Content-Type", "application/json")
        }
        val target = "/api/v2/$path" + if (query.isEmpty()) "" else query.entries.joinToString("&", "?") { (name, value) -> "${urlEncoded(name)}=${urlEncoded(value)}" }
        val stream = openTlsTrusted(host, port, trust)
        val response = try {
            httpRequest(stream, host, method, target, headers, body?.toString()?.encodeToByteArray() ?: ByteArray(0))
        } finally {
            stream.close()
        }
        val envelope = try {
            Json.parseToJsonElement(response.body.decodeToString()) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: throw DeviceException(if (response.status == 404) ErrorCategory.MissingItem else ErrorCategory.Api, "/api/v2/$path answered ${response.status} without the JSON of the API")
        val message = (envelope["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val code = (envelope["code"] as? JsonPrimitive)?.intOrNull ?: response.status
        return when (code) {
            200 -> envelope["data"] ?: envelope
            401, 403 -> throw DeviceException(ErrorCategory.PermissionDenied, message.ifEmpty { "pfSense refused the key or its user may not call /api/v2/$path" })
            404 -> throw DeviceException(ErrorCategory.MissingItem, message.ifEmpty { "this pfSense has no /api/v2/$path" })
            400, 406, 409, 422 -> throw DeviceException(ErrorCategory.ArgumentValue, message.ifEmpty { "/api/v2/$path answered $code" })
            else -> throw DeviceException(ErrorCategory.General, message.ifEmpty { "/api/v2/$path answered $code" })
        }
    }
}
