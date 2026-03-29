package com.jarvis.gateway.github

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.time.Instant

private val logger = LoggerFactory.getLogger("GitHubUserRepos")

/**
 * Lists public repositories for a GitHub user login (GET /users/{login}/repos).
 */
suspend fun listPublicReposForUser(
    httpClient: HttpClient,
    login: String,
    limit: Int,
    token: String?
): GitHubPayload {
    val perPage = limit.coerceIn(1, 30)
    val url = "https://api.github.com/users/$login/repos?per_page=$perPage&sort=updated&direction=desc"
    logger.info("Fetching GitHub user repos: {}", url)

    val response = httpClient.get(url) {
        header(HttpHeaders.Accept, "application/vnd.github.v3+json")
        header(HttpHeaders.UserAgent, "jarvis-voice-gateway/0.1")
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    val now = Instant.now()
    when (response.status) {
        HttpStatusCode.OK -> {
            val body = response.bodyAsText()
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            @Suppress("UNCHECKED_CAST")
            val raw = mapper.readValue(body, List::class.java) as List<Map<String, Any?>>
            val slim = raw.map { repo ->
                mapOf(
                    "full_name" to repo["full_name"],
                    "name" to repo["name"],
                    "description" to (repo["description"] as? String),
                    "updated_at" to repo["updated_at"]
                )
            }
            return GitHubPayload(items = slim, asOf = now, etag = response.headers[HttpHeaders.ETag])
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
                code = "github.user_not_found",
                message = "GitHub user not found.",
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
