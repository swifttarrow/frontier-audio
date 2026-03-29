package com.jarvis.android.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.UUID

class AudioCapturePipeline(
    private val context: Context,
    private val onAudioChunk: (ByteArray) -> Unit
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_DURATION_MS = 100 // 100ms chunks
        val CHUNK_SIZE = SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000 // 16-bit = 2 bytes per sample
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    @Volatile
    private var isCapturing = false

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun startCapture(scope: CoroutineScope): String {
        if (!hasPermission()) throw SecurityException("RECORD_AUDIO permission not granted")
        if (isCapturing) {
            stopCapture()
        }

        val clientTurnId = UUID.randomUUID().toString()

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            CHUNK_SIZE * 2
        )

        // VOICE_RECOGNITION is tuned for speech and tends to route host mic reliably on emulators vs MIC.
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        ).also {
            if (it.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }
        }

        isCapturing = true
        audioRecord?.startRecording()

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(CHUNK_SIZE)
            while (isActive && isCapturing) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    onAudioChunk(buffer.copyOf(read))
                }
            }
        }

        return clientTurnId
    }

    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
            // May throw if not initialized
        }
        audioRecord?.release()
        audioRecord = null
    }
}
