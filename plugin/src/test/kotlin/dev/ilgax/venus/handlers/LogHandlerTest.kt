package dev.ilgax.venus.handlers

import dev.ilgax.venus.backend.BackendLogHandler
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.logging.log4j.LogManager
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.Test

class LogHandlerTest {
    @Test
    fun `start bridges log lines and scheduler flush until stop`() {
        val plugin = mockk<JavaPlugin>()
        val server = mockk<Server>()
        val scheduler = mockk<BukkitScheduler>()
        val task = mockk<BukkitTask>()
        val delegate = mockk<BackendLogHandler>()
        val flush = slot<Runnable>()
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { scheduler.runTaskTimer(plugin, capture(flush), any(), any()) } returns task
        every { delegate.formatLine("VenusCoverage", "hello", any()) } returns "[INFO] hello"
        every { delegate.queueFormattedLine(any()) } just Runs
        every { delegate.flush() } just Runs
        every { task.cancel() } just Runs
        val handler = LogHandler(plugin, delegate)

        handler.start()
        LogManager.getLogger("VenusCoverage").info("hello")
        flush.captured.run()
        handler.stop()
        handler.stop()

        verify { delegate.queueFormattedLine("[INFO] hello") }
        verify { delegate.flush() }
        verify { task.cancel() }
    }
}
