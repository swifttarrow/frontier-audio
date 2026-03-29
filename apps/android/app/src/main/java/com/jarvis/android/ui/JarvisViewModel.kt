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
    /** Matches [ServerEvent.TtsStart] / [ServerEvent.TtsEnd] when audio is for the active reply. */
    private var playbackTurnId: String? = null

    init {
        observeGatewayEvents()
    }

    fun onPttPressed() {
        val currentState = _uiState.value.state

        when (currentState) {
            UiState.SPEAKING, UiState.THINKING -> {
                // Barge-in / tap-to-stop: do not start capture here — that used to flip state mid-gesture and cancel
                // the Compose pointerInput block keyed on state. Go IDLE; user holds the button again to talk.
                val turn = currentTurnId
                ttsPlayback.stopPlayback()
                playbackTurnId = null
                if (turn.isNotEmpty()) {
                    gatewayClient.sendInterrupt(turn)
                }
                currentTurnId = ""
                _uiState.value = _uiState.value.copy(state = UiState.IDLE, errorMessage = "")
            }
            UiState.IDLE, UiState.ERROR -> beginListening()
            else -> { /* ignore during LISTENING */ }
        }
    }

    private fun beginListening() {
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
                        if (event.state == ConnectionState.ERROR ||
                            event.state == ConnectionState.DISCONNECTED) {
                            audioCapture.stopCapture()
                        }
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
                        if (event.clientTurnId == currentTurnId) {
                            _uiState.value = _uiState.value.copy(lastTranscript = event.text)
                        }
                    }
                    is ServerEvent.AssistantText -> {
                        if (event.clientTurnId == currentTurnId) {
                            _uiState.value = _uiState.value.copy(lastAssistantText = event.text)
                        }
                    }
                    is ServerEvent.TtsStart -> {
                        if (event.clientTurnId != currentTurnId) return@collect
                        playbackTurnId = event.clientTurnId
                        _uiState.value = _uiState.value.copy(state = UiState.SPEAKING)
                        ttsPlayback.startPlayback()
                    }
                    is ServerEvent.TtsChunk -> {
                        if (playbackTurnId != null && _uiState.value.state == UiState.SPEAKING) {
                            ttsPlayback.writePcmData(event.pcmData)
                        }
                    }
                    is ServerEvent.TtsEnd -> {
                        if (event.interrupted || event.clientTurnId == playbackTurnId) {
                            ttsPlayback.stopPlayback()
                            playbackTurnId = null
                        }
                        if (_uiState.value.state == UiState.SPEAKING && event.clientTurnId == currentTurnId) {
                            _uiState.value = _uiState.value.copy(state = UiState.IDLE)
                        }
                    }
                    is ServerEvent.Error -> {
                        val turnScoped = event.clientTurnId.isNotEmpty()
                        if (turnScoped && event.clientTurnId != currentTurnId) {
                            return@collect
                        }
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
