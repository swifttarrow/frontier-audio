package com.jarvis.android.ws

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ConnectionState { DISCONNECTED, CONNECTING, READY, ERROR }

sealed class ServerEvent {
    data class SessionAck(val sessionId: String, val sessionToken: String, val repoDisplayName: String) : ServerEvent()
    data class TranscriptFinal(val text: String, val clientTurnId: String) : ServerEvent()
    data class AssistantText(val text: String, val clientTurnId: String) : ServerEvent()
    data class TtsStart(val clientTurnId: String) : ServerEvent()
    data class TtsChunk(val pcmData: ByteArray) : ServerEvent()
    data class TtsEnd(val interrupted: Boolean, val clientTurnId: String) : ServerEvent()
    data class Error(
        val code: String,
        val message: String,
        val retryable: Boolean,
        /** Present for turn-scoped errors (e.g. stt.failed); empty for connection/protocol errors. */
        val clientTurnId: String = ""
    ) : ServerEvent()
    data class ConnectionChanged(val state: ConnectionState) : ServerEvent()
}

class VoiceGatewayClient(
    private val wsUrl: String,
    private val deviceId: String
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val eventChannel = Channel<ServerEvent>(Channel.BUFFERED)
    val events: Flow<ServerEvent> = eventChannel.receiveAsFlow()

    @Volatile
    var state: ConnectionState = ConnectionState.DISCONNECTED
        private set

    private var reconnectAttempts = 0
    private val maxReconnectDelay = 30_000L

    fun connect() {
        if (state == ConnectionState.CONNECTING || state == ConnectionState.READY) return

        state = ConnectionState.CONNECTING
        eventChannel.trySend(ServerEvent.ConnectionChanged(ConnectionState.CONNECTING))

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                // Send session.start
                val msg = buildControlMessage(
                    type = "session.start",
                    payload = mapOf("deviceId" to deviceId, "clientVersion" to "0.1.0"),
                    requestId = UUID.randomUUID().toString()
                )
                webSocket.send(msg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = parseServerMessage(text) ?: return
                when (msg.type) {
                    "session.ack" -> {
                        val sessionId = msg.payload.get("sessionId")?.asString ?: ""
                        val token = msg.payload.get("sessionToken")?.asString ?: ""
                        val repo = msg.payload.get("repoDisplayName")?.asString ?: ""
                        state = ConnectionState.READY
                        eventChannel.trySend(ServerEvent.ConnectionChanged(ConnectionState.READY))
                        eventChannel.trySend(ServerEvent.SessionAck(sessionId, token, repo))
                    }
                    "transcript.final" -> {
                        val text = msg.payload.get("text")?.asString ?: ""
                        eventChannel.trySend(ServerEvent.TranscriptFinal(text, msg.clientTurnId ?: ""))
                    }
                    "assistant.text" -> {
                        val text = msg.payload.get("text")?.asString ?: ""
                        eventChannel.trySend(ServerEvent.AssistantText(text, msg.clientTurnId ?: ""))
                    }
                    "tts.start" -> {
                        eventChannel.trySend(ServerEvent.TtsStart(msg.clientTurnId ?: ""))
                    }
                    "tts.end" -> {
                        val interrupted = msg.payload.get("interrupted")?.asBoolean ?: false
                        eventChannel.trySend(ServerEvent.TtsEnd(interrupted, msg.clientTurnId ?: ""))
                    }
                    "error" -> {
                        val code = msg.payload.get("code")?.asString ?: "unknown"
                        val message = msg.payload.get("message")?.asString ?: "Unknown error"
                        val retryable = msg.payload.get("retryable")?.asBoolean ?: false
                        val turnId = msg.clientTurnId ?: ""
                        eventChannel.trySend(ServerEvent.Error(code, message, retryable, turnId))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                val pcm = unwrapAudioFrame(data) ?: return
                eventChannel.trySend(ServerEvent.TtsChunk(pcm))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                state = ConnectionState.ERROR
                eventChannel.trySend(ServerEvent.ConnectionChanged(ConnectionState.ERROR))
                eventChannel.trySend(ServerEvent.Error("connection.failed", t.message ?: "Connection failed", true))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                state = ConnectionState.DISCONNECTED
                eventChannel.trySend(ServerEvent.ConnectionChanged(ConnectionState.DISCONNECTED))
            }
        })
    }

    fun sendAudio(pcmData: ByteArray) {
        val frame = wrapAudioFrame(pcmData)
        webSocket?.send(frame.toByteString())
    }

    fun sendAudioCommit(clientTurnId: String) {
        val msg = buildControlMessage(
            type = "audio.commit",
            clientTurnId = clientTurnId
        )
        webSocket?.send(msg)
    }

    fun sendInterrupt(clientTurnId: String) {
        val msg = buildControlMessage(
            type = "interrupt",
            clientTurnId = clientTurnId
        )
        webSocket?.send(msg)
    }

    fun disconnect() {
        val msg = buildControlMessage(type = "session.end")
        webSocket?.send(msg)
        webSocket?.close(1000, "Session ended")
        webSocket = null
        state = ConnectionState.DISCONNECTED
    }

    fun getReconnectDelay(): Long {
        val delay = minOf(1000L * (1 shl reconnectAttempts), maxReconnectDelay)
        reconnectAttempts++
        return delay
    }
}
