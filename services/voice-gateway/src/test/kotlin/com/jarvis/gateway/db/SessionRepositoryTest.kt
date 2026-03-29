package com.jarvis.gateway.db

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.UUID

class SessionRepositoryTest {

    private val repo = SessionRepository()

    @BeforeTest
    fun setup() {
        DatabaseFactory.initForTest()
    }

    @Test
    fun `createSession persists and returns session`() {
        val session = repo.createSession("device-1", "hash-abc")
        assertNotNull(session.id)
        assertEquals("device-1", session.deviceId)
    }

    @Test
    fun `insertTurn and findTurnByClientTurnId round-trip`() {
        val session = repo.createSession("device-2", "hash-xyz")
        val turnId = UUID.randomUUID()
        val turn = repo.insertTurn(session.id, turnId, "user", "Hello world")
        assertEquals("Hello world", turn.text)

        val found = repo.findTurnByClientTurnId(session.id, turnId, "user")
        assertNotNull(found)
        assertEquals("Hello world", found.text)
    }

    @Test
    fun `findTurnByClientTurnId returns null for missing turn`() {
        val session = repo.createSession("device-3", "hash-000")
        val result = repo.findTurnByClientTurnId(session.id, UUID.randomUUID(), "user")
        assertNull(result)
    }

    @Test
    fun `same clientTurnId different roles are separate`() {
        val session = repo.createSession("device-4", "hash-111")
        val turnId = UUID.randomUUID()
        repo.insertTurn(session.id, turnId, "user", "question")
        repo.insertTurn(session.id, turnId, "assistant", "answer")

        val userTurn = repo.findTurnByClientTurnId(session.id, turnId, "user")
        val assistantTurn = repo.findTurnByClientTurnId(session.id, turnId, "assistant")
        assertNotNull(userTurn)
        assertNotNull(assistantTurn)
        assertEquals("question", userTurn.text)
        assertEquals("answer", assistantTurn.text)
    }

    @Test
    fun `recentTurns returns oldest first capped by limit`() {
        val session = repo.createSession("device-5", "hash-recent")
        val t1 = UUID.randomUUID()
        val t2 = UUID.randomUUID()
        val t3 = UUID.randomUUID()
        repo.insertTurn(session.id, t1, "user", "a")
        repo.insertTurn(session.id, t1, "assistant", "b")
        repo.insertTurn(session.id, t2, "user", "c")
        repo.insertTurn(session.id, t2, "assistant", "d")
        repo.insertTurn(session.id, t3, "user", "e")
        repo.insertTurn(session.id, t3, "assistant", "f")

        val last4 = repo.recentTurns(session.id, limit = 4)
        assertEquals(4, last4.size)
        assertEquals(listOf("c", "d", "e", "f"), last4.map { it.text })

        val all6 = repo.recentTurns(session.id, limit = 20)
        assertEquals(6, all6.size)
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), all6.map { it.text })
    }
}
