package com.jarvis.gateway.errors

import com.jarvis.gateway.github.GitHubApiException
import com.jarvis.gateway.operational.OperationalApiException

data class UserFacingError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val speak: String
)

fun mapThrowableToUserFacing(t: Throwable): UserFacingError {
    return when (t) {
        is GitHubApiException -> UserFacingError(
            code = t.code,
            message = t.message ?: "GitHub error",
            retryable = t.retryable,
            speak = when (t.code) {
                "github.rate_limited" -> "GitHub rate limit reached. Please try again in a moment."
                "github.not_found" -> "I couldn't find that repository or resource on GitHub."
                else -> "There was a problem reaching GitHub. Please try again."
            }
        )
        is OperationalApiException -> UserFacingError(
            code = t.code,
            message = t.message ?: "Operational API error",
            retryable = t.retryable,
            speak = when (t.code) {
                "operational.unavailable" -> "Operational data is temporarily unavailable. Try again in a moment."
                "operational.forbidden" -> "Unable to access operational data."
                else -> "There was a problem fetching operational data."
            }
        )
        else -> UserFacingError(
            code = "internal.error",
            message = "Internal error",
            retryable = false,
            speak = "Something went wrong. Please try again."
        )
    }
}
