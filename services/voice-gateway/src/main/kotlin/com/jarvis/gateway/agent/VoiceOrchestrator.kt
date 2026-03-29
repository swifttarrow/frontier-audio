package com.jarvis.gateway.agent

import com.jarvis.gateway.errors.UserFacingError
import com.jarvis.gateway.errors.mapThrowableToUserFacing
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

interface VoiceOrchestrator {
    suspend fun handleUserUtterance(
        sessionId: UUID,
        clientTurnId: UUID,
        transcript: String,
        memoryContext: String? = null,
        /** Prior turns in this session (role, text), oldest first; must not include the current user utterance. */
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): OrchestratorResult
}

data class OrchestratorResult(
    val assistantText: String,
    val error: UserFacingError? = null
)

class LlmVoiceOrchestrator(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val toolRegistry: ToolRegistry,
    private val model: String = System.getenv("LLM_MODEL") ?: "gpt-4o",
    private val toolTimeoutMs: Long = (System.getenv("TOOL_TIMEOUT_MS")?.toLongOrNull() ?: 10000)
) : VoiceOrchestrator {

    private val logger = LoggerFactory.getLogger(LlmVoiceOrchestrator::class.java)
    private val mapper = jacksonObjectMapper()
    private val systemPrompt: String = javaClass.classLoader
        .getResourceAsStream("system-prompt.md")?.bufferedReader()?.readText()
        ?: "You are Jarvis, a voice assistant."

    override suspend fun handleUserUtterance(
        sessionId: UUID,
        clientTurnId: UUID,
        transcript: String,
        memoryContext: String?,
        conversationHistory: List<Pair<String, String>>
    ): OrchestratorResult {
        try {
            val messages = mutableListOf<Map<String, Any?>>()
            val systemContent = buildString {
                append(systemPrompt)
                if (memoryContext != null) {
                    append("\n\n## Memory from previous conversations\n")
                    append(memoryContext)
                }
            }
            messages.add(mapOf("role" to "system", "content" to systemContent))
            for ((role, text) in conversationHistory) {
                if (role != "user" && role != "assistant") continue
                if (text.isBlank()) continue
                messages.add(mapOf("role" to role, "content" to text))
            }
            messages.add(mapOf("role" to "user", "content" to transcript))

            val tools = toolRegistry.toolDefinitions.map { tool ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to tool.name,
                        "description" to tool.description,
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to tool.parameters.mapValues { (_, desc) ->
                                mapOf("type" to "string", "description" to desc)
                            }
                        )
                    )
                )
            }

            // First LLM call — may request tool calls
            var response = callLlm(messages, tools)
            var choice = extractChoice(response)
            var iterations = 0
            val maxIterations = 5

            while (choice.toolCalls.isNotEmpty() && iterations < maxIterations) {
                iterations++
                messages.add(mapOf(
                    "role" to "assistant",
                    "content" to (choice.content ?: ""),
                    "tool_calls" to choice.toolCalls.map { tc ->
                        mapOf(
                            "id" to tc.id,
                            "type" to "function",
                            "function" to mapOf(
                                "name" to tc.name,
                                "arguments" to tc.arguments
                            )
                        )
                    }
                ))

                for (tc in choice.toolCalls) {
                    val args: Map<String, Any?> = try {
                        mapper.readValue(tc.arguments)
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    try {
                        logger.info("Executing tool: {} args={}", tc.name, args)
                        val result = toolRegistry.executeTool(tc.name, args, sessionId)
                        val freshnessNote = buildFreshnessNote(result.asOf)
                        val resultContent = "${result.data}\n[Data fetched: ${result.asOf}$freshnessNote]"
                        messages.add(mapOf(
                            "role" to "tool",
                            "tool_call_id" to tc.id,
                            "content" to resultContent
                        ))
                    } catch (e: Exception) {
                        logger.error("Tool {} failed", tc.name, e)
                        val userError = mapThrowableToUserFacing(e)
                        messages.add(mapOf(
                            "role" to "tool",
                            "tool_call_id" to tc.id,
                            "content" to """{"error": "${userError.message}"}"""
                        ))
                    }
                }

                response = callLlm(messages, tools)
                choice = extractChoice(response)
            }

            return OrchestratorResult(assistantText = choice.content ?: "I'm not sure how to help with that.")
        } catch (e: Exception) {
            logger.error("Orchestrator failed for clientTurnId={}", clientTurnId, e)
            val userError = mapThrowableToUserFacing(e)
            return OrchestratorResult(assistantText = userError.speak, error = userError)
        }
    }

    private suspend fun callLlm(messages: List<Map<String, Any?>>, tools: List<Map<String, Any?>>): String {
        val body = mapper.writeValueAsString(mapOf(
            "model" to model,
            "messages" to messages,
            "tools" to tools
        ))

        val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("LLM API error: ${response.status} - ${response.bodyAsText()}")
        }

        return response.bodyAsText()
    }

    private fun extractChoice(response: String): LlmChoice {
        val json = mapper.readTree(response)
        val choice = json.get("choices")?.get(0) ?: return LlmChoice(null, emptyList())
        val message = choice.get("message") ?: return LlmChoice(null, emptyList())
        val content = message.get("content")?.asText()
        val toolCalls = message.get("tool_calls")?.map { tc ->
            ToolCall(
                id = tc.get("id").asText(),
                name = tc.get("function").get("name").asText(),
                arguments = tc.get("function").get("arguments").asText()
            )
        } ?: emptyList()
        return LlmChoice(content, toolCalls)
    }

    private fun buildFreshnessNote(asOf: Instant): String {
        val minutesAgo = ChronoUnit.MINUTES.between(asOf, Instant.now())
        return if (minutesAgo >= 3) " (STALE: ${minutesAgo} minutes old, qualify your answer)" else ""
    }

    private data class LlmChoice(val content: String?, val toolCalls: List<ToolCall>)
    private data class ToolCall(val id: String, val name: String, val arguments: String)
}

/** Simple orchestrator that echoes transcript — used when no LLM API key is set. */
class EchoOrchestrator : VoiceOrchestrator {
    override suspend fun handleUserUtterance(
        sessionId: UUID,
        clientTurnId: UUID,
        transcript: String,
        memoryContext: String?,
        conversationHistory: List<Pair<String, String>>
    ): OrchestratorResult {
        val text = if (transcript.isBlank()) {
            "I didn't catch that. Could you try again?"
        } else {
            "Echo: $transcript"
        }
        return OrchestratorResult(assistantText = text)
    }
}
