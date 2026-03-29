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

        // Send transcript.final
        sendFrame(Frame.Text(buildMessage(
            type = "transcript.final",
            payload = mapOf("text" to transcript),
            clientTurnId = turnIdStr
        )))

        // 2. Persist user turn
        repository.insertTurn(session.sessionId, clientTurnId, "user", transcript)
        logger.info("User turn persisted: clientTurnId={}, transcript={}", clientTurnId, transcript)

        // 3. Same-session conversation history for follow-ups ("try again", "what was that?")
        val conversationHistory = repository.recentTurns(session.sessionId, limit = 40)
            .asSequence()
            .filterNot { it.clientTurnId == clientTurnId && it.role == "user" }
            .mapNotNull { turn ->
                val text = turn.text?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                if (turn.role != "user" && turn.role != "assistant") return@mapNotNull null
                turn.role to text
            }
            .toList()

        // 4. Load memory context (cross-session / summarized)
        val memoryCtx = memoryService?.let { svc ->
            val chunks = svc.recentChunks(session.sessionId)
            svc.buildMemoryContext(chunks)
        }

        // 5. Generate assistant response via orchestrator
        val result = orchestrator.handleUserUtterance(
            sessionId = session.sessionId,
            clientTurnId = clientTurnId,
            transcript = transcript,
            memoryContext = memoryCtx,
            conversationHistory = conversationHistory
        )
        val assistantText = result.assistantText

        // Persist assistant turn
        repository.insertTurn(session.sessionId, clientTurnId, "assistant", assistantText)

        // Save memory (non-blocking, failure doesn't fail the turn)
        memoryService?.appendTurnSummary(session.sessionId, clientTurnId, transcript, assistantText)

        // Check interrupt before TTS
        if (session.interrupted) {
            logger.info("Turn interrupted before TTS: clientTurnId={}", clientTurnId)
            return
        }

        // 4. Send assistant text
        sendFrame(Frame.Text(buildMessage(
            type = "assistant.text",
            payload = mapOf("text" to assistantText),
            clientTurnId = turnIdStr
        )))

        // 5. TTS
        sendTtsResponse(assistantText, turnIdStr, session, sendFrame)
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

        // Send tts.start
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

        // Send audio chunks (max 32000 bytes payload per frame)
        val chunkSize = 32000
        var offset = 0
        while (offset < audioBytes.size) {
            if (session.interrupted) {
                logger.info("TTS interrupted mid-stream: clientTurnId={}", clientTurnId)
                sendFrame(Frame.Text(buildMessage(
                    type = "tts.end",
                    payload = mapOf("interrupted" to true),
                    clientTurnId = clientTurnId
                )))
                return
            }
            val end = minOf(offset + chunkSize, audioBytes.size)
            val chunk = audioBytes.copyOfRange(offset, end)
            // Prepend kind byte
            val frame = ByteArray(1 + chunk.size)
            frame[0] = AUDIO_KIND_PCM
            chunk.copyInto(frame, 1)
            sendFrame(Frame.Binary(true, frame))
            offset = end
        }

        // Send tts.end
        sendFrame(Frame.Text(buildMessage(
            type = "tts.end",
            payload = mapOf("interrupted" to false),
            clientTurnId = clientTurnId
        )))
        logger.info("TTS complete for clientTurnId={}", clientTurnId)
    }
}
