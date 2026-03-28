package com.jarvis.gateway.config

data class IntegrationConfig(
    val defaultPublicRepoUrl: String,
    val repoDisplayName: String,
    val operationalApiBaseUrl: String?
)

object IntegrationConfigProvider {

    fun load(): IntegrationConfig {
        val repoUrl = System.getenv("JARVIS_DEFAULT_GITHUB_REPO_URL")
            ?: "https://github.com/anthropics/claude-code" // dev default

        val validated = validateAndNormalize(repoUrl)
        val displayName = System.getenv("JARVIS_REPO_DISPLAY_NAME")
            ?: parseDisplayName(validated)
        val apiBaseUrl = System.getenv("OPERATIONAL_API_BASE_URL")

        return IntegrationConfig(
            defaultPublicRepoUrl = validated,
            repoDisplayName = displayName,
            operationalApiBaseUrl = apiBaseUrl
        )
    }

    internal fun validateAndNormalize(url: String): String {
        val normalized = url.trimEnd('/')
        val pattern = Regex("""^https://github\.com/[a-zA-Z0-9\-_.]+/[a-zA-Z0-9\-_.]+$""")
        require(pattern.matches(normalized)) {
            "Invalid GitHub repo URL: $url. Expected format: https://github.com/{owner}/{repo}"
        }
        return normalized
    }

    internal fun parseDisplayName(url: String): String {
        // https://github.com/owner/repo -> owner/repo
        return url.removePrefix("https://github.com/")
    }
}
