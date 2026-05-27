package dev.ilgax.venus.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals

class VenusConfigTest {
    @Test
    fun `load correctly applies defaults when config is empty`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val config = mockk<FileConfiguration>(relaxed = true)

        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { config.getInt("max_users", any()) } returns 1
        every { config.getInt("auth_timeout_seconds", any()) } returns 60
        every { config.getBoolean("cache_verified_uuid", any()) } returns true

        VenusConfig.load(plugin)

        verify { plugin.saveDefaultConfig() }
        verify { plugin.reloadConfig() }

        assertEquals(1, VenusConfig.maxUsers)
        assertEquals(60, VenusConfig.authTimeoutSeconds)
        assertEquals(true, VenusConfig.cacheVerifiedUuid)
    }

    @Test
    fun `load applies custom values from config`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val config = mockk<FileConfiguration>(relaxed = true)

        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { config.getInt("max_users", any()) } returns 5
        every { config.getInt("auth_timeout_seconds", any()) } returns 120
        every { config.getBoolean("cache_verified_uuid", any()) } returns false

        VenusConfig.load(plugin)

        assertEquals(5, VenusConfig.maxUsers)
        assertEquals(120, VenusConfig.authTimeoutSeconds)
        assertEquals(false, VenusConfig.cacheVerifiedUuid)
    }
}
