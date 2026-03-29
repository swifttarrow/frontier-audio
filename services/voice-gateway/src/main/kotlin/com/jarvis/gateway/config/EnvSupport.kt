package com.jarvis.gateway.config

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Resolves environment variables for local development.
 *
 * Order: [System.getenv] (trimmed) wins if set and non-blank; otherwise values from a `.env` file
 * found by walking up from [user.dir]. This matches how developers expect `.env` to work when
 * running [com.jarvis.gateway.main] from the IDE (where the process often starts under
 * `services/voice-gateway/` without shell-exported variables).
 */
object EnvSupport {

    private val dotEnv: Map<String, String> by lazy { loadDotEnvFromAncestors() }

    fun get(name: String): String? {
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return dotEnv[name]?.trim()?.takeIf { it.isNotEmpty() }
    }

    internal fun loadDotEnvFromAncestors(): Map<String, String> {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(12) {
            val f = File(dir, ".env")
            if (f.isFile) return parseDotEnv(f)
            dir = dir.parentFile ?: return emptyMap()
        }
        return emptyMap()
    }

    internal fun parseDotEnv(file: File): Map<String, String> {
        val text = file.readText(StandardCharsets.UTF_8)
        val out = linkedMapOf<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r', '\n').trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim()
            if (key.isEmpty()) return@forEach
            var value = line.substring(eq + 1).trim()
            if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                value = value.substring(1, value.length - 1)
            } else if (value.startsWith("'") && value.endsWith("'") && value.length >= 2) {
                value = value.substring(1, value.length - 1)
            }
            out[key] = value
        }
        return out
    }
}
