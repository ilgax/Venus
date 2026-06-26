package dev.ilgax.venus.config

import dev.ilgax.venus.backend.BackendConfig
import org.junit.Test
import org.slf4j.Logger
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FabricVenusConfigTest {
    @Test
    fun `load creates default config file`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        val config = FabricVenusConfig(dataFolder, logger())

        val loaded = config.load()

        assertTrue(dataFolder.resolve("config.yml").isFile)
        assertEquals(1, loaded.maxUsers)
        assertEquals(60, loaded.authTimeoutSeconds)
    }

    @Test
    fun `load applies custom config values`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        dataFolder.resolve("config.yml").writeText(
            """
            max_users: 5
            auth_timeout_seconds: 120
            """.trimIndent() + "\n",
        )
        val config = FabricVenusConfig(dataFolder, logger())

        val loaded = config.load()

        assertEquals(5, loaded.maxUsers)
        assertEquals(120, loaded.authTimeoutSeconds)
    }

    @Test
    fun `load falls back for invalid config values`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        dataFolder.resolve("config.yml").writeText(
            """
            max_users: 0
            auth_timeout_seconds: -1
            """.trimIndent() + "\n",
        )
        val config = FabricVenusConfig(dataFolder, logger())

        val loaded = config.load()

        assertEquals(1, loaded.maxUsers)
        assertEquals(60, loaded.authTimeoutSeconds)
    }

    @Test
    fun `load applies custom UI values`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        dataFolder.resolve("config.yml").writeText(
            """
            max_users: 1
            auth_timeout_seconds: 60
            compact_mode: true
            animations_enabled: false
            background_opacity: 0.5
            show_player_heads: false
            confirm_dangerous_actions: false
            console_history_limit: 1000
            """.trimIndent() + "\n",
        )
        val config = FabricVenusConfig(dataFolder, logger())

        val loaded = config.load()

        assertTrue(loaded.compactMode)
        assertFalse(loaded.animationsEnabled)
        assertEquals(0.5f, loaded.backgroundOpacity)
        assertFalse(loaded.showPlayerHeads)
        assertFalse(loaded.confirmDangerousActions)
        assertEquals(1000, loaded.consoleHistoryLimit)
    }

    @Test
    fun `save persists UI values and round-trips`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        val config = FabricVenusConfig(dataFolder, logger())
        config.load()

        config.save(
            BackendConfig(
                maxUsers = 3,
                authTimeoutSeconds = 90,
                compactMode = true,
                animationsEnabled = false,
                backgroundOpacity = 0.4f,
                showPlayerHeads = false,
                confirmDangerousActions = false,
                consoleHistoryLimit = 2000,
            ),
        )

        val reloaded = FabricVenusConfig(dataFolder, logger()).load()
        assertEquals(3, reloaded.maxUsers)
        assertEquals(90, reloaded.authTimeoutSeconds)
        assertTrue(reloaded.compactMode)
        assertFalse(reloaded.animationsEnabled)
        assertEquals(0.4f, reloaded.backgroundOpacity)
        assertFalse(reloaded.showPlayerHeads)
        assertFalse(reloaded.confirmDangerousActions)
        assertEquals(2000, reloaded.consoleHistoryLimit)
    }

    @Test
    fun `load falls back for invalid UI values`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        dataFolder.resolve("config.yml").writeText(
            """
            background_opacity: 2.0
            console_history_limit: 0
            animations_enabled: maybe
            """.trimIndent() + "\n",
        )
        val config = FabricVenusConfig(dataFolder, logger())

        val loaded = config.load()

        assertEquals(BackendConfig.DEFAULT_BACKGROUND_OPACITY, loaded.backgroundOpacity)
        assertEquals(BackendConfig.DEFAULT_CONSOLE_HISTORY_LIMIT, loaded.consoleHistoryLimit)
        assertEquals(BackendConfig.DEFAULT_ANIMATIONS_ENABLED, loaded.animationsEnabled)
    }

    private fun logger(): Logger = org.slf4j.helpers.NOPLogger.NOP_LOGGER
}
