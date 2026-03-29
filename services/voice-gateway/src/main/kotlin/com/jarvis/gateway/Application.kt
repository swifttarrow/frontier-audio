package com.jarvis.gateway

import com.jarvis.gateway.agent.*
import com.jarvis.gateway.audio.*
import com.jarvis.gateway.config.EnvSupport
import com.jarvis.gateway.config.IntegrationConfigProvider
import com.jarvis.gateway.db.DatabaseFactory
import com.jarvis.gateway.db.SessionRepository
import com.jarvis.gateway.lifecycle.DataLifecycleService
import com.jarvis.gateway.memory.*
import com.jarvis.gateway.observability.Metrics
import com.jarvis.gateway.operational.*
import com.jarvis.gateway.ws.SessionManager
import com.jarvis.gateway.ws.voiceWebSocket
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger("Application")

fun main() {
    val config = IntegrationConfigProvider.load()
    logger.info(
        "Starting Jarvis voice-gateway, defaultRepo={}",
        config.repoDisplayName ?: "(none — user selects in conversation)"
    )

    val databaseUrl = EnvSupport.get("DATABASE_URL")
    if (databaseUrl != null) {
        DatabaseFactory.init(databaseUrl)
        logger.info("Database initialized")
    } else {
        logger.warn("DATABASE_URL not set — running without persistence (dev mode)")
    }

    val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 30_000
        }
    }

    val openAiKey = EnvSupport.get("OPENAI_API_KEY") ?: ""
    val sttModel = EnvSupport.get("STT_MODEL") ?: "whisper-large-v3-turbo"
    val sttLanguage = EnvSupport.get("STT_LANGUAGE")?.trim()?.takeIf { it.isNotEmpty() }
    val stt: SpeechToText = if (openAiKey.isNotBlank()) {
        OpenAiStt(httpClient, openAiKey, model = sttModel, language = sttLanguage)
    } else {
        FakeStt()
    }
    val tts: TextToSpeech = if (openAiKey.isNotBlank()) OpenAiTts(httpClient, openAiKey) else FakeTts()

    if (openAiKey.isBlank()) {
        logger.warn("OPENAI_API_KEY not set — using fake STT/TTS")
    }

    val githubToken = EnvSupport.get("GITHUB_TOKEN")
    val tavilyApiKey = EnvSupport.get("TAVILY_API_KEY")
    if (tavilyApiKey.isNullOrBlank()) {
        logger.warn("TAVILY_API_KEY not set — web_search tool will return a configuration error until set.")
    }
    val cacheTtl = EnvSupport.get("GITHUB_CACHE_TTL_SECONDS")?.toLongOrNull() ?: 180

    if (githubToken != null) {
        logger.info("GITHUB_TOKEN is set (GitHub API calls use authenticated rate limits).")
    } else {
        logger.warn(
            "GITHUB_TOKEN is not set — GitHub REST calls are unauthenticated (stricter rate limits). " +
                "Add GITHUB_TOKEN to the repo-root .env or export it before starting the gateway."
        )
    }

    // Operational API adapter
    val operationalAdapter: OperationalApiAdapter = if (config.operationalApiBaseUrl.isNullOrBlank()) {
        logger.info("Using FakeOperationalAdapter (OPERATIONAL_API_BASE_URL not set)")
        FakeOperationalAdapter()
    } else {
        logger.info("Operational API base URL: {}", config.operationalApiBaseUrl)
        FakeOperationalAdapter() // Real HTTP adapter TBD
    }

    val repository = SessionRepository()
    val sessionManager = SessionManager(repository, config)

    // Tool registry and orchestrator (GitHub repo is per session; optional env default seeds new sessions)
    val toolRegistry = ToolRegistry(httpClient, githubToken, cacheTtl, sessionManager, operationalAdapter, tavilyApiKey)
    val orchestrator: VoiceOrchestrator = if (openAiKey.isNotBlank()) {
        LlmVoiceOrchestrator(httpClient, openAiKey, toolRegistry)
    } else {
        logger.warn("OPENAI_API_KEY not set — using echo orchestrator")
        EchoOrchestrator()
    }

    // Memory service
    val memoryRepository = MemoryRepository()
    val memoryService: MemoryService = SimpleMemoryService(memoryRepository)

    // Data lifecycle
    val dataLifecycleService = DataLifecycleService()
    val adminKey = EnvSupport.get("ADMIN_API_KEY") ?: ""

    val turnPipeline = TurnPipeline(stt, tts, repository, orchestrator, memoryService)

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(60)
            maxFrameSize = Long.MAX_VALUE
        }
        routing {
            get("/health") {
                call.respondText("OK")
            }

            get("/metrics") {
                call.respondText(Metrics.render(), ContentType.Text.Plain)
            }

            // Admin: delete device data
            delete("/v1/devices/{deviceId}/data") {
                val key = call.request.header("X-Admin-Key")
                if (adminKey.isNotBlank() && key != adminKey) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid admin key")
                    return@delete
                }
                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "deviceId required")
                    return@delete
                }
                val deleted = dataLifecycleService.purgeDevice(deviceId)
                call.respondText("""{"deleted": $deleted}""", ContentType.Application.Json)
            }

            voiceWebSocket(sessionManager, turnPipeline)
        }
    }.start(wait = true)
}
