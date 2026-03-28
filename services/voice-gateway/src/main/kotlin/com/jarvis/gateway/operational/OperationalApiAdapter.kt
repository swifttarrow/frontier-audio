package com.jarvis.gateway.operational

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import java.time.Instant

data class OperationalResult<T>(
    val data: T,
    val asOf: Instant,
    val source: String // "cache" | "live" | "fake"
)

class OperationalApiException(
    val code: String,
    override val message: String,
    val retryable: Boolean,
    val httpStatus: Int? = null
) : RuntimeException(message)

interface OperationalApiAdapter {
    suspend fun healthSummary(): OperationalResult<Map<String, Any?>>
    suspend fun alertsOrEvents(limit: Int? = null): OperationalResult<List<Map<String, Any?>>>
}

class FakeOperationalAdapter(
    private val mode: String = System.getenv("OPERATIONAL_FAKE_MODE") ?: "normal"
) : OperationalApiAdapter {

    private val logger = LoggerFactory.getLogger(FakeOperationalAdapter::class.java)
    private val mapper = jacksonObjectMapper()

    override suspend fun healthSummary(): OperationalResult<Map<String, Any?>> {
        return when (mode) {
            "error" -> throw OperationalApiException("operational.unavailable", "Service unavailable", true)
            "empty" -> OperationalResult(emptyMap(), asOf(), "fake")
            "stale" -> {
                val data = loadFixture<Map<String, Any?>>("health.json")
                OperationalResult(data, Instant.now().minusSeconds(600), "fake") // 10 min ago
            }
            else -> {
                val data = loadFixture<Map<String, Any?>>("health.json")
                OperationalResult(data, asOf(), "fake")
            }
        }
    }

    override suspend fun alertsOrEvents(limit: Int?): OperationalResult<List<Map<String, Any?>>> {
        return when (mode) {
            "error" -> throw OperationalApiException("operational.unavailable", "Service unavailable", true)
            "empty" -> OperationalResult(emptyList(), asOf(), "fake")
            "stale" -> {
                val data = loadFixture<List<Map<String, Any?>>>("alerts.json")
                OperationalResult(data.take(limit ?: Int.MAX_VALUE), Instant.now().minusSeconds(600), "fake")
            }
            else -> {
                val data = loadFixture<List<Map<String, Any?>>>("alerts.json")
                OperationalResult(data.take(limit ?: Int.MAX_VALUE), asOf(), "fake")
            }
        }
    }

    private fun asOf(): Instant = Instant.now()

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> loadFixture(name: String): T {
        val stream = javaClass.classLoader.getResourceAsStream("fixtures/operational/$name")
            ?: throw RuntimeException("Fixture not found: fixtures/operational/$name")
        return mapper.readValue(stream)
    }
}
