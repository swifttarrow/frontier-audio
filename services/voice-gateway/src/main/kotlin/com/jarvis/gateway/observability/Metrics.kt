package com.jarvis.gateway.observability

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple in-process metrics registry.
 * Exposes counters and histograms as a /metrics endpoint (Prometheus text format).
 * For MVP; upgrade to Micrometer/Prometheus client library in production.
 */
object Metrics {

    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val histograms = ConcurrentHashMap<String, MutableList<Double>>()

    fun incrementCounter(name: String, labels: Map<String, String> = emptyMap()) {
        val key = formatKey(name, labels)
        counters.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }

    fun recordHistogram(name: String, value: Double, labels: Map<String, String> = emptyMap()) {
        val key = formatKey(name, labels)
        histograms.computeIfAbsent(key) { mutableListOf() }.add(value)
    }

    fun sttLatency(seconds: Double) = recordHistogram("jarvis_stt_seconds", seconds)
    fun ttsTtfb(seconds: Double) = recordHistogram("jarvis_tts_ttfb_seconds", seconds)
    fun toolError(code: String) = incrementCounter("jarvis_tool_errors_total", mapOf("code" to code))
    fun interrupt() = incrementCounter("jarvis_interrupts_total")
    fun wsActive(delta: Int) {
        val key = "jarvis_ws_active"
        counters.computeIfAbsent(key) { AtomicLong(0) }.addAndGet(delta.toLong())
    }

    /** Render Prometheus text exposition format. */
    fun render(): String {
        val sb = StringBuilder()
        for ((key, value) in counters) {
            sb.appendLine("$key ${value.get()}")
        }
        for ((key, values) in histograms) {
            if (values.isNotEmpty()) {
                val sum = values.sum()
                val count = values.size
                sb.appendLine("${key}_sum $sum")
                sb.appendLine("${key}_count $count")
            }
        }
        return sb.toString()
    }

    private fun formatKey(name: String, labels: Map<String, String>): String {
        if (labels.isEmpty()) return name
        val labelStr = labels.entries.joinToString(",") { "${it.key}=\"${it.value}\"" }
        return "$name{$labelStr}"
    }
}
