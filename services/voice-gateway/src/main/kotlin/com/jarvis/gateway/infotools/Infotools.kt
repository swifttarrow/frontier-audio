package com.jarvis.gateway.infotools

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.time.Instant

private val logger = LoggerFactory.getLogger("Infotools")
private val mapper = jacksonObjectMapper()

/** Nominatim requires a valid User-Agent per https://operations.osmfoundation.org/policies/nominatim/ */
private const val NOMINATIM_USER_AGENT = "frontier-audio-jarvis-voice-gateway/0.1 (voice assistant)"

/** Normalize user ticker (e.g. SPY) to Stooq symbol (spy.us). */
internal fun normalizeStooqSymbol(raw: String): String {
    val s = raw.trim().lowercase()
    if (s.isEmpty()) return s
    if ('.' in s) return s
    if (s.all { it.isLetterOrDigit() }) return "$s.us"
    return s
}

/** WMO Weather interpretation — Open-Meteo uses WMO Code table. */
internal fun wmoWeatherDescription(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2, 3 -> "Mainly clear, partly cloudy, or overcast"
    45, 48 -> "Fog or depositing rime fog"
    51, 53, 55 -> "Drizzle (light, moderate, dense)"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain (slight, moderate, heavy)"
    66, 67 -> "Freezing rain"
    71, 73, 75 -> "Snow fall (slight, moderate, heavy)"
    77 -> "Snow grains"
    80, 81, 82 -> "Rain showers (slight, moderate, violent)"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Weather code $code"
}

