package com.jarvis.gateway.config

import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File

class EnvSupportTest {

    @Test
    fun `parseDotEnv strips CRLF quotes and comments`() {
        val f = File.createTempFile("jarvis-test", ".env")
        f.deleteOnExit()
        f.writeText(
            """
            # comment
            GITHUB_TOKEN=github_pat_abc
            OPENAI="sk-quoted"
            EMPTY=
            SPACE = ' x '
            """.trimIndent().replace("\n", "\r\n")
        )
        val m = EnvSupport.parseDotEnv(f)
        assertEquals("github_pat_abc", m["GITHUB_TOKEN"])
        assertEquals("sk-quoted", m["OPENAI"])
        assertEquals("", m.getValue("EMPTY"))
        assertEquals(" x ", m["SPACE"])
    }
}
