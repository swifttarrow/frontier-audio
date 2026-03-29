package com.jarvis.gateway.infotools

import kotlin.test.Test
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
}