class InfotoolsService(
    private val httpClient: HttpClient,
    private val tavilyApiKey: String?
) {

    /**
     * Reports coordinates last sent by the app in `session.start` (WGS84).
     * Optionally enriches with reverse geocoding (OpenStreetMap Nominatim) when the network allows.
     */
    suspend fun deviceLocationReport(
        sessionLat: Double?,
        sessionLon: Double?,
        sessionLocationLabel: String?
    ): String {
        if (sessionLat == null || sessionLon == null) {
            return mapper.writeValueAsString(
                mapOf(
                    "error" to "location_unavailable",
                    "message" to "This session has no device coordinates. The app includes latitude and longitude in " +
                        "session.start only when location permission is granted and a last-known fix exists. " +
                        "Suggest the user enable location for Jarvis, or they can name a city for weather and other local answers."
                )
            )
        }

        var placeDescription: String? = null
        try {
            val response = httpClient.get("https://nominatim.openstreetmap.org/reverse") {
                parameter("lat", sessionLat)
                parameter("lon", sessionLon)
                parameter("format", "json")
                header(HttpHeaders.UserAgent, NOMINATIM_USER_AGENT)
            }
            if (response.status == HttpStatusCode.OK) {
                val root = mapper.readTree(response.bodyAsText())
                placeDescription = root.get("display_name")?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                logger.warn("Nominatim reverse failed: {}", response.status)
            }
        } catch (e: Exception) {
            logger.warn("Nominatim reverse geocode error: {}", e.message)
        }

        val payload = linkedMapOf<String, Any>(
            "source" to "device_session",
            "latitude" to sessionLat,
            "longitude" to sessionLon,
            "fetched_at" to Instant.now().toString()
        )
        sessionLocationLabel?.trim()?.takeIf { it.isNotEmpty() }?.let {
            payload["client_location_label"] = it
        }
        placeDescription?.let { payload["place_description"] = it }
        return mapper.writeValueAsString(payload)
    }

    suspend fun weatherCurrent(
        locationQuery: String?,
        sessionLat: Double?,
        sessionLon: Double?,
        sessionLocationLabel: String?
    ): String {
        val query = locationQuery?.trim()?.takeIf { it.isNotEmpty() }
        val (lat, lon, label, timezone) = if (query != null) {
            val geo = geocode(query) ?: return mapper.writeValueAsString(
                mapOf(
                    "error" to "geocode_not_found",
                    "message" to "Could not resolve that place name. Try a nearby city or add region/country."
                )
            )
            ResolvedPlace(
                geo.latitude,
                geo.longitude,
                listOfNotNull(geo.name, geo.countryCode).joinToString(", "),
                geo.timezone
            )
        } else if (sessionLat != null && sessionLon != null) {
            ResolvedPlace(sessionLat, sessionLon, sessionLocationLabel ?: "device location", null)
        } else {
            return mapper.writeValueAsString(
                mapOf(
                    "error" to "location_required",
                    "message" to "No place was given and the app has not shared device location. " +
                        "Ask the user which city or area they want, or they can enable location for this app."
                )
            )
        }

        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?")
            append("latitude=").append(lat)
            append("&longitude=").append(lon)
            append("&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,apparent_temperature")
            append("&wind_speed_unit=kmh")
            if (timezone != null) {
                append("&timezone=").append(java.net.URLEncoder.encode(timezone, Charsets.UTF_8))
            } else {
                append("&timezone=auto")
            }
        }

        val response = httpClient.get(url)
        if (response.status != HttpStatusCode.OK) {
            logger.warn("Open-Meteo forecast failed: {}", response.status)
            return mapper.writeValueAsString(
                mapOf("error" to "weather_fetch_failed", "message" to "Weather service returned an error.")
            )
        }

        val root = mapper.readTree(response.bodyAsText())
        val current = root.get("current") ?: return mapper.writeValueAsString(
            mapOf("error" to "weather_parse_failed", "message" to "Unexpected weather response.")
        )
        val code = current.get("weather_code")?.asInt() ?: -1
        val payload = mapOf(
            "location" to label,
            "latitude" to lat,
            "longitude" to lon,
            "temperature_c" to current.get("temperature_2m")?.asDouble(),
            "feels_like_c" to current.get("apparent_temperature")?.asDouble(),
            "relative_humidity_percent" to current.get("relative_humidity_2m")?.asInt(),
            "wind_speed_kmh" to current.get("wind_speed_10m")?.asDouble(),
            "conditions" to wmoWeatherDescription(code),
            "observation_time" to (current.get("time")?.asText() ?: ""),
            "timezone_note" to (root.get("timezone")?.asText() ?: ""),
            "fetched_at" to Instant.now().toString()
        )
        return mapper.writeValueAsString(payload)
    }

    suspend fun stockQuote(symbolArg: String): String {
        val raw = symbolArg.trim().uppercase()
        if (raw.isEmpty()) {
            return mapper.writeValueAsString(mapOf("error" to "symbol_required", "message" to "Ticker symbol is required."))
        }
        if (!raw.all { it.isLetterOrDigit() || it == '.' }) {
            return mapper.writeValueAsString(
                mapOf("error" to "invalid_symbol", "message" to "Use letters, numbers, or exchange suffix like VOD.UK.")
            )
        }
        val stooqSymbol = normalizeStooqSymbol(raw)
        val url = "https://stooq.com/q/l/?s=$stooqSymbol&f=sd2t2ohlcv&h&e=csv"
        val response = httpClient.get(url)
        if (response.status != HttpStatusCode.OK) {
            return mapper.writeValueAsString(
                mapOf("error" to "quote_fetch_failed", "message" to "Could not reach quote service.")
            )
        }
        val text = response.bodyAsText().trim()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) {
            return mapper.writeValueAsString(
                mapOf("error" to "quote_not_found", "message" to "No quote for that symbol. Try a US ticker like SPY or AAPL, or an exchange suffix.")
            )
        }
        val header = lines[0].split(",")
        val values = lines[1].split(",")
        val row = header.zip(values).toMap()
        val close = row["Close"]?.trim()
        if (close.isNullOrBlank() || close == "N/D") {
            return mapper.writeValueAsString(
                mapOf("error" to "quote_not_found", "message" to "No price data for that symbol.")
            )
        }
        return mapper.writeValueAsString(
            mapOf(
                "symbol" to raw,
                "stooq_symbol" to stooqSymbol,
                "date" to (row["Date"] ?: ""),
                "time_utc" to (row["Time"] ?: ""),
                "open" to row["Open"],
                "high" to row["High"],
                "low" to row["Low"],
                "close" to close,
                "volume" to row["Volume"],
                "fetched_at" to Instant.now().toString()
            )
        )
    }

    suspend fun webSearch(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) {
            return mapper.writeValueAsString(mapOf("error" to "query_required", "message" to "Search query is required."))
        }
        val key = tavilyApiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: return mapper.writeValueAsString(
                mapOf(
                    "error" to "web_search_not_configured",
                    "message" to "Web search is not configured on the server (set TAVILY_API_KEY). " +
                        "You cannot answer live web questions until the operator adds a Tavily API key."
                )
            )

        val body = mapper.writeValueAsString(
            mapOf(
                "api_key" to key,
                "query" to q,
                "search_depth" to "basic",
                "max_results" to 8,
                "include_answer" to true
            )
        )
        val response = httpClient.post("https://api.tavily.com/search") {
            header(HttpHeaders.ContentType, "application/json")
            setBody(body)
        }
        if (response.status != HttpStatusCode.OK) {
            logger.warn("Tavily search failed: {} {}", response.status, response.bodyAsText().take(200))
            return mapper.writeValueAsString(
                mapOf("error" to "web_search_failed", "message" to "Search service returned an error.")
            )
        }
        val root = mapper.readTree(response.bodyAsText())
        val answer = root.get("answer")?.asText()
        val results = root.get("results")
        val snippets = mutableListOf<Map<String, String?>>()
        if (results != null && results.isArray) {
            for (node in results) {
                snippets.add(
                    mapOf(
                        "title" to node.get("title")?.asText(),
                        "url" to node.get("url")?.asText(),
                        "content" to node.get("content")?.asText()
                    )
                )
            }
        }
        return mapper.writeValueAsString(
            mapOf(
                "answer" to answer,
                "results" to snippets,
                "fetched_at" to Instant.now().toString()
            )
        )
    }

    private suspend fun geocode(name: String): GeocodeHit? {
        val enc = java.net.URLEncoder.encode(name, Charsets.UTF_8)
        val response = httpClient.get("https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=1")
        if (response.status != HttpStatusCode.OK) return null
        val results = mapper.readTree(response.bodyAsText()).get("results") ?: return null
        if (!results.isArray || results.size() == 0) return null
        return parseGeocodeHit(results[0])
    }

    private fun parseGeocodeHit(node: JsonNode): GeocodeHit {
        val country = node.get("country_code")?.asText()
        val nm = node.get("name")?.asText() ?: "Unknown"
        return GeocodeHit(
            name = nm,
            latitude = node.get("latitude")?.asDouble() ?: 0.0,
            longitude = node.get("longitude")?.asDouble() ?: 0.0,
            countryCode = country,
            timezone = node.get("timezone")?.asText()
        )
    }

    private data class GeocodeHit(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val countryCode: String?,
        val timezone: String?
    )

    private data class ResolvedPlace(val lat: Double, val lon: Double, val label: String, val timezone: String?)
}
