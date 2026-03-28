package com.jarvis.gateway.ws

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

val mapper: ObjectMapper = jacksonObjectMapper().apply {
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}

/** Audio binary frame kind discriminator */
const val AUDIO_KIND_PCM: Byte = 0x01

/** Parse a JSON text frame into type + payload. Returns null on malformed input. */
fun parseControlMessage(text: String): ControlMessage? {
    return try {
        val node = mapper.readTree(text)
        val type = node.get("type")?.asText() ?: return null
        val payload = node.get("payload") as? ObjectNode ?: mapper.createObjectNode()
        val requestId = node.get("requestId")?.asText()
        val clientTurnId = node.get("clientTurnId")?.asText()
        ControlMessage(type, payload, requestId, clientTurnId)
    } catch (_: Exception) {
        null
    }
}

data class ControlMessage(
    val type: String,
    val payload: ObjectNode,
    val requestId: String?,
    val clientTurnId: String?
)

fun buildMessage(type: String, payload: Map<String, Any?>, requestId: String? = null, clientTurnId: String? = null): String {
    val node = mapper.createObjectNode()
    node.put("type", type)
    node.set<JsonNode>("payload", mapper.valueToTree(payload))
    requestId?.let { node.put("requestId", it) }
    clientTurnId?.let { node.put("clientTurnId", it) }
    return mapper.writeValueAsString(node)
}

fun buildError(code: String, message: String, retryable: Boolean, requestId: String? = null, clientTurnId: String? = null): String {
    return buildMessage(
        type = "error",
        payload = mapOf("code" to code, "message" to message, "retryable" to retryable),
        requestId = requestId,
        clientTurnId = clientTurnId
    )
}
