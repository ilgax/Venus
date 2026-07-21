package dev.ilgax.venus.config

import dev.ilgax.venus.backend.BackendConfig
import dev.ilgax.venus.backend.BackendFileConfig
import dev.ilgax.venus.backend.BackendFileRoot
import dev.ilgax.venus.backend.BackendFileRootMode
import org.junit.Test
import org.slf4j.Logger
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FabricVenusConfigTest {
    @Test
    fun `load creates default server config without UI state`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        val loaded = FabricVenusConfig(dataFolder, logger()).load()
        val text = dataFolder.resolve("config.yml").readText()

        assertEquals(1, loaded.maxUsers)
        assertEquals(60, loaded.authTimeoutSeconds)
        assertFalse(text.contains("background_opacity"))
        assertFalse(text.contains("compact_mode"))
    }

    @Test
    fun `load applies custom server config values`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        dataFolder.resolve("config.yml").writeText(
            """
            max_users: 5
            auth_timeout_seconds: 120
            """.trimIndent() + "\n",
        )

        val loaded = FabricVenusConfig(dataFolder, logger()).load()

        assertEquals(5, loaded.maxUsers)
        assertEquals(120, loaded.authTimeoutSeconds)
    }

    @Test
    fun `load falls back for invalid server config values`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        dataFolder.resolve("config.yml").writeText(
            """
            max_users: 0
            auth_timeout_seconds: -1
            """.trimIndent() + "\n",
        )

        val loaded = FabricVenusConfig(dataFolder, logger()).load()

        assertEquals(1, loaded.maxUsers)
        assertEquals(60, loaded.authTimeoutSeconds)
    }

    @Test
    fun `file roots parse and round trip`() {
        val dataFolder = createTempDirectory("venus-fabric-config").toFile()
        val config = FabricVenusConfig(dataFolder, logger())
        config.save(
            BackendConfig(
                files =
                    BackendFileConfig(
                        roots =
                            listOf(
                                BackendFileRoot("configs", "Server Config", "config", BackendFileRootMode.READ_WRITE),
                                BackendFileRoot("logs", "Logs", "logs", BackendFileRootMode.READ_ONLY),
                            ),
                        reservedFreeBytes = 1234,
                        maxConcurrentTransfers = 3,
                        idleTimeoutSeconds = 45,
                    ),
            ),
        )

        val loaded = FabricVenusConfig(dataFolder, logger()).load().files

        assertEquals(2, loaded.roots.size)
        assertEquals(BackendFileRootMode.READ_WRITE, loaded.roots.first().mode)
        assertEquals(1234, loaded.reservedFreeBytes)
        assertEquals(3, loaded.maxConcurrentTransfers)
        assertEquals(45, loaded.idleTimeoutSeconds)
        assertTrue(dataFolder.resolve("config.yml").readText().contains("files:"))
    }

    private fun logger(): Logger = org.slf4j.helpers.NOPLogger.NOP_LOGGER
}
