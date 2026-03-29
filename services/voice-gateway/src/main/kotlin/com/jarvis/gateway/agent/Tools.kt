package com.jarvis.gateway.agent

import com.jarvis.gateway.github.GitHubClient
import com.jarvis.gateway.github.GitHubIdentifiers
import com.jarvis.gateway.github.GitHubPayload
import com.jarvis.gateway.github.HttpGitHubClient
import com.jarvis.gateway.github.listPublicReposForUser
import com.jarvis.gateway.infotools.InfotoolsService
import com.jarvis.gateway.operational.OperationalApiAdapter
import com.jarvis.gateway.operational.OperationalResult
import com.jarvis.gateway.ws.SessionManager
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tool definitions for the LLM orchestrator.
 * Each tool wraps a client call and returns a structured JSON string
 * that the LLM can cite in its answer.
 */
class ToolRegistry(
    private val httpClient: HttpClient,
    private val githubToken: String?,
    private val cacheTtlSeconds: Long,
    private val sessionManager: SessionManager,
    private val operationalAdapter: OperationalApiAdapter,
    private val tavilyApiKey: String? = null
) {
    private val mapper = jacksonObjectMapper()
    private val repoClientCache = ConcurrentHashMap<String, GitHubClient>()
    private val infotools = InfotoolsService(httpClient, tavilyApiKey)

    /** All available tool definitions for the LLM. */
    val toolDefinitions: List<ToolDef> = listOf(
        ToolDef(
            name = "github_list_public_repos",
            description = "List the most recently updated public repositories for a GitHub user login. " +
                "Use after the user names a GitHub handle (e.g. after they answer who to analyze). " +
                "Default limit is 3 for voice-friendly listing.",
            parameters = mapOf(
                "github_username" to "string, required — GitHub login without @; contiguous (e.g. swifttarrow), never letter-by-letter hyphens (not s-w-i-f-t-...)",
                "limit" to "integer, optional, default 3, max 30"
            )
        ),
        ToolDef(
            name = "github_set_active_repository",
            description = "Select which public GitHub repository this conversation uses for PR/issue tools. " +
                "Call after the user picks a repo (by name or from a short list you offered).",
            parameters = mapOf(
                "owner" to "string, required — repo owner login or org name",
                "repo" to "string, required — repository name",
                "full_name" to "string, optional — alternative to owner+repo, format owner/repo"
            )
        ),
        ToolDef(
            name = "github_list_open_prs",
            description = "List open pull requests in the active GitHub repository for this session",
            parameters = mapOf("limit" to "integer, optional, default 20")
        ),
        ToolDef(
            name = "github_list_open_issues",
            description = "List open issues in the active GitHub repository for this session",
            parameters = mapOf("limit" to "integer, optional, default 20")
        ),
        ToolDef(
            name = "github_recent_merged_prs",
            description = "List recently merged pull requests in the active GitHub repository for this session",
            parameters = mapOf("limit" to "integer, optional, default 20")
        ),
        ToolDef(
            name = "github_issue_comments",
            description = "Get comments on a specific issue or PR by number in the active repository",
            parameters = mapOf("issue_number" to "integer, required")
        ),
        ToolDef(
            name = "operational_health",
            description = "Get current operational health summary",
            parameters = emptyMap()
        ),
        ToolDef(
            name = "operational_alerts",
            description = "Get recent operational alerts or events",
            parameters = mapOf("limit" to "integer, optional")
        ),
        ToolDef(
            name = "device_location",
            description = "The user's current device location for this voice session: optional human-readable place or address " +
                "(reverse geocoding / client label) plus WGS84 coordinates for your internal use. " +
                "Call when the user asks where they are, what their coordinates are, whether you know their location, " +
                "or before weather_current for 'here' if you need to confirm a fix exists. " +
                "When answering aloud, give place or address — not raw lat/long — unless they explicitly ask for coordinates. " +
                "Do not invent location — only report what this tool returns.",
            parameters = emptyMap()
        ),
        ToolDef(
            name = "weather_current",
            description = "Current weather (temperature, conditions, wind) for a place or for the user's device location. " +
                "Use when the user asks about weather. If they name a city or region, pass it as location_query. " +
                "If they say 'here' or do not specify a place, omit location_query so the tool uses coordinates " +
                "from the app session when available. If unsure whether the session has coordinates, call device_location first.",
            parameters = mapOf(
                "location_query" to "string, optional — e.g. Austin, Paris France; omit for device location"
            )
        ),
        ToolDef(
            name = "stock_quote",
            description = "Latest available stock or ETF quote (delayed, not real-time). " +
                "Call this whenever the user asks for a stock price, share price, or how a public company is trading. " +
                "Map common names to tickers (e.g. Google or Alphabet → GOOGL or GOOG, Apple → AAPL, Microsoft → MSFT). " +
                "US listings need no exchange suffix; other markets may use forms like VOD.UK.",
            parameters = mapOf("symbol" to "string, required — ticker symbol")
        ),
        ToolDef(
            name = "web_search",
            description = "Search the public web for time-sensitive or local facts (store hours, nearby businesses, news). " +
                "Use for questions that need live web data. Summarize results in natural speech; do not invent URLs.",
            parameters = mapOf("query" to "string, required — concise search query in English")
        )
    )

    /** Execute a tool by name with the given arguments. Returns a JSON string result. */
    suspend fun executeTool(name: String, args: Map<String, Any?>, sessionId: UUID): ToolResult {
        return when (name) {
            "github_list_public_repos" -> {
                val trimmed = (args["github_username"] as? String)?.trim()?.removePrefix("@")
                if (trimmed.isNullOrBlank()) return noRepoToolResult("github_username is required")
                val rawLogin = GitHubIdentifiers.normalizeVoiceSpelledLogin(trimmed)
                if (!GitHubIdentifiers.isValidLogin(rawLogin)) {
                    return ToolResult(
                        data = """{"error":"invalid_github_username","message":"That does not look like a valid GitHub username."}""",
                        asOf = Instant.now()
                    )
                }
                val limit = (args["limit"] as? Number)?.toInt() ?: 3
                val payload = listPublicReposForUser(httpClient, rawLogin, limit, githubToken)
                payload.toToolResult()
            }
            "github_set_active_repository" -> {
                val fullName = (args["full_name"] as? String)?.trim()
                val (owner, repo) = if (!fullName.isNullOrBlank()) {
                    val parts = fullName.split("/", limit = 2)
                    if (parts.size != 2) {
                        return ToolResult(
                            data = """{"error":"invalid_full_name","message":"full_name must be owner/repo"}""",
                            asOf = Instant.now()
                        )
                    }
                    GitHubIdentifiers.normalizeVoiceSpelledLogin(parts[0].trim()) to parts[1].trim()
                } else {
                    val o = GitHubIdentifiers.normalizeVoiceSpelledLogin((args["owner"] as? String)?.trim() ?: "")
                    val r = (args["repo"] as? String)?.trim() ?: ""
                    o to r
                }
                if (!GitHubIdentifiers.isValidOwnerOrRepo(owner) || !GitHubIdentifiers.isValidOwnerOrRepo(repo)) {
                    return ToolResult(
                        data = """{"error":"invalid_owner_or_repo","message":"owner and repo must be valid GitHub path segments."}""",
                        asOf = Instant.now()
                    )
                }
                val active = sessionManager.getActive(sessionId)
                    ?: return ToolResult(
                        data = """{"error":"session_not_found"}""",
                        asOf = Instant.now()
                    )
                active.githubOwner = owner
                active.githubRepo = repo
                active.repoDisplayName = "$owner/$repo"
                ToolResult(
                    data = """{"ok":true,"active_repository":"$owner/$repo"}""",
                    asOf = Instant.now()
                )
            }
            "github_list_open_prs" -> {
                val limit = (args["limit"] as? Number)?.toInt() ?: 20
                val client = githubClientForSession(sessionId) ?: return noRepositorySelected()
                val payload = client.listOpenPullRequests(limit)
                payload.toToolResult()
            }
            "github_list_open_issues" -> {
                val limit = (args["limit"] as? Number)?.toInt() ?: 20
                val client = githubClientForSession(sessionId) ?: return noRepositorySelected()
                val payload = client.listOpenIssues(limit)
                payload.toToolResult()
            }
            "github_recent_merged_prs" -> {
                val limit = (args["limit"] as? Number)?.toInt() ?: 20
                val client = githubClientForSession(sessionId) ?: return noRepositorySelected()
                val payload = client.recentMergedPullRequests(limit)
                payload.toToolResult()
            }
            "github_issue_comments" -> {
                val issueNumber = (args["issue_number"] as? Number)?.toInt()
                    ?: throw IllegalArgumentException("issue_number is required")
                val client = githubClientForSession(sessionId) ?: return noRepositorySelected()
                val payload = client.getIssueComments(issueNumber)
                payload.toToolResult()
            }
            "operational_health" -> {
                val result = operationalAdapter.healthSummary()
                result.toToolResult()
            }
            "operational_alerts" -> {
                val limit = (args["limit"] as? Number)?.toInt()
                val result = operationalAdapter.alertsOrEvents(limit)
                result.toToolResult()
            }
            "device_location" -> {
                val session = sessionManager.getActive(sessionId)
                val data = infotools.deviceLocationReport(
                    sessionLat = session?.clientLatitude,
                    sessionLon = session?.clientLongitude,
                    sessionLocationLabel = session?.clientLocationLabel
                )
                ToolResult(data = data, asOf = Instant.now())
            }
            "weather_current" -> {
                val session = sessionManager.getActive(sessionId)
                val locQuery = args["location_query"] as? String
                val data = infotools.weatherCurrent(
                    locationQuery = locQuery,
                    sessionLat = session?.clientLatitude,
                    sessionLon = session?.clientLongitude,
                    sessionLocationLabel = session?.clientLocationLabel
                )
                ToolResult(data = data, asOf = Instant.now())
            }
            "stock_quote" -> {
                val symbol = (args["symbol"] as? String)?.trim()
                    ?: return noRepoToolResult("symbol is required")
                val data = infotools.stockQuote(symbol)
                ToolResult(data = data, asOf = Instant.now())
            }
            "web_search" -> {
                val query = (args["query"] as? String)?.trim()
                    ?: return noRepoToolResult("query is required")
                val data = infotools.webSearch(query)
                ToolResult(data = data, asOf = Instant.now())
            }
            else -> ToolResult(
                data = """{"error": "Unknown tool: $name"}""",
                asOf = Instant.now()
            )
        }
    }

    private fun githubClientForSession(sessionId: UUID): GitHubClient? {
        val s = sessionManager.getActive(sessionId) ?: return null
        val o = s.githubOwner ?: return null
        val r = s.githubRepo ?: return null
        val key = "$o/$r"
        return repoClientCache.computeIfAbsent(key) {
            HttpGitHubClient(httpClient, o, r, githubToken, cacheTtlSeconds)
        }
    }

    private fun noRepositorySelected() = ToolResult(
        data = """{"error":"no_repository_selected","message":"No GitHub repository is selected yet. Ask which GitHub user to analyze, use github_list_public_repos with their login, then github_set_active_repository (or ask them to name owner/repo) before listing PRs or issues."}""",
        asOf = Instant.now()
    )

    private fun noRepoToolResult(message: String) = ToolResult(
        data = mapper.writeValueAsString(mapOf("error" to "invalid_arguments", "message" to message)),
        asOf = Instant.now()
    )

    private fun GitHubPayload.toToolResult() = ToolResult(
        data = mapper.writeValueAsString(items),
        asOf = asOf
    )

    private fun <T> OperationalResult<T>.toToolResult() = ToolResult(
        data = mapper.writeValueAsString(data),
        asOf = asOf
    )
}

data class ToolDef(
    val name: String,
    val description: String,
    val parameters: Map<String, String>
)

data class ToolResult(
    val data: String,
    val asOf: Instant
)
