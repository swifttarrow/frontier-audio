package com.jarvis.gateway.infotools

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class InfotoolsTest {

    @Test
    fun normalizeStooqSymbol_appendsUsForPlainTicker() {
        assertEquals("spy.us", normalizeStooqSymbol("SPY"))
        assertEquals("aapl.us", normalizeStooqSymbol("aapl"))
    }

    @Test
    fun normalizeStooqSymbol_preservesExchangeSuffix() {
        assertEquals("vod.uk", normalizeStooqSymbol("VOD.UK"))
    }

    @Test
    fun wmoWeatherDescription_clearSky() {
        assertEquals("Clear sky", wmoWeatherDescription(0))
    }

    @Test
    fun deviceLocationReport_missingCoords_returnsUnavailable() = runBlocking {
        val http = HttpClient(CIO)
        val svc = InfotoolsService(http, null)
        val json = svc.deviceLocationReport(null, null, null)
        assertContains(json, "location_unavailable")
        http.close()
    }
}
