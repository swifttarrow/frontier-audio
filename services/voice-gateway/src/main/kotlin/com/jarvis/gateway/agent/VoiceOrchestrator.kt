package com.jarvis.gateway.agent

import com.jarvis.gateway.audio.TurnInterruptedException
import com.jarvis.gateway.config.EnvSupport
import com.jarvis.gateway.errors.UserFacingError
import com.jarvis.gateway.errors.mapThrowableToUserFacing
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
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
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): OrchestratorResult

    /**
     * Streams visible assistant text via [onAssistantTextDelta]. When [shouldAbort] is true, aborts
     * remote streaming and throws [TurnInterruptedException].
     */
    suspend fun handleUserUtteranceStreaming(
        sessionId: UUID,
        clientTurnId: UUID,
        transcript: String,
        memoryContext: String?,
        conversationHistory: List<Pair<String, String>>,
        shouldAbort: () -> Boolean,
        onAssistantTextDelta: suspend (String) -> Unit
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
    private val model: String = EnvSupport.get("LLM_MODEL") ?: "gpt-5.4-nano"
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
    ): OrchestratorResult = handleUserUtteranceStreaming(
        sessionId, clientTurnId, transcript, memoryContext, conversationHistory,
        shouldAbort = { false },
        onAssistantTextDelta = {}
    )

    override suspend fun handleUserUtteranceStreaming(
        sessionId: UUID,
        clientTurnId: UUID,
        transcript: String,
        memoryContext: String?,
        conversationHistory: List<Pair<String, String>>,
        shouldAbort: () -> Boolean,
        onAssistantTextDelta: suspend (String) -> Unit
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

            if (shouldAbort()) throw TurnInterruptedException()

            var choice = callLlmStreaming(messages, tools, shouldAbort, onAssistantTextDelta)
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
                        if (shouldAbort()) throw TurnInterruptedException()
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

                if (shouldAbort()) throw TurnInterruptedException()
                choice = callLlmStreaming(messages, tools, shouldAbort, onAssistantTextDelta)
            }

            return OrchestratorResult(assistantText = choice.content ?: "I'm not sure how to help with that.")
        } catch (e: TurnInterruptedException) {
            throw e
        } catch (e: Exception) {
            logger.error("Orchestrator failed for clientTurnId={}", clientTurnId, e)
            val userError = mapThrowableToUserFacing(e)
            return OrchestratorResult(assistantText = userError.speak, error = userError)
        }
    }

    private suspend fun callLlmStreaming(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>,
        shouldAbort: () -> Boolean,
        onContentDelta: suspend (String) -> Unit
    ): LlmChoice {
        val body = mapper.writeValueAsString(
            mapOf(
                "model" to model,
                "messages" to messages,
                "tools" to tools,
                "stream" to true
            )
        )

        val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("LLM API error: ${response.status} - ${response.bodyAsText()}")
        }

        val channel = response.bodyAsChannel()
        val contentBuf = StringBuilder()
        val toolAcc = StreamingToolCallAccumulator()
        var sawToolCallDelta = false

        try {
            while (!channel.isClosedForRead) {
                if (shouldAbort()) {
                    channel.cancel(CancellationException("turn aborted"))
                    throw TurnInterruptedException()
                }
                val line = channel.readUTF8Line() ?: break
                if (line.isEmpty()) continue
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                val json = try {
                    mapper.readTree(data)
                } catch (_: Exception) {
                    continue
                }
                val choice = json.get("choices")?.get(0) ?: continue

                val delta = choice.get("delta") ?: continue
                val toolCallsNode = delta.get("tool_calls")
                if (toolCallsNode != null && toolCallsNode.isArray && toolCallsNode.size() > 0) {
                    sawToolCallDelta = true
                    toolAcc.addDelta(toolCallsNode)
                }
                delta.get("content")?.asText()?.let { chunk ->
                    if (chunk.isEmpty()) return@let
                    contentBuf.append(chunk)
                    if (!sawToolCallDelta) {
                        onContentDelta(chunk)
                    }
                }
            }
        } finally {
            channel.cancel(CancellationException("stream closed"))
        }

        val toolCalls = toolAcc.toToolCalls()
            .filter { it.second.isNotBlank() }
            .map { ToolCall(id = it.first, name = it.second, arguments = it.third) }
        return LlmChoice(
            content = contentBuf.toString().ifBlank { null },
            toolCalls = toolCalls
        )
    }

    private fun buildFreshnessNote(asOf: Instant): String {
        val minutesAgo = ChronoUnit.MINUTES.between(asOf, Instant.now())
        return if (minutesAgo >= 3) " (STALE: ${minutesAgo} minutes old, qualify your answer)" else ""
    }

    private data class LlmChoice(val content: String?, val toolCalls: List<ToolCall>)
    private data class ToolCall(val id: String, val name: String, val arguments: String)

    private class StreamingToolCallAccumulator {
        private val byIndex = sortedMapOf<Int, MutableToolCallFrag>()

        fun addDelta(toolCallsNode: JsonNode) {
            if (!toolCallsNode.isArray) return
            for (i in 0 until toolCallsNode.size()) {
                val node = toolCallsNode.get(i)
                val idx = node.get("index")?.asInt() ?: continue
                val entry = byIndex.getOrPut(idx) { MutableToolCallFrag() }
                node.get("id")?.asText()?.takeIf { it.isNotEmpty() }?.let { entry.id = it }
                val fn = node.get("function") ?: continue
                fn.get("name")?.asText()?.takeIf { it.isNotEmpty() }?.let { entry.name = it }
                fn.get("arguments")?.asText()?.let { entry.arguments.append(it) }
            }
        }

        fun toToolCalls(): List<Triple<String, String, String>> =
            byIndex.values.mapNotNull { it.finish() }
    }

    private class MutableToolCallFrag {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun finish(): Triple<String, String, String>? {
            if (name.isBlank() && id.isBlank() && arguments.isEmpty()) return null
            return Triple(id, name, arguments.toString())
        }
    }
}

/** Simple orchestrator that echoes transcript — used when no LLM API key is set. */
class EchoOrchestrator : VoiceOrchestrator {
    override suspend fun handleUserUtterance(
        sessionId: UUID,
        clientTurnId: UUID,
        transcript: String,
        memoryContext: String?,
        conversationHistory: List<Pair<String, String>>
    ): OrchestratorResult = handleUserUtteranceStreaming(
        sessionId, clientTurnId, transcript, memoryContext, conversationHistory,
        shouldAbort = { false },
        onAssistantTextDelta = {}
    )

    override suspend fun handleUserUtteranceStreaming(
        sessionId: UUID,
        clientTurnId: UUID,
        transcript: String,
        memoryContext: String?,
        conversationHistory: List<Pair<String, String>>,
        shouldAbort: () -> Boolean,
        onAssistantTextDelta: suspend (String) -> Unit
    ): OrchestratorResult {
        if (shouldAbort()) throw TurnInterruptedException()
        val text = if (transcript.isBlank()) {
            "I didn't catch that. Could you try again?"
        } else {
            "Echo: $transcript"
        }
        onAssistantTextDelta(text)
        return OrchestratorResult(assistantText = text)
    }
}
