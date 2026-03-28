package com.jarvis.gateway.agent

import com.jarvis.gateway.github.GitHubClient
import com.jarvis.gateway.github.GitHubPayload
import com.jarvis.gateway.operational.OperationalApiAdapter
import com.jarvis.gateway.operational.OperationalResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

/**
 * Tool definitions for the LLM orchestrator.
 * Each tool wraps a client call and returns a structured JSON string
 * that the LLM can cite in its answer.
 */
class ToolRegistry(
    private val gitHubClient: GitHubClient,
    private val operationalAdapter: OperationalApiAdapter
) {
    private val mapper = jacksonObjectMapper()

    /** All available tool definitions for the LLM. */
    val toolDefinitions: List<ToolDef> = listOf(
        ToolDef(
            name = "github_list_open_prs",
            description = "List open pull requests in the configured GitHub repository",
            parameters = mapOf("limit" to "integer, optional, default 20")
        ),
        ToolDef(
            name = "github_list_open_issues",
            description = "List open issues in the configured GitHub repository",
            parameters = mapOf("limit" to "integer, optional, default 20")
        ),
        ToolDef(
            name = "github_recent_merged_prs",
            description = "List recently merged pull requests in the configured GitHub repository",
            parameters = mapOf("limit" to "integer, optional, default 20")
        ),
        ToolDef(
            name = "github_issue_comments",
            description = "Get comments on a specific issue or PR by number",
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
        )
    )

    /** Execute a tool by name with the given arguments. Returns a JSON string result. */
    suspend fun executeTool(name: String, args: Map<String, Any?>): ToolResult {
        return when (name) {
            "github_list_open_prs" -> {
                val limit = (args["limit"] as? Number)?.toInt() ?: 20
                val payload = gitHubClient.listOpenPullRequests(limit)
                payload.toToolResult()
            }
            "github_list_open_issues" -> {
                val limit = (args["limit"] as? Number)?.toInt() ?: 20
                val payload = gitHubClient.listOpenIssues(limit)
                payload.toToolResult()
            }
            "github_recent_merged_prs" -> {
                val limit = (args["limit"] as? Number)?.toInt() ?: 20
                val payload = gitHubClient.recentMergedPullRequests(limit)
                payload.toToolResult()
            }
            "github_issue_comments" -> {
                val issueNumber = (args["issue_number"] as? Number)?.toInt()
                    ?: throw IllegalArgumentException("issue_number is required")
                val payload = gitHubClient.getIssueComments(issueNumber)
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
            else -> ToolResult(
                data = """{"error": "Unknown tool: $name"}""",
                asOf = Instant.now()
            )
        }
    }

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
