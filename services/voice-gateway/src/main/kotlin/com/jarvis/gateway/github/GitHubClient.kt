package com.jarvis.gateway.github

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class GitHubPayload(
    val items: List<Map<String, Any?>>,
    val asOf: Instant,
    val etag: String? = null
)

class GitHubApiException(
    val code: String,
    override val message: String,
    val retryable: Boolean,
    val resetAt: Instant? = null
) : RuntimeException(message)

interface GitHubClient {
    suspend fun listOpenPullRequests(limit: Int = 20): GitHubPayload
    suspend fun listOpenIssues(limit: Int = 20): GitHubPayload
    suspend fun recentMergedPullRequests(limit: Int = 20): GitHubPayload
    suspend fun getIssueComments(issueNumber: Int): GitHubPayload
}

class HttpGitHubClient(
    private val httpClient: HttpClient,
    private val owner: String,
    private val repo: String,
    private val token: String? = null,
    private val cacheTtlSeconds: Long = 180
) : GitHubClient {

    private val logger = LoggerFactory.getLogger(HttpGitHubClient::class.java)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(val payload: GitHubPayload, val expiresAt: Instant)

    override suspend fun listOpenPullRequests(limit: Int): GitHubPayload {
        return cachedGet("pulls?state=open&per_page=$limit&sort=updated&direction=desc")
    }

    override suspend fun listOpenIssues(limit: Int): GitHubPayload {
        return cachedGet("issues?state=open&per_page=$limit&sort=updated&direction=desc")
    }

    override suspend fun recentMergedPullRequests(limit: Int): GitHubPayload {
        return cachedGet("pulls?state=closed&per_page=$limit&sort=updated&direction=desc")
    }

    override suspend fun getIssueComments(issueNumber: Int): GitHubPayload {
        return cachedGet("issues/$issueNumber/comments?per_page=50")
    }

    private suspend fun cachedGet(path: String): GitHubPayload {
        val cacheKey = "$owner/$repo/$path"
        val now = Instant.now()

        cache[cacheKey]?.let { entry ->
            if (entry.expiresAt.isAfter(now)) {
                logger.debug("Cache hit for {}", cacheKey)
                return entry.payload
            }
        }

        val url = "https://api.github.com/repos/$owner/$repo/$path"
        logger.info("Fetching GitHub: {}", url)

        val response = httpClient.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            header(HttpHeaders.UserAgent, "jarvis-voice-gateway/0.1")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.bodyAsText()
                val items = parseJsonArray(body)
                val etag = response.headers[HttpHeaders.ETag]
                val payload = GitHubPayload(items = items, asOf = now, etag = etag)
                cache[cacheKey] = CacheEntry(payload, now.plusSeconds(cacheTtlSeconds))
                return payload
            }
            HttpStatusCode.Forbidden, HttpStatusCode.TooManyRequests -> {
                val resetHeader = response.headers["X-RateLimit-Reset"]
                val resetAt = resetHeader?.toLongOrNull()?.let { Instant.ofEpochSecond(it) }
                throw GitHubApiException(
                    code = "github.rate_limited",
                    message = "GitHub rate limit exceeded. Please try again in a moment.",
                    retryable = true,
                    resetAt = resetAt
                )
            }
            HttpStatusCode.NotFound -> {
                throw GitHubApiException(
                    code = "github.not_found",
                    message = "Repository or resource not found.",
                    retryable = false
                )
            }
            else -> {
                throw GitHubApiException(
                    code = "github.error",
                    message = "GitHub API error: ${response.status}",
                    retryable = true
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonArray(body: String): List<Map<String, Any?>> {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        return try {
            mapper.readValue(body, List::class.java) as List<Map<String, Any?>>
        } catch (_: Exception) {
            // Single object response (e.g., for non-list endpoints)
            val obj = mapper.readValue(body, Map::class.java) as Map<String, Any?>
            listOf(obj)
        }
    }
}

/** Fake GitHub client for testing — returns deterministic fixture data. */
class FakeGitHubClient : GitHubClient {
    var openPRs: List<Map<String, Any?>> = listOf(
        mapOf("number" to 42, "title" to "Fix authentication bug", "state" to "open", "user" to mapOf("login" to "alice")),
        mapOf("number" to 43, "title" to "Add logging middleware", "state" to "open", "user" to mapOf("login" to "bob"))
    )
    var openIssues: List<Map<String, Any?>> = listOf(
        mapOf("number" to 10, "title" to "App crashes on startup", "state" to "open", "labels" to listOf(mapOf("name" to "bug")))
    )

    override suspend fun listOpenPullRequests(limit: Int) = GitHubPayload(openPRs.take(limit), Instant.now())
    override suspend fun listOpenIssues(limit: Int) = GitHubPayload(openIssues.take(limit), Instant.now())
    override suspend fun recentMergedPullRequests(limit: Int) = GitHubPayload(emptyList(), Instant.now())
    override suspend fun getIssueComments(issueNumber: Int) = GitHubPayload(emptyList(), Instant.now())
}
