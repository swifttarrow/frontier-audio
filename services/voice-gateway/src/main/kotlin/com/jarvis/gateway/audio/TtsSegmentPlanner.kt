package com.jarvis.gateway.audio

/**
 * Chooses how much of a growing transcript buffer is safe to send to TTS before the LLM stream ends.
 * Prefers sentence boundaries; uses a hard cap so very long clauses still flush.
 */
object TtsSegmentPlanner {

    private const val MIN_FIRST_FLUSH = 18
    private const val MIN_SUBSEQUENT_FLUSH = 36
    private const val HARD_MAX = 280

    /**
     * @param buffer current unsynthesized tail (same as [StringBuilder] contents)
     * @param streamEnded true when the LLM has finished — flush all remaining non-blank text
     * @param ttsAlreadyStarted false until the first segment has been sent to TTS
     * @return number of leading characters to remove from the buffer and synthesize, or -1 if none
     */
    fun flushLength(buffer: String, streamEnded: Boolean, ttsAlreadyStarted: Boolean): Int {
        if (buffer.isEmpty()) return -1
        val min = if (ttsAlreadyStarted) MIN_SUBSEQUENT_FLUSH else MIN_FIRST_FLUSH

        if (streamEnded) {
            return buffer.length
        }

        if (buffer.length < min) return -1

        if (buffer.length >= HARD_MAX) {
            return softBreakBefore(buffer, HARD_MAX)
        }

        for (i in min - 1 until buffer.length) {
            when (val c = buffer[i]) {
                '\n' -> return i + 1
                '.', '!', '?' -> {
                    if (i == buffer.lastIndex || buffer[i + 1].isWhitespace()) {
                        return i + 1
                    }
                }
            }
        }
        return -1
    }

    private fun softBreakBefore(s: String, limit: Int): Int {
        val slice = s.take(limit)
        val lastSpace = slice.lastIndexOf(' ')
        return if (lastSpace >= MIN_FIRST_FLUSH) lastSpace + 1 else limit
    }
}
