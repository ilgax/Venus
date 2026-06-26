package dev.ilgax.venus.config

import org.junit.Test
import org.slf4j.Logger
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
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

    private fun logger(): Logger = org.slf4j.helpers.NOPLogger.NOP_LOGGER
}
