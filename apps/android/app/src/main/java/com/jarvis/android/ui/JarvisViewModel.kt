package com.jarvis.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.android.BuildConfig
import com.jarvis.android.audio.AudioCapturePipeline
import com.jarvis.android.audio.TtsPlaybackController
import com.jarvis.android.storage.EncryptedSessionStore
import com.jarvis.android.ws.ConnectionState
import com.jarvis.android.ws.ServerEvent
import com.jarvis.android.ws.VoiceGatewayClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UiState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

data class JarvisUiState(
    val state: UiState = UiState.IDLE,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val lastTranscript: String = "",
    val lastAssistantText: String = "",
    val errorMessage: String = "",
    val repoDisplayName: String = ""
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = EncryptedSessionStore(application)
    private val deviceId = sessionStore.getOrCreateDeviceId()
    private val gatewayClient = VoiceGatewayClient(BuildConfig.VOICE_GATEWAY_WS_URL, deviceId)
    private val audioCapture = AudioCapturePipeline(application) { pcmData ->
        gatewayClient.sendAudio(pcmData)
    }
    private val ttsPlayback = TtsPlaybackController(application)

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var currentTurnId: String = ""

    init {
        observeGatewayEvents()
    }

    fun onPttPressed() {
        val currentState = _uiState.value.state

        when (currentState) {
            UiState.SPEAKING -> {
                // Interrupt: tap during playback
                ttsPlayback.stopPlayback()
                gatewayClient.sendInterrupt(currentTurnId)
                _uiState.value = _uiState.value.copy(state = UiState.IDLE)
            }
            UiState.IDLE, UiState.ERROR -> {
                // Start recording
                if (gatewayClient.state != ConnectionState.READY) {
                    gatewayClient.connect()
                }
                if (!audioCapture.hasPermission()) {
                    _uiState.value = _uiState.value.copy(
                        state = UiState.ERROR,
                        errorMessage = "Microphone permission required"
                    )
                    return
                }
                try {
                    currentTurnId = audioCapture.startCapture(viewModelScope)
                    _uiState.value = _uiState.value.copy(state = UiState.LISTENING, errorMessage = "")
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        state = UiState.ERROR,
                        errorMessage = e.message ?: "Failed to start recording"
                    )
                }
            }
            else -> { /* ignore during LISTENING/THINKING */ }
        }
    }

    fun onPttReleased() {
        if (_uiState.value.state != UiState.LISTENING) return

        audioCapture.stopCapture()
        gatewayClient.sendAudioCommit(currentTurnId)
        _uiState.value = _uiState.value.copy(state = UiState.THINKING)
    }

    fun connect() {
        if (gatewayClient.state == ConnectionState.DISCONNECTED ||
            gatewayClient.state == ConnectionState.ERROR) {
            gatewayClient.connect()
        }
    }

    private fun observeGatewayEvents() {
        viewModelScope.launch {
            gatewayClient.events.collect { event ->
                when (event) {
                    is ServerEvent.ConnectionChanged -> {
                        _uiState.value = _uiState.value.copy(connectionState = event.state)
                        if (event.state == ConnectionState.ERROR) {
                            _uiState.value = _uiState.value.copy(
                                state = UiState.ERROR,
                                errorMessage = "Connection lost"
                            )
                        }
                    }
                    is ServerEvent.SessionAck -> {
                        sessionStore.setSessionToken(event.sessionToken)
                        _uiState.value = _uiState.value.copy(repoDisplayName = event.repoDisplayName)
                    }
                    is ServerEvent.TranscriptFinal -> {
                        _uiState.value = _uiState.value.copy(lastTranscript = event.text)
                    }
                    is ServerEvent.AssistantText -> {
                        _uiState.value = _uiState.value.copy(lastAssistantText = event.text)
                    }
                    is ServerEvent.TtsStart -> {
                        _uiState.value = _uiState.value.copy(state = UiState.SPEAKING)
                        ttsPlayback.startPlayback()
                    }
                    is ServerEvent.TtsChunk -> {
                        ttsPlayback.writePcmData(event.pcmData)
                    }
                    is ServerEvent.TtsEnd -> {
                        ttsPlayback.stopPlayback()
                        if (_uiState.value.state == UiState.SPEAKING) {
                            _uiState.value = _uiState.value.copy(state = UiState.IDLE)
                        }
                    }
                    is ServerEvent.Error -> {
                        _uiState.value = _uiState.value.copy(
                            state = UiState.ERROR,
                            errorMessage = event.message
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioCapture.stopCapture()
        ttsPlayback.stopPlayback()
        gatewayClient.disconnect()
    }
}
