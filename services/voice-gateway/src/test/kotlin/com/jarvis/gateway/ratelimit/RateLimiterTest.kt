package com.jarvis.gateway.ratelimit

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class RateLimiterTest {

    @Test
    fun `allows requests within limit`() {
        val limiter = RateLimiter(RateLimitConfig(maxRequests = 3, windowSeconds = 60))
        assertTrue(limiter.check("key1").allowed)
        assertTrue(limiter.check("key1").allowed)
        assertTrue(limiter.check("key1").allowed)
    }

    @Test
    fun `blocks requests over limit`() {
        val limiter = RateLimiter(RateLimitConfig(maxRequests = 2, windowSeconds = 60))
        limiter.check("key1")
        limiter.check("key1")
        val result = limiter.check("key1")
        assertFalse(result.allowed)
        assertEquals(0, result.remaining)
    }

    @Test
    fun `different keys are independent`() {
        val limiter = RateLimiter(RateLimitConfig(maxRequests = 1, windowSeconds = 60))
        assertTrue(limiter.check("key1").allowed)
        assertTrue(limiter.check("key2").allowed)
        assertFalse(limiter.check("key1").allowed)
    }

    @Test
    fun `remaining count decreases`() {
        val limiter = RateLimiter(RateLimitConfig(maxRequests = 5, windowSeconds = 60))
        assertEquals(4, limiter.check("key1").remaining)
        assertEquals(3, limiter.check("key1").remaining)
        assertEquals(2, limiter.check("key1").remaining)
    }
}
