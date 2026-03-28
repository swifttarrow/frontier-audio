package com.jarvis.gateway.ws

import com.jarvis.gateway.audio.TurnPipeline
import com.jarvis.gateway.observability.withLoggingContext
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("VoiceWebSocket")

fun Routing.voiceWebSocket(sessionManager: SessionManager, turnPipeline: TurnPipeline) {
    webSocket("/v1/voice") {
        var activeSession: ActiveSession? = null
        val audioBuffer = mutableListOf<ByteArray>()

        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        val msg = parseControlMessage(text)
                        if (msg == null) {
                            send(Frame.Text(buildError("protocol.invalid", "Malformed JSON", false)))
                            continue
                        }

                        when (msg.type) {
                            "session.start" -> {
                                if (activeSession != null) {
                                    send(Frame.Text(buildError("protocol.invalid", "Session already started", false, msg.requestId)))
                                    continue
                                }
                                val deviceId = msg.payload.get("deviceId")?.asText()
                                if (deviceId.isNullOrBlank()) {
                                    send(Frame.Text(buildError("session.invalid_device", "deviceId required", false, msg.requestId)))
                                    continue
                                }

                                try {
                                    val session = sessionManager.createSession(deviceId)
                                    activeSession = session
                                    logger.info("Session created: sessionId={}, deviceId={}", session.sessionId, deviceId)

                                    val ack = buildMessage(
                                        type = "session.ack",
                                        payload = mapOf(
                                            "sessionId" to session.sessionId.toString(),
                                            "sessionToken" to session.sessionToken,
                                            "capabilities" to mapOf("stt" to true, "tts" to true),
                                            "repoDisplayName" to session.repoDisplayName
                                        ),
                                        requestId = msg.requestId
                                    )
                                    send(Frame.Text(ack))
                                } catch (e: Exception) {
                                    logger.error("Failed to create session", e)
                                    send(Frame.Text(buildError("orchestrator.failed", "Session creation failed", true, msg.requestId)))
                                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Session creation failed"))
                                    return@webSocket
                                }
                            }

                            "audio.commit" -> {
                                val session = activeSession
                                if (session == null) {
                                    send(Frame.Text(buildError("session.not_started", "Send session.start first", false)))
                                    continue
                                }
                                val clientTurnId = msg.clientTurnId
                                if (clientTurnId == null) {
                                    send(Frame.Text(buildError("protocol.invalid", "clientTurnId required on audio.commit", false)))
                                    continue
                                }

                                withLoggingContext(session.sessionId, UUID.fromString(clientTurnId)) {
                                    sessionManager.clearInterrupt(session.sessionId)
                                    sessionManager.setActiveTurn(session.sessionId, clientTurnId)

                                    // Collect buffered audio
                                    val audioBytes = mergeAudioBuffers(audioBuffer)
                                    audioBuffer.clear()

                                    try {
                                        turnPipeline.processTurn(
                                            session = session,
                                            clientTurnId = UUID.fromString(clientTurnId),
                                            audioData = audioBytes,
                                            sendFrame = { send(it) }
                                        )
                                    } catch (e: Exception) {
                                        logger.error("Turn processing failed: clientTurnId={}", clientTurnId, e)
                                        send(Frame.Text(buildError("orchestrator.failed", "Turn processing failed", true, clientTurnId = clientTurnId)))
                                    } finally {
                                        sessionManager.setActiveTurn(session.sessionId, null)
                                    }
                                }
                            }

                            "interrupt" -> {
                                val session = activeSession
                                if (session != null) {
                                    logger.info("Interrupt received: sessionId={}", session.sessionId)
                                    sessionManager.markInterrupted(session.sessionId)
                                }
                            }

                            "session.end" -> {
                                val session = activeSession
                                if (session != null) {
                                    logger.info("Session ended: sessionId={}", session.sessionId)
                                    sessionManager.removeSession(session.sessionId)
                                }
                                close(CloseReason(CloseReason.Codes.NORMAL, "Session ended"))
                                return@webSocket
                            }

                            else -> {
                                send(Frame.Text(buildError("protocol.unknown_type", "Unknown type: ${msg.type}", false)))
                            }
                        }
                    }

                    is Frame.Binary -> {
                        if (activeSession == null) {
                            send(Frame.Text(buildError("session.not_started", "Send session.start first", false)))
                            continue
                        }
                        val data = frame.readBytes()
                        if (data.isEmpty()) continue
                        // Strip 1-byte kind discriminator
                        if (data[0] == AUDIO_KIND_PCM && data.size > 1) {
                            audioBuffer.add(data.copyOfRange(1, data.size))
                        }
                    }

                    else -> {}
                }
            }
        } catch (e: Exception) {
            logger.error("WebSocket error", e)
        } finally {
            activeSession?.let { sessionManager.removeSession(it.sessionId) }
        }
    }
}

private fun mergeAudioBuffers(buffers: List<ByteArray>): ByteArray {
    val totalSize = buffers.sumOf { it.size }
    val result = ByteArray(totalSize)
    var offset = 0
    for (buf in buffers) {
        buf.copyInto(result, offset)
        offset += buf.size
    }
    return result
}
