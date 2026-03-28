package com.jarvis.gateway.ws

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProtocolTest {

    @Test
    fun `parseControlMessage parses valid session start`() {
        val json = """{"type":"session.start","payload":{"deviceId":"dev-1","clientVersion":"1.0.0"},"requestId":"abc-123"}"""
        val msg = parseControlMessage(json)
        assertNotNull(msg)
        assertEquals("session.start", msg.type)
        assertEquals("dev-1", msg.payload.get("deviceId").asText())
        assertEquals("abc-123", msg.requestId)
        assertNull(msg.clientTurnId)
    }

    @Test
    fun `parseControlMessage returns null for malformed JSON`() {
        assertNull(parseControlMessage("not json"))
        assertNull(parseControlMessage("{}")) // missing type
    }

    @Test
    fun `parseControlMessage parses audio commit with clientTurnId`() {
        val json = """{"type":"audio.commit","payload":{},"clientTurnId":"550e8400-e29b-41d4-a716-446655440000"}"""
        val msg = parseControlMessage(json)
        assertNotNull(msg)
        assertEquals("audio.commit", msg.type)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", msg.clientTurnId)
    }

    @Test
    fun `buildMessage produces valid JSON`() {
        val json = buildMessage("session.ack", mapOf("sessionId" to "abc"), requestId = "req-1")
        val parsed = parseControlMessage(json)
        assertNotNull(parsed)
        assertEquals("session.ack", parsed.type)
        assertEquals("abc", parsed.payload.get("sessionId").asText())
        assertEquals("req-1", parsed.requestId)
    }

    @Test
    fun `buildError produces error envelope`() {
        val json = buildError("protocol.invalid", "Bad request", false)
        val parsed = parseControlMessage(json)
        assertNotNull(parsed)
        assertEquals("error", parsed.type)
        assertEquals("protocol.invalid", parsed.payload.get("code").asText())
        assertEquals(false, parsed.payload.get("retryable").asBoolean())
    }
}
