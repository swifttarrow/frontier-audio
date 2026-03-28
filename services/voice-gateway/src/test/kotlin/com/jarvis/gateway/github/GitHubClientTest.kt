package com.jarvis.gateway.github

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubClientTest {

    @Test
    fun `FakeGitHubClient returns fixture PRs with asOf`() = runBlocking {
        val client = FakeGitHubClient()
        val result = client.listOpenPullRequests()
        assertEquals(2, result.items.size)
        assertEquals(42, result.items[0]["number"])
        assertEquals("Fix authentication bug", result.items[0]["title"])
        assertTrue(result.asOf.epochSecond > 0)
    }

    @Test
    fun `FakeGitHubClient returns fixture issues`() = runBlocking {
        val client = FakeGitHubClient()
        val result = client.listOpenIssues()
        assertEquals(1, result.items.size)
        assertEquals("App crashes on startup", result.items[0]["title"])
    }

    @Test
    fun `FakeGitHubClient respects limit`() = runBlocking {
        val client = FakeGitHubClient()
        val result = client.listOpenPullRequests(limit = 1)
        assertEquals(1, result.items.size)
    }

    @Test
    fun `FakeGitHubClient empty merged PRs`() = runBlocking {
        val client = FakeGitHubClient()
        val result = client.recentMergedPullRequests()
        assertTrue(result.items.isEmpty())
    }
}
