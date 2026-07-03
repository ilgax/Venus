package dev.ilgax.venus.config

import dev.ilgax.venus.backend.BackendConfig
import dev.ilgax.venus.backend.BackendFileRootMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.configuration.ConfigurationSection
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

        VenusConfig.load(plugin)

        verify { plugin.saveDefaultConfig() }
        verify { plugin.reloadConfig() }

        assertEquals(1, VenusConfig.maxUsers)
        assertEquals(60, VenusConfig.authTimeoutSeconds)
    }

    @Test
    fun `load applies custom values from config`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val config = mockk<FileConfiguration>(relaxed = true)

        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { config.getInt("max_users", any()) } returns 5
        every { config.getInt("auth_timeout_seconds", any()) } returns 120

        VenusConfig.load(plugin)

        assertEquals(5, VenusConfig.maxUsers)
        assertEquals(120, VenusConfig.authTimeoutSeconds)
    }

    @Test
    fun `load falls back for invalid values`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val config = mockk<FileConfiguration>(relaxed = true)

        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { config.getInt("max_users", any()) } returns 0
        every { config.getInt("auth_timeout_seconds", any()) } returns -1

        VenusConfig.load(plugin)

        assertEquals(1, VenusConfig.maxUsers)
        assertEquals(60, VenusConfig.authTimeoutSeconds)
    }

    @Test
    fun `V29 load falls back for values above backend limits`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val config = mockk<FileConfiguration>(relaxed = true)
        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { config.getInt("max_users", any()) } returns BackendConfig.MAX_USERS_LIMIT + 1
        every { config.getInt("auth_timeout_seconds", any()) } returns BackendConfig.MAX_AUTH_TIMEOUT_LIMIT + 1

        VenusConfig.load(plugin)

        assertEquals(BackendConfig.DEFAULT_MAX_USERS, VenusConfig.maxUsers)
        assertEquals(BackendConfig.DEFAULT_AUTH_TIMEOUT_SECONDS, VenusConfig.authTimeoutSeconds)
    }

    @Test
    fun `load parses configured file roots`() {
        val plugin = mockk<JavaPlugin>(relaxed = true)
        val config = mockk<FileConfiguration>(relaxed = true)
        val files = mockk<ConfigurationSection>()
        val roots = mockk<ConfigurationSection>()
        val root = mockk<ConfigurationSection>()
        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getAnonymousLogger()
        every { config.getInt("max_users", any()) } returns 1
        every { config.getInt("auth_timeout_seconds", any()) } returns 60
        every { config.getConfigurationSection("files") } returns files
        every { files.getConfigurationSection("roots") } returns roots
        every { roots.getKeys(false) } returns setOf("configs")
        every { roots.getConfigurationSection("configs") } returns root
        every { root.getString("path") } returns "config"
        every { root.getString("label", "configs") } returns "Server Config"
        every { root.getString("mode", "read_only") } returns "read_write"
        every { files.getLong("reserved_free_bytes", any()) } returns 1234
        every { files.getInt("max_concurrent_transfers", any()) } returns 3
        every { files.getInt("idle_timeout_seconds", any()) } returns 45

        VenusConfig.load(plugin)

        assertEquals(1, VenusConfig.files.roots.size)
        assertEquals(
            BackendFileRootMode.READ_WRITE,
            VenusConfig.files.roots
                .single()
                .mode,
        )
        assertEquals(1234, VenusConfig.files.reservedFreeBytes)
        assertEquals(3, VenusConfig.files.maxConcurrentTransfers)
        assertEquals(45, VenusConfig.files.idleTimeoutSeconds)
    }
}
