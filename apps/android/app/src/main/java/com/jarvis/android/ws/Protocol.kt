package com.jarvis.android.ws

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

val gson = Gson()

const val AUDIO_KIND_PCM: Byte = 0x01

data class ControlMessage(
    val type: String,
    val payload: JsonObject,
    val requestId: String? = null,
    val clientTurnId: String? = null
)

fun parseServerMessage(text: String): ControlMessage? {
    return try {
        val json = JsonParser.parseString(text).asJsonObject
        val type = json.get("type")?.asString ?: return null
        val payload = json.getAsJsonObject("payload") ?: JsonObject()
        val requestId = json.get("requestId")?.asString
        val clientTurnId = json.get("clientTurnId")?.asString
        ControlMessage(type, payload, requestId, clientTurnId)
    } catch (_: Exception) {
        null
    }
}

fun buildControlMessage(
    type: String,
    payload: Map<String, Any?> = emptyMap(),
    requestId: String? = null,
    clientTurnId: String? = null
): String {
    val json = JsonObject()
    json.addProperty("type", type)
    json.add("payload", gson.toJsonTree(payload))
    requestId?.let { json.addProperty("requestId", it) }
    clientTurnId?.let { json.addProperty("clientTurnId", it) }
    return json.toString()
}

/** Wrap PCM audio bytes with the 1-byte kind discriminator. */
fun wrapAudioFrame(pcmData: ByteArray): ByteArray {
    val frame = ByteArray(1 + pcmData.size)
    frame[0] = AUDIO_KIND_PCM
    pcmData.copyInto(frame, 1)
    return frame
}

/** Strip the 1-byte kind discriminator from a binary frame. */
fun unwrapAudioFrame(frame: ByteArray): ByteArray? {
    if (frame.isEmpty() || frame[0] != AUDIO_KIND_PCM) return null
    return frame.copyOfRange(1, frame.size)
}
