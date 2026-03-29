package com.jarvis.gateway.audio

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

interface TextToSpeech {
    suspend fun synthesize(text: String, voice: String? = null): Result<ByteArray>
}

/**
 * OpenAI TTS client. Returns raw audio bytes (PCM or MP3 depending on config).
 * OpenAI speech API returns raw PCM at 24 kHz (16-bit LE mono); the WS protocol matches that for TTS downlink.
 */
class OpenAiTts(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val model: String = "tts-1",
    private val defaultVoice: String = "alloy"
) : TextToSpeech {

    private val logger = LoggerFactory.getLogger(OpenAiTts::class.java)

    override suspend fun synthesize(text: String, voice: String?): Result<ByteArray> = runCatching {
        val response = httpClient.post("https://api.openai.com/v1/audio/speech") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody("""{"model":"$model","input":"${text.replace("\"", "\\\"")}","voice":"${voice ?: defaultVoice}","response_format":"pcm"}""")
        }

        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("TTS failed: ${response.status} - ${response.bodyAsText()}")
        }

        response.readBytes()
    }
}

/** Fake TTS for testing — returns a small sine wave PCM buffer. */
class FakeTts : TextToSpeech {
    override suspend fun synthesize(text: String, voice: String?): Result<ByteArray> {
        // Generate 0.5s of silence at 24kHz 16-bit mono (matches OpenAiTts PCM rate)
        val samples = 12000
        val pcm = ByteArray(samples * 2) // 16-bit = 2 bytes per sample
        return Result.success(pcm)
    }
}
