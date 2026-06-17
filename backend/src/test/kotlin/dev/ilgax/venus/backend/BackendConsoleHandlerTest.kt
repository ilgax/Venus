package dev.ilgax.venus.backend

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlin.test.Test

class BackendConsoleHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `blank console command is ignored`() {
        val platform = mockk<BackendPlatform>(relaxed = true)
        val logger = mockk<BackendLogger>(relaxed = true)
        every { platform.logger } returns logger
        every { logger.warning(any()) } just runs
        val handler = BackendConsoleHandler(platform, json)
        val player = BackendPlayer(java.util.UUID.randomUUID(), "TestPlayer")

        handler.handle(player, """{"type":"console_cmd","command":"   "}""")

        verify { logger.warning(match { it.contains("blank console command") }) }
        verify(exactly = 0) { platform.executeCommand(any(), any(), any()) }
        verify(exactly = 0) { platform.sendData(any(), any()) }
    }
}
