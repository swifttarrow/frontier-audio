package com.jarvis.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class TtsPlaybackController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val trackLock = Any()
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stopPlayback()
        }
    }

    fun startPlayback() {
        synchronized(trackLock) {
            if (isPlaying) return

            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying = true
        }
    }

    fun writePcmData(data: ByteArray) {
        synchronized(trackLock) {
            if (!isPlaying) return
            audioTrack?.write(data, 0, data.size)
        }
    }

    fun stopPlayback() {
        synchronized(trackLock) {
            isPlaying = false
            try {
                audioTrack?.stop()
                audioTrack?.flush()
            } catch (_: Exception) {
                // May throw if not playing
            }
            audioTrack?.release()
            audioTrack = null
        }

        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(focusChangeListener)
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying
}
