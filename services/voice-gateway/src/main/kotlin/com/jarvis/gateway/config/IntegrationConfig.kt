package com.jarvis.gateway.config

data class IntegrationConfig(
    /** When set, new voice sessions start with this public repo already selected. */
    val defaultPublicRepoUrl: String?,
    val repoDisplayName: String?,
    val operationalApiBaseUrl: String?
)

object IntegrationConfigProvider {

    /** Non-null only when the variable is set and not blank (sourced `.env` often sets `KEY=`). */
    private fun env(name: String): String? =
        EnvSupport.get(name)?.takeIf { it.isNotBlank() }

    fun load(): IntegrationConfig {
        val apiBaseUrl = env("OPERATIONAL_API_BASE_URL")
        val repoUrl = env("JARVIS_DEFAULT_GITHUB_REPO_URL")
        if (repoUrl == null) {
            return IntegrationConfig(
                defaultPublicRepoUrl = null,
                repoDisplayName = null,
                operationalApiBaseUrl = apiBaseUrl
            )
        }
        val validated = validateAndNormalize(repoUrl)
        val displayName = env("JARVIS_REPO_DISPLAY_NAME") ?: parseDisplayName(validated)
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
