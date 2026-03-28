package com.jarvis.android.ws

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {

    @Test
    fun `parseServerMessage parses session ack`() {
        val json = """{"type":"session.ack","payload":{"sessionId":"abc","sessionToken":"tok","capabilities":{"stt":true},"repoDisplayName":"owner/repo"},"requestId":"req1"}"""
        val msg = parseServerMessage(json)
        assertNotNull(msg)
        assertEquals("session.ack", msg!!.type)
        assertEquals("abc", msg.payload.get("sessionId").asString)
        assertEquals("req1", msg.requestId)
    }

    @Test
    fun `parseServerMessage returns null for malformed json`() {
        assertNull(parseServerMessage("not json"))
        assertNull(parseServerMessage("{}"))
    }

    @Test
    fun `buildControlMessage produces valid json`() {
        val json = buildControlMessage("session.start", mapOf("deviceId" to "dev-1"), requestId = "r1")
        val parsed = parseServerMessage(json)
        assertNotNull(parsed)
        assertEquals("session.start", parsed!!.type)
        assertEquals("r1", parsed.requestId)
    }

    @Test
    fun `wrapAudioFrame prepends kind byte`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val frame = wrapAudioFrame(pcm)
        assertEquals(5, frame.size)
        assertEquals(AUDIO_KIND_PCM, frame[0])
        assertEquals(1.toByte(), frame[1])
    }

    @Test
    fun `unwrapAudioFrame strips kind byte`() {
        val frame = byteArrayOf(AUDIO_KIND_PCM, 10, 20, 30)
        val pcm = unwrapAudioFrame(frame)
        assertNotNull(pcm)
        assertEquals(3, pcm!!.size)
        assertEquals(10.toByte(), pcm[0])
    }

    @Test
    fun `unwrapAudioFrame returns null for wrong kind`() {
        val frame = byteArrayOf(0x02, 10, 20)
        assertNull(unwrapAudioFrame(frame))
    }
}
