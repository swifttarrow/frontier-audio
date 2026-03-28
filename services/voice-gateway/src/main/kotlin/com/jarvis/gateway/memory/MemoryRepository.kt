package com.jarvis.gateway.memory

import com.jarvis.gateway.db.DeviceSessions
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object MemoryChunks : Table("memory_chunks") {
    val id = uuid("id")
    val sessionId = uuid("session_id").references(DeviceSessions.id)
    val summary = text("summary")
    val sourceTurnIds = text("source_turn_ids") // JSONB stored as text for Exposed compatibility
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

data class MemoryChunk(
    val id: UUID,
    val sessionId: UUID,
    val summary: String,
    val sourceTurnIds: List<UUID>,
    val createdAt: OffsetDateTime
)

class MemoryRepository {

    fun insertChunk(sessionId: UUID, summary: String, sourceTurnIds: List<UUID>): MemoryChunk = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = UUID.randomUUID()
        val turnIdsJson = "[${sourceTurnIds.joinToString(",") { "\"$it\"" }}]"

        MemoryChunks.insert {
            it[MemoryChunks.id] = id
            it[MemoryChunks.sessionId] = sessionId
            it[MemoryChunks.summary] = summary.take(8000) // enforce max length
            it[MemoryChunks.sourceTurnIds] = turnIdsJson
            it[MemoryChunks.createdAt] = now
        }
        MemoryChunk(id, sessionId, summary.take(8000), sourceTurnIds, now)
    }

    fun recentChunks(sessionId: UUID, limit: Int = 10): List<MemoryChunk> = transaction {
        MemoryChunks.selectAll()
            .where { MemoryChunks.sessionId eq sessionId }
            .orderBy(MemoryChunks.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                MemoryChunk(
                    id = row[MemoryChunks.id],
                    sessionId = row[MemoryChunks.sessionId],
                    summary = row[MemoryChunks.summary],
                    sourceTurnIds = parseTurnIds(row[MemoryChunks.sourceTurnIds]),
                    createdAt = row[MemoryChunks.createdAt]
                )
            }
            .reversed() // oldest first for context
    }

    private fun parseTurnIds(json: String): List<UUID> {
        return try {
            json.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .map { UUID.fromString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
