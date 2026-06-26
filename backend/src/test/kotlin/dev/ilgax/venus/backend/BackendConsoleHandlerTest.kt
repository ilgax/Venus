package dev.ilgax.venus.backend

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class BackendConsoleHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `blank console command is ignored`() {
        val (platform, logger) = platformFixture()
        val handler = BackendConsoleHandler(platform, json)
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        handler.handle(player, """{"type":"console_cmd","command":"   "}""")

        verify { logger.warning(match { it.contains("blank console command") }) }
        verify(exactly = 0) { platform.executeCommand(any(), any(), any()) }
        verify(exactly = 0) { platform.sendData(any(), any()) }
    }

    @Test
    fun `valid command executes and sends response with output lines`() {
        val (platform, _) = platformFixture()
        val handler = BackendConsoleHandler(platform, json)
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        every { platform.executeCommand(player, "say hi", any()) } answers {
            val output = thirdArg<(String) -> Unit>()
            output("Line 1")
            output("Line 2")
            true
        }

        handler.handle(player, """{"type":"console_cmd","command":"say hi"}""")

        verify { platform.executeCommand(player, "say hi", any()) }
        verify { platform.sendData(player, match<String> { it.contains("Line 1") && it.contains("Line 2") }) }
    }

    @Test
    fun `undispatched command with no output appends Unknown command`() {
        val (platform, _) = platformFixture()
        val handler = BackendConsoleHandler(platform, json)
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        every { platform.executeCommand(any(), any(), any()) } returns false

        handler.handle(player, """{"type":"console_cmd","command":"bogus"}""")

        verify { platform.sendData(player, match<String> { it.contains("Unknown command") }) }
    }

    @Test
    fun `malformed JSON is logged and ignored`() {
        val (platform, logger) = platformFixture()
        val handler = BackendConsoleHandler(platform, json)
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        handler.handle(player, """{"type":"console_cmd""")

        verify { logger.warning(match { it.contains("malformed") }) }
        verify(exactly = 0) { platform.executeCommand(any(), any(), any()) }
    }

    @Test
    fun `suppressOwnExecutionLog callback is invoked before logging`() {
        val (platform, logger) = platformFixture()
        var suppressCalled = false
        var suppressMarker: String? = null
        val handler =
            BackendConsoleHandler(platform, json) { _, marker ->
                suppressCalled = true
                suppressMarker = marker
            }
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        every { platform.executeCommand(any(), any(), any()) } returns true

        handler.handle(player, """{"type":"console_cmd","command":"say hi"}""")

        assertTrue(suppressCalled)
        assertTrue(suppressMarker?.contains("say hi") == true)
        verify { logger.info(match<String> { it.contains("say hi") }) }
    }

    private fun platformFixture(): Pair<BackendPlatform, BackendLogger> {
        val platform = mockk<BackendPlatform>(relaxed = true)
        val logger = mockk<BackendLogger>(relaxed = true)
        every { platform.logger } returns logger
        every { platform.sendData(any(), any()) } just runs
        return platform to logger
    }
}
