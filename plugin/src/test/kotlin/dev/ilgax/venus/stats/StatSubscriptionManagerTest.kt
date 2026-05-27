package dev.ilgax.venus.stats

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.Server
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.Before
import org.junit.Test
import java.util.UUID

class StatSubscriptionManagerTest {

    private lateinit var plugin: Plugin
    private lateinit var server: Server
    private lateinit var scheduler: BukkitScheduler
    private lateinit var task: BukkitTask
    private val uuid = UUID.randomUUID()

    @Before
    fun setup() {
        plugin = mockk(relaxed = true)
        server = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        task = mockk(relaxed = true)

        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { scheduler.runTaskTimer(any(), any<Runnable>(), any(), any()) } returns task

        StatSubscriptionManager.cancelAll()
    }

    @Test
    fun `subscribe schedules repeating task`() {
        StatSubscriptionManager.subscribe(uuid, listOf("tps"), 5, plugin) { }
        verify { scheduler.runTaskTimer(plugin, any<Runnable>(), 100L, 100L) }
    }

    @Test
    fun `subscribe enforces minimum 2 second interval`() {
        StatSubscriptionManager.subscribe(uuid, listOf("tps"), 1, plugin) { }
        verify { scheduler.runTaskTimer(plugin, any<Runnable>(), 40L, 40L) }
    }

    @Test
    fun `subscribe cancels existing task`() {
        StatSubscriptionManager.subscribe(uuid, listOf("tps"), 5, plugin) { }
        StatSubscriptionManager.subscribe(uuid, listOf("tps"), 5, plugin) { }
        verify(exactly = 1) { task.cancel() }
    }

    @Test
    fun `cancel removes task`() {
        StatSubscriptionManager.subscribe(uuid, listOf("tps"), 5, plugin) { }
        StatSubscriptionManager.cancel(uuid)
        verify(exactly = 1) { task.cancel() }
    }

    @Test
    fun `cancelAll removes all tasks`() {
        val uuid2 = UUID.randomUUID()
        StatSubscriptionManager.subscribe(uuid, listOf("tps"), 5, plugin) { }
        StatSubscriptionManager.subscribe(uuid2, listOf("tps"), 5, plugin) { }
        StatSubscriptionManager.cancelAll()
        verify(exactly = 2) { task.cancel() }
    }
}
