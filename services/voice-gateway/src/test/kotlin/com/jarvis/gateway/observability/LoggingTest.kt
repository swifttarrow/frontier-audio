package com.jarvis.gateway.observability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class LoggingTest {

    @Test
    fun `redactSecrets hides GitHub PATs`() {
        val input = "Token is ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefgh"
        val result = redactSecrets(input)
        assertContains(result, "ghp_[REDACTED]")
        assert(!result.contains("ABCDEFGHIJKLMNOP"))
    }

    @Test
    fun `redactSecrets hides Bearer tokens`() {
        val input = "Authorization: Bearer sk-abc123.def456"
        val result = redactSecrets(input)
        assertContains(result, "Bearer [REDACTED]")
        assert(!result.contains("sk-abc123"))
    }

    @Test
    fun `redactSecrets hides query param tokens`() {
        val input = "https://example.com/api?token=secret123&other=ok"
        val result = redactSecrets(input)
        assertContains(result, "?token=[REDACTED]")
        assert(!result.contains("secret123"))
    }

    @Test
    fun `redactSecrets leaves clean messages unchanged`() {
        val input = "Session started for device-123"
        assertEquals(input, redactSecrets(input))
    }
}
