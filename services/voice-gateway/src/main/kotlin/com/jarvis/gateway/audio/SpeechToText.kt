package com.jarvis.gateway.audio

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

interface SpeechToText {
    suspend fun transcribe(pcmAudio: ByteArray): Result<String>
}

/**
 * OpenAI Speech-to-Text client (`/v1/audio/transcriptions`).
 * Sends raw PCM as WAV. Model ID must match what your API key supports (e.g. whisper-1, gpt-4o-mini-transcribe).
 */
class OpenAiStt(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val model: String = "whisper-large-v3-turbo"
) : SpeechToText {

    private val logger = LoggerFactory.getLogger(OpenAiStt::class.java)

    override suspend fun transcribe(pcmAudio: ByteArray): Result<String> = runCatching {
        val wavBytes = pcmToWav(pcmAudio, sampleRate = 16000, channels = 1, bitsPerSample = 16)

        val response = httpClient.submitFormWithBinaryData(
            url = "https://api.openai.com/v1/audio/transcriptions",
            formData = formData {
                append("model", model)
                append("file", wavBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                    append(HttpHeaders.ContentType, "audio/wav")
                })
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }

        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("STT failed: ${response.status} - ${response.bodyAsText()}")
        }

        val body = response.bodyAsText()
        // Simple JSON parse for {"text": "..."}
        val textMatch = Regex(""""text"\s*:\s*"([^"]*?)"""").find(body)
        textMatch?.groupValues?.get(1) ?: throw RuntimeException("No text in STT response")
    }
}

/** Fake STT for testing — returns a fixed transcript. */
class FakeStt(private val transcript: String = "Hello from test") : SpeechToText {
    override suspend fun transcribe(pcmAudio: ByteArray): Result<String> = Result.success(transcript)
}

/** Wrap raw PCM bytes into a minimal WAV container. */
fun pcmToWav(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = pcm.size
    val headerSize = 44
    val wav = ByteArray(headerSize + dataSize)

    fun writeInt(offset: Int, value: Int) {
        wav[offset] = (value and 0xFF).toByte()
        wav[offset + 1] = ((value shr 8) and 0xFF).toByte()
        wav[offset + 2] = ((value shr 16) and 0xFF).toByte()
        wav[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
    fun writeShort(offset: Int, value: Int) {
        wav[offset] = (value and 0xFF).toByte()
        wav[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    // RIFF header
    "RIFF".toByteArray().copyInto(wav, 0)
    writeInt(4, 36 + dataSize)
    "WAVE".toByteArray().copyInto(wav, 8)
    // fmt chunk
    "fmt ".toByteArray().copyInto(wav, 12)
    writeInt(16, 16) // chunk size
    writeShort(20, 1) // PCM format
    writeShort(22, channels)
    writeInt(24, sampleRate)
    writeInt(28, byteRate)
    writeShort(32, blockAlign)
    writeShort(34, bitsPerSample)
    // data chunk
    "data".toByteArray().copyInto(wav, 36)
    writeInt(40, dataSize)
    pcm.copyInto(wav, 44)

    return wav
}
