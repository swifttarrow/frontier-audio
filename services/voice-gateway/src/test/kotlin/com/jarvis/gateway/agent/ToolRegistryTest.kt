package com.jarvis.gateway.agent

import com.jarvis.gateway.config.IntegrationConfig
import com.jarvis.gateway.db.DatabaseFactory
import com.jarvis.gateway.db.SessionRepository
import com.jarvis.gateway.operational.FakeOperationalAdapter
import com.jarvis.gateway.ws.SessionManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ToolRegistryTest {

    @Test
    fun `github_list_open_prs returns no_repository_selected without active repo`() = runBlocking {
        DatabaseFactory.initForTest()
        val sessionManager = SessionManager(SessionRepository(), IntegrationConfig(null, null, null))
        val active = sessionManager.createSession("device-t1")
        val http = HttpClient(CIO)
        val registry = ToolRegistry(http, null, 180, sessionManager, FakeOperationalAdapter())

        val result = registry.executeTool("github_list_open_prs", emptyMap(), active.sessionId)

        assertContains(result.data, "no_repository_selected")
        http.close()
    }

    @Test
    fun `github_set_active_repository updates session display and owner repo`() = runBlocking {
        DatabaseFactory.initForTest()
        val sessionManager = SessionManager(SessionRepository(), IntegrationConfig(null, null, null))
        val active = sessionManager.createSession("device-t2")
        val http = HttpClient(CIO)
        val registry = ToolRegistry(http, null, 180, sessionManager, FakeOperationalAdapter())

        val set = registry.executeTool(
            "github_set_active_repository",
            mapOf("full_name" to "acme/widget"),
            active.sessionId
        )
        assertContains(set.data, "acme/widget")
        assertEquals("acme", active.githubOwner)
        assertEquals("widget", active.githubRepo)
        assertEquals("acme/widget", active.repoDisplayName)
        http.close()
    }
}
