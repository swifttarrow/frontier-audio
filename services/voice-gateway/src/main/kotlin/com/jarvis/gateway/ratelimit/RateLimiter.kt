package com.jarvis.gateway.ratelimit

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class RateLimitConfig(
    val maxRequests: Int,
    val windowSeconds: Long
)

data class RateLimitResult(
    val allowed: Boolean,
    val remaining: Int,
    val resetAt: Instant
)

/**
 * Simple fixed-window rate limiter, keyed by arbitrary string (deviceId, IP, etc.).
 */
class RateLimiter(private val config: RateLimitConfig) {

    private data class Window(
        val count: Int,
        val windowStart: Instant
    )

    private val windows = ConcurrentHashMap<String, Window>()

    fun check(key: String): RateLimitResult {
        val now = Instant.now()
        val window = windows.compute(key) { _, existing ->
            if (existing == null || now.epochSecond - existing.windowStart.epochSecond >= config.windowSeconds) {
                Window(1, now)
            } else {
                existing.copy(count = existing.count + 1)
            }
        }!!

        val resetAt = window.windowStart.plusSeconds(config.windowSeconds)
        val remaining = maxOf(0, config.maxRequests - window.count)
        val allowed = window.count <= config.maxRequests

        return RateLimitResult(allowed, remaining, resetAt)
    }

    fun cleanup() {
        val now = Instant.now()
        windows.entries.removeIf { (_, window) ->
            now.epochSecond - window.windowStart.epochSecond >= config.windowSeconds * 2
        }
    }
}

/** Pre-configured rate limiters for the gateway. */
object GatewayRateLimiters {
    val audioCommitPerDevice = RateLimiter(RateLimitConfig(maxRequests = 30, windowSeconds = 3600))
    val connectPerIp = RateLimiter(RateLimitConfig(maxRequests = 10, windowSeconds = 3600))
}
