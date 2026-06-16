package dev.ilgax.venus.backend

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test

class BackendStatSubscriptionManagerTest {
    @Test
    fun `subscribe schedules repeating task`() {
        val platform = platformFixture()
        val uuid = UUID.randomUUID()

        BackendStatSubscriptionManager(platform.platform).subscribe(uuid, listOf("tps"), 5)

        verify { platform.scheduler.runRepeating(100L, 100L, any()) }
    }

    @Test
    fun `subscribe enforces minimum two second interval`() {
        val platform = platformFixture()
        val uuid = UUID.randomUUID()

        BackendStatSubscriptionManager(platform.platform).subscribe(uuid, listOf("tps"), 1)

        verify { platform.scheduler.runRepeating(40L, 40L, any()) }
    }

    @Test
    fun `subscribe cancels existing task`() {
        val platform = platformFixture()
        val uuid = UUID.randomUUID()
        val manager = BackendStatSubscriptionManager(platform.platform)

        manager.subscribe(uuid, listOf("tps"), 5)
        manager.subscribe(uuid, listOf("tps"), 5)

        verify(exactly = 1) { platform.task.cancel() }
    }

    @Test
    fun `cancelAll removes all tasks`() {
        val platform = platformFixture()
        val manager = BackendStatSubscriptionManager(platform.platform)

        manager.subscribe(UUID.randomUUID(), listOf("tps"), 5)
        manager.subscribe(UUID.randomUUID(), listOf("tps"), 5)
        manager.cancelAll()

        verify(exactly = 2) { platform.task.cancel() }
    }

    private fun platformFixture(): PlatformFixture {
        val task = mockk<BackendTask>(relaxed = true)
        val scheduler = mockk<BackendScheduler>(relaxed = true)
        every { scheduler.runRepeating(any(), any(), any()) } returns task
        val platform = mockk<BackendPlatform>(relaxed = true)
        every { platform.scheduler } returns scheduler
        return PlatformFixture(platform, scheduler, task)
    }

    private data class PlatformFixture(
        val platform: BackendPlatform,
        val scheduler: BackendScheduler,
        val task: BackendTask,
    )
}
