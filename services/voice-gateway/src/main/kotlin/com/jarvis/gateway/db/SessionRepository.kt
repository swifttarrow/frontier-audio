package com.jarvis.gateway.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class DeviceSession(
    val id: UUID,
    val deviceId: String,
    val sessionTokenHash: String,
    val createdAt: OffsetDateTime,
    val lastActiveAt: OffsetDateTime
)

data class ConversationTurn(
    val id: UUID,
    val sessionId: UUID,
    val clientTurnId: UUID,
    val role: String,
    val text: String?,
    val audioArtifactRef: String?,
    val createdAt: OffsetDateTime
)

class SessionRepository {

    fun createSession(deviceId: String, sessionTokenHash: String): DeviceSession = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = UUID.randomUUID()
        DeviceSessions.insert {
            it[DeviceSessions.id] = id
            it[DeviceSessions.deviceId] = deviceId
            it[DeviceSessions.sessionTokenHash] = sessionTokenHash
            it[DeviceSessions.createdAt] = now
            it[DeviceSessions.lastActiveAt] = now
        }
        DeviceSession(id, deviceId, sessionTokenHash, now, now)
    }

    fun updateLastActive(sessionId: UUID) = transaction {
        DeviceSessions.update({ DeviceSessions.id eq sessionId }) {
            it[lastActiveAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    fun findTurnByClientTurnId(sessionId: UUID, clientTurnId: UUID, role: String): ConversationTurn? = transaction {
        ConversationTurns.selectAll().where {
            (ConversationTurns.sessionId eq sessionId) and
                (ConversationTurns.clientTurnId eq clientTurnId) and
                (ConversationTurns.role eq role)
        }.firstOrNull()?.toConversationTurn()
    }

    /**
     * Most recent [limit] rows for the session, oldest-first (suitable for LLM context).
     */
    fun recentTurns(sessionId: UUID, limit: Int = 40): List<ConversationTurn> = transaction {
        ConversationTurns.selectAll().where { ConversationTurns.sessionId eq sessionId }
            .orderBy(ConversationTurns.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toConversationTurn() }
            .reversed()
    }

    fun insertTurn(sessionId: UUID, clientTurnId: UUID, role: String, text: String?): ConversationTurn = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = UUID.randomUUID()
        ConversationTurns.insert {
            it[ConversationTurns.id] = id
            it[ConversationTurns.sessionId] = sessionId
            it[ConversationTurns.clientTurnId] = clientTurnId
            it[ConversationTurns.role] = role
            it[ConversationTurns.text] = text
            it[ConversationTurns.createdAt] = now
        }
        ConversationTurn(id, sessionId, clientTurnId, role, text, null, now)
    }

    private fun ResultRow.toConversationTurn() = ConversationTurn(
        id = this[ConversationTurns.id],
        sessionId = this[ConversationTurns.sessionId],
        clientTurnId = this[ConversationTurns.clientTurnId],
        role = this[ConversationTurns.role],
        text = this[ConversationTurns.text],
        audioArtifactRef = this[ConversationTurns.audioArtifactRef],
        createdAt = this[ConversationTurns.createdAt]
    )
}
