package com.jarvis.gateway.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object DeviceSessions : Table("device_sessions") {
    val id = uuid("id")
    val deviceId = text("device_id")
    val sessionTokenHash = text("session_token_hash")
    val createdAt = timestampWithTimeZone("created_at")
    val lastActiveAt = timestampWithTimeZone("last_active_at")

    override val primaryKey = PrimaryKey(id)
}

object ConversationTurns : Table("conversation_turns") {
    val id = uuid("id")
    val sessionId = uuid("session_id").references(DeviceSessions.id)
    val clientTurnId = uuid("client_turn_id")
    val role = varchar("role", 20)
    val text = text("text").nullable()
    val audioArtifactRef = text("audio_artifact_ref").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
