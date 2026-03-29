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

    /**
     * Voice models often pass usernames spelled letter-by-letter as `s-w-i-f-t-t-a-r-r-o-w`.
     * GitHub logins are contiguous (e.g. `swifttarrow`). When every `-`-separated segment is a
     * single alphanumeric character and there are at least five segments, join them; otherwise
     * return [login] unchanged (preserves real handles like `user-name`).
     */
    fun normalizeVoiceSpelledLogin(login: String): String {
        val parts = login.split('-')
        if (parts.size < 5) return login
        if (parts.all { it.length == 1 && it.isNotEmpty() && it[0].isLetterOrDigit() }) {
            return parts.joinToString("")
        }
        return login
    }
}
