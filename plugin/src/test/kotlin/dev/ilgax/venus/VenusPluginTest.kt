package dev.ilgax.venus

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.stats.StatSubscriptionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.bukkit.Server
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.messaging.Messenger
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.logging.Logger

class VenusPluginTest {
    private lateinit var plugin: VenusPlugin
    private lateinit var server: Server
    private lateinit var pluginManager: PluginManager
    private lateinit var messenger: Messenger
    private lateinit var logger: Logger

    @Before
    fun setup() {
        plugin = mockk(relaxed = true)
        server = mockk(relaxed = true)
        pluginManager = mockk(relaxed = true)
        messenger = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        every { plugin.server } returns server
        every { server.pluginManager } returns pluginManager
        every { server.messenger } returns messenger
        every { plugin.logger } returns logger
        every { plugin.dataFolder } returns File("dummy")

        mockkObject(VenusConfig)
        mockkObject(AuthorizedKeys)
        mockkObject(SessionManager)
        mockkObject(StatSubscriptionManager)

        every { VenusConfig.load(any()) } returns Unit
        every { AuthorizedKeys.init(any()) } returns Unit
        every { SessionManager.clearAll() } returns Unit
        every { StatSubscriptionManager.cancelAll() } returns Unit
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `dummy test to prevent unused class warning`() {
        // Test runner will now recognize this as a test class
    }
}
