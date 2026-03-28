package com.jarvis.gateway.lifecycle

import com.jarvis.gateway.db.ConversationTurns
import com.jarvis.gateway.db.DeviceSessions
import com.jarvis.gateway.memory.MemoryChunks
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class DataLifecycleService(
    private val retentionDays: Long = System.getenv("RETENTION_DAYS")?.toLongOrNull() ?: 30
) {
    private val logger = LoggerFactory.getLogger(DataLifecycleService::class.java)

    /** Delete data older than retention period. Returns count of deleted rows. */
    fun cleanupExpired(): Int = transaction {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays)
        var totalDeleted = 0

        // Delete old memory chunks
        val memoryDeleted = MemoryChunks.deleteWhere { createdAt less cutoff }
        totalDeleted += memoryDeleted

        // Delete old conversation turns
        val turnsDeleted = ConversationTurns.deleteWhere { createdAt less cutoff }
        totalDeleted += turnsDeleted

        // Delete old sessions with no remaining turns
        val sessionsDeleted = DeviceSessions.deleteWhere { lastActiveAt less cutoff }
        totalDeleted += sessionsDeleted

        logger.info("Retention cleanup: deleted {} memory chunks, {} turns, {} sessions",
            memoryDeleted, turnsDeleted, sessionsDeleted)
        totalDeleted
    }

    /** Delete all data for a specific device. Returns count of deleted rows. */
    fun purgeDevice(deviceId: String): Int = transaction {
        // Find sessions for this device
        val sessions = DeviceSessions.select(DeviceSessions.id)
            .where { DeviceSessions.deviceId eq deviceId }
            .map { it[DeviceSessions.id] }

        var totalDeleted = 0
        for (sessionId in sessions) {
            totalDeleted += MemoryChunks.deleteWhere { MemoryChunks.sessionId eq sessionId }
            totalDeleted += ConversationTurns.deleteWhere { ConversationTurns.sessionId eq sessionId }
        }
        totalDeleted += DeviceSessions.deleteWhere { DeviceSessions.deviceId eq deviceId }

        logger.info("Purged device {}: {} rows deleted (no PII logged)", deviceId.take(8) + "...", totalDeleted)
        totalDeleted
    }
}
