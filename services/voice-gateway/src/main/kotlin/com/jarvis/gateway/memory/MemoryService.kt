package com.jarvis.gateway.memory

import com.jarvis.gateway.format.VoiceFriendlyTime
import org.slf4j.LoggerFactory
import java.util.UUID

interface MemoryService {
    suspend fun appendTurnSummary(sessionId: UUID, clientTurnId: UUID, userText: String, assistantText: String)
    suspend fun recentChunks(sessionId: UUID, limit: Int = 10): List<MemoryChunk>
    fun buildMemoryContext(chunks: List<MemoryChunk>): String?
}

/**
 * Simple memory service that stores truncated summaries of each turn.
 * Uses a cheap heuristic (first 400 chars of combined user+assistant text)
 * rather than an LLM summary call, for MVP speed.
 */
class SimpleMemoryService(
    private val repository: MemoryRepository
) : MemoryService {

    private val logger = LoggerFactory.getLogger(SimpleMemoryService::class.java)

    override suspend fun appendTurnSummary(
        sessionId: UUID,
        clientTurnId: UUID,
        userText: String,
        assistantText: String
    ) {
        try {
            val summary = buildSummary(userText, assistantText)
            repository.insertChunk(sessionId, summary, listOf(clientTurnId))
            logger.debug("Memory chunk saved for session={}, turn={}", sessionId, clientTurnId)
        } catch (e: Exception) {
            // Memory save failure should not fail the turn
            logger.error("Failed to save memory chunk: session={}, turn={}", sessionId, clientTurnId, e)
        }
    }

    override suspend fun recentChunks(sessionId: UUID, limit: Int): List<MemoryChunk> {
        return try {
            repository.recentChunks(sessionId, limit)
        } catch (e: Exception) {
            logger.error("Failed to retrieve memory chunks: session={}", sessionId, e)
            emptyList()
        }
    }

    override fun buildMemoryContext(chunks: List<MemoryChunk>): String? {
        if (chunks.isEmpty()) return null
        return chunks.joinToString("\n") { chunk ->
            val whenStr = VoiceFriendlyTime.formatUtc(chunk.createdAt.toInstant())
            "[$whenStr UTC] ${chunk.summary}"
        }
    }

    internal fun buildSummary(userText: String, assistantText: String): String {
        val combined = "User asked: ${userText.take(200)}\nAssistant replied: ${assistantText.take(200)}"
        return combined.take(400)
    }
}
