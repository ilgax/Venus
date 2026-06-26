package dev.ilgax.venus.client.ui.component

import dev.ilgax.venus.client.ui.core.ToastKind
import dev.ilgax.venus.client.ui.core.VenusToastRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToastLifecycleTest {
    private fun toast(
        createdAt: Long,
        duration: Long,
    ) = VenusToastRequest(1, ToastKind.INFO, "Title", "Msg", createdAt, createdAt + duration)

    @Test
    fun `isExpired returns true after expiry`() {
        val t = toast(1000, 5000)
        assertTrue(ToastLifecycle.isExpired(t, 7000))
    }

    @Test
    fun `isExpired returns false before expiry`() {
        val t = toast(1000, 5000)
        assertFalse(ToastLifecycle.isExpired(t, 4000))
    }

    @Test
    fun `expiryMs sums createdAt and duration`() {
        assertEquals(6000, ToastLifecycle.expiryMs(1000, 5000))
    }

    @Test
    fun `sortStack orders newest first`() {
        val t1 = toast(1000, 5000)
        val t2 = toast(2000, 5000)
        val sorted = ToastLifecycle.sortStack(listOf(t1, t2))
        assertTrue(sorted.first().createdAtMs >= sorted.last().createdAtMs)
    }
}

class ConsoleLineParserTest {
    @Test
    fun `parses timestamp and level`() {
        val parsed = ConsoleLineParser.parse("[12:34:56] [INFO] Server started")
        assertEquals("12:34:56", parsed.timestamp)
        assertEquals("INFO", parsed.level)
        assertEquals("[INFO] Server started", parsed.text)
    }

    @Test
    fun `parses level without timestamp`() {
        val parsed = ConsoleLineParser.parse("[WARN] Low memory")
        assertEquals("", parsed.timestamp)
        assertEquals("WARN", parsed.level)
        assertEquals("Low memory", parsed.text)
    }

    @Test
    fun `returns raw text for unrecognized lines`() {
        val parsed = ConsoleLineParser.parse("some random output")
        assertEquals("", parsed.timestamp)
        assertEquals("", parsed.level)
        assertEquals("some random output", parsed.text)
    }

    @Test
    fun `handles ERROR and SEVERE levels`() {
        assertEquals("ERROR", ConsoleLineParser.parse("[ERROR] crash").level)
        assertEquals("SEVERE", ConsoleLineParser.parse("[SEVERE] crash").level)
    }

    @Test
    fun `handles DEBUG and TRACE`() {
        assertEquals("DEBUG", ConsoleLineParser.parse("[DEBUG] trace").level)
        assertEquals("TRACE", ConsoleLineParser.parse("[TRACE] trace").level)
    }

    @Test
    fun `extracts logger name from bracket`() {
        val parsed = ConsoleLineParser.parse("[12:34:56] [net.minecraft.server.MinecraftServer] Starting server")
        assertEquals("12:34:56", parsed.timestamp)
        assertEquals("net.minecraft.server.MinecraftServer", parsed.logger)
        assertEquals("Starting server", parsed.message)
    }

    @Test
    fun `extracts level from logger bracket with slash`() {
        val parsed = ConsoleLineParser.parse("[12:34:56] [Server thread/INFO] Starting server")
        assertEquals("12:34:56", parsed.timestamp)
        assertEquals("Server thread", parsed.logger)
        assertEquals("INFO", parsed.level)
        assertEquals("Starting server", parsed.message)
    }

    @Test
    fun `treats bracket containing only level as level not logger`() {
        val parsed = ConsoleLineParser.parse("[12:34:56] [INFO] Server started")
        assertEquals("12:34:56", parsed.timestamp)
        assertEquals("INFO", parsed.level)
        assertEquals("", parsed.logger)
        assertEquals("Server started", parsed.message)
    }

    @Test
    fun `simplifyLoggerName takes last segment`() {
        assertEquals("MinecraftServer", ConsoleLineParser.simplifyLoggerName("net.minecraft.server.MinecraftServer"))
        assertEquals("MyPlugin", ConsoleLineParser.simplifyLoggerName("com.example.MyPlugin"))
    }

    @Test
    fun `simplifyLoggerName keeps names without dots`() {
        assertEquals("Server thread", ConsoleLineParser.simplifyLoggerName("Server thread"))
        assertEquals("", ConsoleLineParser.simplifyLoggerName(""))
    }
}
