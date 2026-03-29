package com.jarvis.gateway.memory

import com.jarvis.gateway.db.DatabaseFactory
import com.jarvis.gateway.db.SessionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

class MemoryServiceTest {

    private val sessionRepo = SessionRepository()
    private val memoryRepo = MemoryRepository()
    private val service = SimpleMemoryService(memoryRepo)

    @BeforeTest
    fun setup() {
        DatabaseFactory.initForTest()
    }

    @Test
    fun `appendTurnSummary stores chunk for session`() = runBlocking {
        val session = sessionRepo.createSession("device-1", "hash")
        val turnId = UUID.randomUUID()
        service.appendTurnSummary(session.id, turnId, "What is the budget?", "The budget is 50k.")

        val chunks = service.recentChunks(session.id)
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].summary.contains("budget"))
    }

    @Test
    fun `sessions are isolated`() = runBlocking {
        val sessionA = sessionRepo.createSession("device-a", "hash-a")
        val sessionB = sessionRepo.createSession("device-b", "hash-b")

        service.appendTurnSummary(sessionA.id, UUID.randomUUID(), "Topic A", "Response A")
        service.appendTurnSummary(sessionB.id, UUID.randomUUID(), "Topic B", "Response B")

        val chunksA = service.recentChunks(sessionA.id)
        val chunksB = service.recentChunks(sessionB.id)

        assertEquals(1, chunksA.size)
        assertEquals(1, chunksB.size)
        assertTrue(chunksA[0].summary.contains("Topic A"))
        assertTrue(chunksB[0].summary.contains("Topic B"))
    }

    @Test
    fun `recentChunks returns empty for new session`() = runBlocking {
        val session = sessionRepo.createSession("device-new", "hash")
        val chunks = service.recentChunks(session.id)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `buildMemoryContext returns null for empty chunks`() {
        val result = service.buildMemoryContext(emptyList())
        assertNull(result)
    }

    @Test
    fun `buildMemoryContext formats chunks with timestamps`() = runBlocking {
        val session = sessionRepo.createSession("device-ctx", "hash")
        service.appendTurnSummary(session.id, UUID.randomUUID(), "Q1", "A1")
        service.appendTurnSummary(session.id, UUID.randomUUID(), "Q2", "A2")

        val chunks = service.recentChunks(session.id)
        val ctx = requireNotNull(service.buildMemoryContext(chunks))
        assertTrue(ctx.contains("Q1"))
        assertTrue(ctx.contains("Q2"))
    }

    @Test
    fun `buildSummary truncates long text`() {
        val longText = "a".repeat(500)
        val summary = service.buildSummary(longText, longText)
        assertTrue(summary.length <= 400)
    }
}
