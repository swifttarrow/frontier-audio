package com.jarvis.gateway.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IntegrationConfigTest {

    @Test
    fun `valid GitHub URL parses display name`() {
        val url = "https://github.com/anthropics/claude-code"
        val normalized = IntegrationConfigProvider.validateAndNormalize(url)
        assertEquals("https://github.com/anthropics/claude-code", normalized)
        assertEquals("anthropics/claude-code", IntegrationConfigProvider.parseDisplayName(normalized))
    }

    @Test
    fun `trailing slash is normalized`() {
        val url = "https://github.com/owner/repo/"
        val normalized = IntegrationConfigProvider.validateAndNormalize(url)
        assertEquals("https://github.com/owner/repo", normalized)
    }

    @Test
    fun `invalid URL is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            IntegrationConfigProvider.validateAndNormalize("file:///etc/passwd")
        }
        assertFailsWith<IllegalArgumentException> {
            IntegrationConfigProvider.validateAndNormalize("https://gitlab.com/owner/repo")
        }
        assertFailsWith<IllegalArgumentException> {
            IntegrationConfigProvider.validateAndNormalize("not-a-url")
        }
    }

    @Test
    fun `URL with dots and hyphens is valid`() {
        val url = "https://github.com/my-org/my.repo_v2"
        val normalized = IntegrationConfigProvider.validateAndNormalize(url)
        assertEquals("my-org/my.repo_v2", IntegrationConfigProvider.parseDisplayName(normalized))
    }
}
