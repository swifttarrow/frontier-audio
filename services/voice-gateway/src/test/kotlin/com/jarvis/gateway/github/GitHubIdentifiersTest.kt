package com.jarvis.gateway.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubIdentifiersTest {

    @Test
    fun validLogins() {
        assertTrue(GitHubIdentifiers.isValidLogin("swifttarrow"))
        assertTrue(GitHubIdentifiers.isValidLogin("a"))
        assertTrue(GitHubIdentifiers.isValidLogin("user-name"))
        assertTrue(GitHubIdentifiers.isValidLogin("user_name"))
    }

    @Test
    fun invalidLogins() {
        assertFalse(GitHubIdentifiers.isValidLogin(""))
        assertFalse(GitHubIdentifiers.isValidLogin("-bad"))
        assertFalse(GitHubIdentifiers.isValidLogin("bad-"))
        assertFalse(GitHubIdentifiers.isValidLogin("has/slash"))
        assertFalse(GitHubIdentifiers.isValidLogin("a".repeat(40)))
    }

    @Test
    fun normalizeVoiceSpelledLogin_collapsesLetterByLetterHyphens() {
        assertEquals(
            "swifttarrow",
            GitHubIdentifiers.normalizeVoiceSpelledLogin("s-w-i-f-t-t-a-r-r-o-w")
        )
    }

    @Test
    fun normalizeVoiceSpelledLogin_leavesNormalHandles() {
        assertEquals("user-name", GitHubIdentifiers.normalizeVoiceSpelledLogin("user-name"))
        assertEquals("a-b-c", GitHubIdentifiers.normalizeVoiceSpelledLogin("a-b-c"))
    }
}
