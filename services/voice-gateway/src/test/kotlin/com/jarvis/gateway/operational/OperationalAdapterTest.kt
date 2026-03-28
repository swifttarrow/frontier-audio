package com.jarvis.gateway.operational

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OperationalAdapterTest {

    @Test
    fun `normal mode returns health data with asOf`() = runBlocking {
        val adapter = FakeOperationalAdapter("normal")
        val result = adapter.healthSummary()
        assertEquals("healthy", result.data["status"])
        assertEquals("fake", result.source)
        assertTrue(result.asOf.epochSecond > 0)
    }

    @Test
    fun `normal mode returns alerts`() = runBlocking {
        val adapter = FakeOperationalAdapter("normal")
        val result = adapter.alertsOrEvents()
        assertTrue(result.data.isNotEmpty())
    }

    @Test
    fun `empty mode returns empty data`() = runBlocking {
        val adapter = FakeOperationalAdapter("empty")
        val health = adapter.healthSummary()
        assertTrue(health.data.isEmpty())
        val alerts = adapter.alertsOrEvents()
        assertTrue(alerts.data.isEmpty())
    }

    @Test
    fun `error mode throws OperationalApiException`() = runBlocking {
        val adapter = FakeOperationalAdapter("error")
        assertFailsWith<OperationalApiException> {
            adapter.healthSummary()
        }
    }

    @Test
    fun `stale mode returns old asOf`() = runBlocking {
        val adapter = FakeOperationalAdapter("stale")
        val result = adapter.healthSummary()
        val minutesAgo = java.time.Duration.between(result.asOf, java.time.Instant.now()).toMinutes()
        assertTrue(minutesAgo >= 9) // 10 min minus small tolerance
    }
}
