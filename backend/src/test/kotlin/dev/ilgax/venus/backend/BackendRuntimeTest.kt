package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.KeyManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.Test
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertNotNull

class BackendRuntimeTest {
    @Test
    fun `create wires services and lifecycle cleanup`() {
        val platform = mockk<BackendPlatform>()
        val scheduler = mockk<BackendScheduler>()
        val timeoutTask = mockk<BackendTask>(relaxed = true)
        every { platform.scheduler } returns scheduler
        every { platform.config } returns BackendConfig()
        every { platform.logger } returns mockk(relaxed = true)
        every { platform.serverDirectory } returns Path.of(".").toAbsolutePath()
        every { platform.players() } returns mockk(relaxed = true)
        every { scheduler.runRepeating(any(), any(), any()) } returns timeoutTask

        val runtime = BackendRuntime.create(platform, Json { ignoreUnknownKeys = true }, mockk<KeyManager>(relaxed = true))

        assertNotNull(runtime.authHandler)
        assertNotNull(runtime.channelHandler)
        assertNotNull(runtime.logHandler)
        assertNotNull(runtime.statSubscriptions)
        assertNotNull(runtime.approvals)
        assertNotNull(runtime.files)

        runtime.onPlayerQuit(BackendPlayer(UUID.randomUUID(), "Player"))
        runtime.shutdown()

        verify { timeoutTask.cancel() }
    }
}
