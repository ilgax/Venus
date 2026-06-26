package dev.ilgax.venus.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendConfigTest {
    @Test
    fun `defaults match current Venus config defaults`() {
        val config = BackendConfig()

        assertEquals(1, config.maxUsers)
        assertEquals(60, config.authTimeoutSeconds)
        assertEquals(false, config.compactMode)
        assertEquals(true, config.animationsEnabled)
        assertEquals(0.75f, config.backgroundOpacity)
        assertEquals(true, config.showPlayerHeads)
        assertEquals(true, config.confirmDangerousActions)
        assertEquals(500, config.consoleHistoryLimit)
    }

    @Test
    fun `rejects non-positive values`() {
        assertFailsWith<IllegalArgumentException> {
            BackendConfig(maxUsers = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig(authTimeoutSeconds = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig(consoleHistoryLimit = 0)
        }
    }

    @Test
    fun `rejects out-of-range background opacity`() {
        assertFailsWith<IllegalArgumentException> {
            BackendConfig(backgroundOpacity = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            BackendConfig(backgroundOpacity = 1.1f)
        }
    }

    @Test
    fun `accepts boundary values for UI fields`() {
        BackendConfig(backgroundOpacity = 0f)
        BackendConfig(backgroundOpacity = 1f)
        BackendConfig(consoleHistoryLimit = 1)
        BackendConfig(consoleHistoryLimit = BackendConfig.MAX_CONSOLE_HISTORY_LIMIT)
    }
}
