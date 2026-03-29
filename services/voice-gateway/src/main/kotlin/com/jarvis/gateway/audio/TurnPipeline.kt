package com.jarvis.gateway.audio

import com.jarvis.gateway.agent.VoiceOrchestrator
import com.jarvis.gateway.db.SessionRepository
import com.jarvis.gateway.memory.MemoryService
import com.jarvis.gateway.ws.ActiveSession
import com.jarvis.gateway.ws.AUDIO_KIND_PCM
import com.jarvis.gateway.ws.buildMessage
import com.jarvis.gateway.ws.buildError
import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.util.UUID

class TurnPipeline(
    private val stt: SpeechToText,
    private val tts: TextToSpeech,
    private val repository: SessionRepository,
    private val orchestrator: VoiceOrchestrator,
    private val memoryService: MemoryService? = null
) {
    private val logger = LoggerFactory.getLogger(TurnPipeline::class.java)

    suspend fun processTurn(
        session: ActiveSession,
        clientTurnId: UUID,
        audioData: ByteArray,
        sendFrame: suspend (Frame) -> Unit
    ) {
        val turnIdStr = clientTurnId.toString()

        // Idempotency check — if we already processed this turn, skip
        val existing = repository.findTurnByClientTurnId(session.sessionId, clientTurnId, "user")
        if (existing != null) {
            logger.info("Duplicate clientTurnId={}, returning existing result", clientTurnId)
            val assistantTurn = repository.findTurnByClientTurnId(session.sessionId, clientTurnId, "assistant")
            if (assistantTurn?.text != null) {
                sendTtsResponse(assistantTurn.text, turnIdStr, session, sendFrame)
            }
            return
        }

        // 1. STT
        logger.info("Starting STT for clientTurnId={}, audioSize={}", clientTurnId, audioData.size)
        val transcriptResult = stt.transcribe(audioData)
        val transcript = transcriptResult.getOrElse { e ->
            logger.error("STT failed for clientTurnId={}", clientTurnId, e)
            sendFrame(Frame.Text(buildError("stt.failed", "Speech recognition failed", true, clientTurnId = turnIdStr)))
            return
        }

        sendFrame(Frame.Text(buildMessage(
            type = "transcript.final",
            payload = mapOf("text" to transcript),
            clientTurnId = turnIdStr
        )))

        repository.insertTurn(session.sessionId, clientTurnId, "user", transcript)
        logger.info("User turn persisted: clientTurnId={}, transcript={}", clientTurnId, transcript)

        val conversationHistory = repository.recentTurns(session.sessionId, limit = 40)
            .asSequence()
            .filterNot { it.clientTurnId == clientTurnId && it.role == "user" }
            .mapNotNull { turn ->
                val text = turn.text?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                if (turn.role != "user" && turn.role != "assistant") return@mapNotNull null
                turn.role to text
            }
            .toList()

        val memoryCtx = memoryService?.let { svc ->
            val chunks = svc.recentChunks(session.sessionId)
            svc.buildMemoryContext(chunks)
        }

        var ttsStarted = false
        val ttsBuffer = StringBuilder()

        val result = try {
            orchestrator.handleUserUtteranceStreaming(
                sessionId = session.sessionId,
                clientTurnId = clientTurnId,
                transcript = transcript,
                memoryContext = memoryCtx,
                conversationHistory = conversationHistory,
                shouldAbort = { session.interrupted },
                onAssistantTextDelta = { delta ->
                    if (session.interrupted) throw TurnInterruptedException()
                    sendFrame(Frame.Text(buildMessage(
                        type = "assistant.text.delta",
                        payload = mapOf("text" to delta),
                        clientTurnId = turnIdStr
                    )))
                    ttsBuffer.append(delta)
                    ttsStarted = flushSpeakableTts(
                        ttsBuffer, streamEnded = false, ttsStarted, session, turnIdStr, sendFrame
                    )
                }
            )
        } catch (_: TurnInterruptedException) {
            logger.info("Turn interrupted during orchestration or TTS: clientTurnId={}", clientTurnId)
            if (ttsStarted) {
                sendFrame(Frame.Text(buildMessage(
                    type = "tts.end",
                    payload = mapOf("interrupted" to true),
                    clientTurnId = turnIdStr
                )))
            }
            return
        }

        repository.insertTurn(session.sessionId, clientTurnId, "assistant", result.assistantText)
        memoryService?.appendTurnSummary(session.sessionId, clientTurnId, transcript, result.assistantText)

        try {
            ttsStarted = flushSpeakableTts(
                ttsBuffer, streamEnded = true, ttsStarted, session, turnIdStr, sendFrame
            )
        } catch (_: TurnInterruptedException) {
            logger.info("Turn interrupted flushing final TTS segment: clientTurnId={}", clientTurnId)
            if (ttsStarted) {
                sendFrame(Frame.Text(buildMessage(
                    type = "tts.end",
                    payload = mapOf("interrupted" to true),
                    clientTurnId = turnIdStr
                )))
            }
            sendFrame(Frame.Text(buildMessage(
                type = "assistant.text",
                payload = mapOf("text" to result.assistantText),
                clientTurnId = turnIdStr
            )))
            return
        }

        sendFrame(Frame.Text(buildMessage(
            type = "assistant.text",
            payload = mapOf("text" to result.assistantText),
            clientTurnId = turnIdStr
        )))

        if (session.interrupted) {
            logger.info("Turn interrupted before closing TTS: clientTurnId={}", clientTurnId)
            if (ttsStarted) {
                sendFrame(Frame.Text(buildMessage(
                    type = "tts.end",
                    payload = mapOf("interrupted" to true),
                    clientTurnId = turnIdStr
                )))
            }
            return
        }

        when {
            result.error != null -> {
                sendTtsResponse(result.assistantText, turnIdStr, session, sendFrame)
            }
            !ttsStarted && result.assistantText.isNotBlank() -> {
                sendTtsResponse(result.assistantText, turnIdStr, session, sendFrame)
            }
            ttsStarted -> {
                sendFrame(Frame.Text(buildMessage(
                    type = "tts.end",
                    payload = mapOf("interrupted" to false),
                    clientTurnId = turnIdStr
                )))
                logger.info("TTS complete for clientTurnId={}", clientTurnId)
            }
            else -> { /* empty reply — nothing to speak */ }
        }
    }

    /**
     * Drains [buffer] into TTS using [TtsSegmentPlanner]. Sends [tts.start] before the first PCM byte.
     * @return whether TTS downlink has started
     */
    private suspend fun flushSpeakableTts(
        buffer: StringBuilder,
        streamEnded: Boolean,
        ttsAlreadyStarted: Boolean,
        session: ActiveSession,
        clientTurnId: String,
        sendFrame: suspend (Frame) -> Unit
    ): Boolean {
        var started = ttsAlreadyStarted
        while (true) {
            val take = TtsSegmentPlanner.flushLength(buffer.toString(), streamEnded, started)
            if (take <= 0) break
            if (session.interrupted) throw TurnInterruptedException()
            val raw = buffer.substring(0, take)
            buffer.delete(0, take)
            val segment = raw.trim()
            if (segment.isEmpty()) continue
            if (!started) {
                sendFrame(Frame.Text(buildMessage(
                    type = "tts.start",
                    payload = mapOf(
                        "format" to "pcm_24k_16bit_mono",
                        "sampleRate" to 24000,
                        "channels" to 1,
                        "bitsPerSample" to 16
                    ),
                    clientTurnId = clientTurnId
                )))
                started = true
                logger.info("Starting incremental TTS for clientTurnId={}", clientTurnId)
            }
            val pcm = tts.synthesize(segment).getOrElse { e ->
                logger.error("TTS failed mid-stream for clientTurnId={}", clientTurnId, e)
                sendFrame(Frame.Text(buildError("tts.failed", "Speech synthesis failed", true, clientTurnId = clientTurnId)))
                throw TurnInterruptedException()
            }
            streamPcmChunks(pcm, session, clientTurnId, sendFrame)
        }
        return started
    }

    private suspend fun streamPcmChunks(
        audioBytes: ByteArray,
        session: ActiveSession,
        clientTurnId: String,
        sendFrame: suspend (Frame) -> Unit
    ) {
        val chunkSize = 32000
        var offset = 0
        while (offset < audioBytes.size) {
            if (session.interrupted) throw TurnInterruptedException()
            val end = minOf(offset + chunkSize, audioBytes.size)
            val chunk = audioBytes.copyOfRange(offset, end)
            val frame = ByteArray(1 + chunk.size)
            frame[0] = AUDIO_KIND_PCM
            chunk.copyInto(frame, 1)
            sendFrame(Frame.Binary(true, frame))
            offset = end
        }
    }

    private suspend fun sendTtsResponse(
        text: String,
        clientTurnId: String,
        session: ActiveSession,
        sendFrame: suspend (Frame) -> Unit
    ) {
        logger.info("Starting TTS for clientTurnId={}", clientTurnId)
        val ttsResult = tts.synthesize(text)
        val audioBytes = ttsResult.getOrElse { e ->
            logger.error("TTS failed for clientTurnId={}", clientTurnId, e)
            sendFrame(Frame.Text(buildError("tts.failed", "Speech synthesis failed", true, clientTurnId = clientTurnId)))
            return
        }

        sendFrame(Frame.Text(buildMessage(
            type = "tts.start",
            payload = mapOf(
                "format" to "pcm_24k_16bit_mono",
                "sampleRate" to 24000,
                "channels" to 1,
                "bitsPerSample" to 16
            ),
            clientTurnId = clientTurnId
        )))

        try {
            streamPcmChunks(audioBytes, session, clientTurnId, sendFrame)
        } catch (_: TurnInterruptedException) {
            sendFrame(Frame.Text(buildMessage(
                type = "tts.end",
                payload = mapOf("interrupted" to true),
                clientTurnId = clientTurnId
            )))
            return
        }

        sendFrame(Frame.Text(buildMessage(
            type = "tts.end",
            payload = mapOf("interrupted" to false),
            clientTurnId = clientTurnId
        )))
        logger.info("TTS complete for clientTurnId={}", clientTurnId)
    }
}
