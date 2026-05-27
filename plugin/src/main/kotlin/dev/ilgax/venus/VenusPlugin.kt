package dev.ilgax.venus

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.channel.ChannelListener
import dev.ilgax.venus.channel.PacketRouter
import dev.ilgax.venus.commands.VenusCommand
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.handlers.AuthHandler
import dev.ilgax.venus.handlers.ConsoleHandler
import dev.ilgax.venus.handlers.StatsHandler
import dev.ilgax.venus.protocol.VenusChannels
import dev.ilgax.venus.stats.StatSubscriptionManager
import kotlinx.serialization.json.Json
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.DiscardedPayload
import net.minecraft.resources.Identifier
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class VenusPlugin :
    JavaPlugin(),
    Listener {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var authHandler: AuthHandler
    private lateinit var channelListener: ChannelListener

    override fun onEnable() {
        VenusConfig.load(this)
        logger.info("Venus enabled")
        val keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        logger.info("Server keypair loaded")
        AuthorizedKeys.init(dataFolder)

        val sendData = { player: Player, data: String -> sendPayloadToPlayer(player, "data", data) }
        val packetRouter =
            PacketRouter(
                this,
                json,
                ConsoleHandler(this, json, sendData),
                StatsHandler(this, json, sendData),
            )
        authHandler =
            AuthHandler(
                this,
                json,
                keyManager,
                sendKey = { player, data -> sendPayloadToPlayer(player, "key", data) },
                sendAuth = { player, data -> sendPayloadToPlayer(player, "auth", data) },
                sendReady = { player, data -> sendPayloadToPlayer(player, "ready", data) },
            )
        channelListener = ChannelListener(authHandler, packetRouter)

        registerCommand("venus", VenusCommand(this, authHandler))
        server.pluginManager.registerEvents(this, this)

        server.messenger.registerIncomingPluginChannel(this, VenusChannels.HELLO, channelListener)
        server.messenger.registerIncomingPluginChannel(this, VenusChannels.KEY, channelListener)
        server.messenger.registerIncomingPluginChannel(this, VenusChannels.AUTH, channelListener)
        server.messenger.registerIncomingPluginChannel(this, VenusChannels.ERROR, channelListener)
        server.messenger.registerIncomingPluginChannel(this, VenusChannels.CMD, channelListener)
    }

    override fun onDisable() {
        logger.info("Venus disabled")
        authHandler.cancelAllTimeouts()
        StatSubscriptionManager.cancelAll()
        SessionManager.clearAll()
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.HELLO)
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.KEY)
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.AUTH)
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.ERROR)
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.CMD)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        authHandler.onPlayerQuit(event.player)
    }

    private fun sendPayloadToPlayer(
        player: Player,
        channelPath: String,
        data: String,
    ) {
        val id = Identifier.fromNamespaceAndPath("venus", channelPath)
        val packet = ClientboundCustomPayloadPacket(DiscardedPayload(id, data.toByteArray(Charsets.UTF_8)))
        (player as CraftPlayer).handle.connection.send(packet)
    }
}
