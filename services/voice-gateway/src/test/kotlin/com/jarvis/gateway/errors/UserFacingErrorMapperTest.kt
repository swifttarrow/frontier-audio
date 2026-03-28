package com.jarvis.gateway.errors

import com.jarvis.gateway.github.GitHubApiException
import com.jarvis.gateway.operational.OperationalApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserFacingErrorMapperTest {

    @Test
    fun `GitHub rate limit maps to retryable error`() {
        val ex = GitHubApiException("github.rate_limited", "Rate limit exceeded", true)
        val error = mapThrowableToUserFacing(ex)
        assertEquals("github.rate_limited", error.code)
        assertTrue(error.retryable)
        assertTrue(error.speak.length <= 120)
        assertTrue(error.speak.contains("rate limit", ignoreCase = true))
    }

    @Test
    fun `GitHub not found maps to non-retryable error`() {
        val ex = GitHubApiException("github.not_found", "Not found", false)
        val error = mapThrowableToUserFacing(ex)
        assertEquals("github.not_found", error.code)
        assertEquals(false, error.retryable)
    }

    @Test
    fun `Operational unavailable maps to retryable error`() {
        val ex = OperationalApiException("operational.unavailable", "Timeout", true)
        val error = mapThrowableToUserFacing(ex)
        assertTrue(error.retryable)
        assertTrue(error.speak.contains("unavailable", ignoreCase = true))
    }

    @Test
    fun `Unknown exception maps to internal error`() {
        val ex = RuntimeException("Unexpected")
        val error = mapThrowableToUserFacing(ex)
        assertEquals("internal.error", error.code)
        assertEquals(false, error.retryable)
        assertEquals("Something went wrong. Please try again.", error.speak)
    }

    @Test
    fun `speak never contains PAT or stack traces`() {
        val ex = GitHubApiException("github.error", "Failed with ghp_secrettoken123456 in header", true)
        val error = mapThrowableToUserFacing(ex)
        // speak should use template, not raw message
        assertTrue(!error.speak.contains("ghp_"))
    }
}
