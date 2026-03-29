package com.jarvis.gateway.github

object GitHubIdentifiers {
    /** Owner or repo name segment as used in github.com URLs (aligned with IntegrationConfig). */
    private val ownerRepoSegment = Regex("""^[a-zA-Z0-9\-_.]+$""")

    fun isValidOwnerOrRepo(segment: String): Boolean =
        segment.isNotBlank() && ownerRepoSegment.matches(segment)

    /** GitHub login: ASCII alphanumeric and internal hyphens, length ≤ 39. */
    fun isValidLogin(login: String): Boolean {
        if (login.length !in 1..39) return false
        if (login.first() == '-' || login.last() == '-') return false
        return login.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
