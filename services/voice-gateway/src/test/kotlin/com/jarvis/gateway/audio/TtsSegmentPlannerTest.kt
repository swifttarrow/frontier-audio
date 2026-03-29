package com.jarvis.gateway.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class TtsSegmentPlannerTest {

    @Test
    fun `no flush until min length before stream end`() {
        val b = StringBuilder("Hello")
        assertEquals(-1, TtsSegmentPlanner.flushLength(b.toString(), streamEnded = false, ttsAlreadyStarted = false))
    }

    @Test
    fun `flush at sentence boundary after min length`() {
        val s = "This is a short reply. "
        val n = TtsSegmentPlanner.flushLength(s, streamEnded = false, ttsAlreadyStarted = false)
        assertEquals(21, n) // through '.'; trailing space stays in buffer
    }

    @Test
    fun `flush entire buffer when stream ended`() {
        val s = "OK"
        assertEquals(2, TtsSegmentPlanner.flushLength(s, streamEnded = true, ttsAlreadyStarted = false))
    }

    @Test
    fun `hard cap triggers soft break`() {
        val s = "word ".repeat(80).trimEnd() + "x"
        val n = TtsSegmentPlanner.flushLength(s, streamEnded = false, ttsAlreadyStarted = true)
        assert(n in 1..280)
    }
}
