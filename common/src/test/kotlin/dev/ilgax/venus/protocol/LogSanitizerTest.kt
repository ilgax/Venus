package dev.ilgax.venus.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LogSanitizerTest {
    @Test
    fun `sanitize replaces newlines with escaped form`() {
        assertEquals("line1\\nline2", LogSanitizer.sanitize("line1\nline2"))
    }

    @Test
    fun `sanitize replaces carriage returns`() {
        assertEquals("line1\\rline2", LogSanitizer.sanitize("line1\rline2"))
    }

    @Test
    fun `sanitize replaces tabs`() {
        assertEquals("a\\tb", LogSanitizer.sanitize("a\tb"))
    }

    @Test
    fun `sanitize escapes backslashes`() {
        assertEquals("a\\\\b", LogSanitizer.sanitize("a\\b"))
    }

    @Test
    fun `sanitize strips control characters`() {
        val result = LogSanitizer.sanitize("a\u0000b\u0001c")
        assertFalse(result.contains("\u0000"))
        assertFalse(result.contains("\u0001"))
    }

    @Test
    fun `sanitize preserves normal text`() {
        assertEquals("hello world", LogSanitizer.sanitize("hello world"))
    }

    @Test
    fun `redactCommand returns only command name without args`() {
        assertEquals("say", LogSanitizer.redactCommand("say hello world"))
    }

    @Test
    fun `redactCommand returns full string when no args`() {
        assertEquals("stop", LogSanitizer.redactCommand("stop"))
    }

    @Test
    fun `redactCommand sanitizes newlines in command name`() {
        assertEquals("sa\\ny", LogSanitizer.redactCommand("sa\ny hello"))
    }
}
