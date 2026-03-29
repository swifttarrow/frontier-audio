package com.jarvis.gateway.ws

import com.jarvis.gateway.config.IntegrationConfig
import com.jarvis.gateway.db.SessionRepository
import com.jarvis.gateway.github.GitHubIdentifiers
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ActiveSession(
    val sessionId: UUID,
    val deviceId: String,
    val sessionToken: String,
    @Volatile var repoDisplayName: String,
    @Volatile var githubOwner: String? = null,
    @Volatile var githubRepo: String? = null,
    @Volatile var interrupted: Boolean = false,
    @Volatile var activeTurnId: String? = null
)

class SessionManager(
    private val repository: SessionRepository,
    private val config: IntegrationConfig
) {
    private val activeSessions = ConcurrentHashMap<UUID, ActiveSession>()

    fun createSession(deviceId: String): ActiveSession {
        val token = generateToken()
        val tokenHash = hashToken(token)
        val session = repository.createSession(deviceId, tokenHash)
        val initial = config.repoDisplayName?.let { display ->
            val parts = display.split("/", limit = 2)
            if (parts.size == 2 &&
                GitHubIdentifiers.isValidOwnerOrRepo(parts[0]) &&
                GitHubIdentifiers.isValidOwnerOrRepo(parts[1])
            ) {
                Triple(parts[0], parts[1], display)
            } else {
                null
            }
        }
        val active = ActiveSession(
            sessionId = session.id,
            deviceId = deviceId,
            sessionToken = token,
            repoDisplayName = initial?.third ?: "not selected",
            githubOwner = initial?.first,
            githubRepo = initial?.second
        )
        activeSessions[session.id] = active
        return active
    }

    fun getActive(sessionId: UUID): ActiveSession? = activeSessions[sessionId]

    fun removeSession(sessionId: UUID) {
        activeSessions.remove(sessionId)
    }

    fun markInterrupted(sessionId: UUID) {
        activeSessions[sessionId]?.interrupted = true
    }

    fun clearInterrupt(sessionId: UUID) {
        activeSessions[sessionId]?.interrupted = false
    }

    fun setActiveTurn(sessionId: UUID, turnId: String?) {
        activeSessions[sessionId]?.activeTurnId = turnId
    }

    companion object {
        private val random = SecureRandom()

        fun generateToken(): String {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun hashToken(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(digest.digest(token.toByteArray()))
        }
    }
}
