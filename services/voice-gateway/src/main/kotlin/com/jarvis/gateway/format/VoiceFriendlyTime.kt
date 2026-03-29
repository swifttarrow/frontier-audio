package com.jarvis.gateway.format

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Compact date/time strings for voice UX and LLM context (e.g. "3/29 @ 3 PM" UTC).
 * Prefer this over ISO-8601 in user-facing or spoken-adjacent payloads.
 */
object VoiceFriendlyTime {
    private val utcCompact: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M/d '@' h a", Locale.US).withZone(ZoneOffset.UTC)

    fun formatUtc(instant: Instant): String = utcCompact.format(instant)
}
