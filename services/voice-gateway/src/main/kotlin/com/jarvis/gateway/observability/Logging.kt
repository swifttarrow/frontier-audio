package com.jarvis.gateway.observability

import org.slf4j.MDC
import java.util.UUID

/**
 * Sets MDC context for structured logging within a coroutine block.
 * All log lines within the block will include sessionId and turnId.
 */
suspend fun <T> withLoggingContext(sessionId: UUID, turnId: UUID? = null, block: suspend () -> T): T {
    val previousSession = MDC.get("sessionId")
    val previousTurn = MDC.get("turnId")
    try {
        MDC.put("sessionId", sessionId.toString())
        turnId?.let { MDC.put("turnId", it.toString()) }
        return block()
    } finally {
        if (previousSession != null) MDC.put("sessionId", previousSession) else MDC.remove("sessionId")
        if (previousTurn != null) MDC.put("turnId", previousTurn) else MDC.remove("turnId")
    }
}

/**
 * Redact known secret patterns from a string.
 * Used by the log encoder to prevent accidental secret leakage.
 */
fun redactSecrets(message: String): String {
    var result = message
    // GitHub PATs
    result = result.replace(Regex("""ghp_[A-Za-z0-9]{36}"""), "ghp_[REDACTED]")
    result = result.replace(Regex("""ghs_[A-Za-z0-9]{36}"""), "ghs_[REDACTED]")
    // Bearer tokens
    result = result.replace(Regex("""Bearer\s+[A-Za-z0-9._\-/+=]+"""), "Bearer [REDACTED]")
    // Query param tokens
    result = result.replace(Regex("""\?token=[^&\s]+"""), "?token=[REDACTED]")
    // GITHUB_TOKEN env value patterns
    result = result.replace(Regex("""GITHUB_TOKEN=[^\s]+"""), "GITHUB_TOKEN=[REDACTED]")
    return result
}
