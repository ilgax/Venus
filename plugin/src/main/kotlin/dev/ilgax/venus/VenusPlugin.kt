package dev.ilgax.venus

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.backend.BackendStatSubscriptionManager
import dev.ilgax.venus.channel.ChannelListener
import dev.ilgax.venus.channel.PacketRouter
import dev.ilgax.venus.commands.VenusCommand
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.handlers.AuthHandler
import dev.ilgax.venus.handlers.ConsoleHandler
import dev.ilgax.venus.handlers.LogHandler
import dev.ilgax.venus.handlers.PlayersHandler
import dev.ilgax.venus.handlers.StatsHandler
import dev.ilgax.venus.platform.PaperBackendPlatform
import dev.ilgax.venus.protocol.VenusChannels
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
    private lateinit var logHandler: LogHandler
    private lateinit var channelListener: ChannelListener
    private lateinit var statSubscriptions: BackendStatSubscriptionManager

    override fun onEnable() {
        VenusConfig.load(this)
        logger.info("Venus enabled")
        val keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        AuthorizedKeys.init(dataFolder)

        val sendData = { player: Player, data: String -> sendPayloadToPlayer(player, "data", data) }
        val platform =
            PaperBackendPlatform(
                this,
                sendKeyPacket = { player, data -> sendPayloadToPlayer(player, "key", data) },
                sendAuthPacket = { player, data -> sendPayloadToPlayer(player, "auth", data) },
                sendReadyPacket = { player, data -> sendPayloadToPlayer(player, "ready", data) },
                sendErrorPacket = { player, data -> sendPayloadToPlayer(player, "error", data) },
                sendDataPacket = sendData,
            )
        statSubscriptions = BackendStatSubscriptionManager(platform)
        logHandler = LogHandler(this, platform, json)
        val packetRouter =
            PacketRouter(
                this,
                json,
                ConsoleHandler(platform, json, logHandler::suppressNextFor),
                StatsHandler(platform, json, statSubscriptions),
                logHandler,
                PlayersHandler(platform, json),
            )
        authHandler =
            AuthHandler(
                platform,
                json,
                keyManager,
                statSubscriptions,
            )
        channelListener = ChannelListener(authHandler, packetRouter)
        logHandler.start()

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
        logHandler.stop()
        authHandler.cancelAllTimeouts()
        statSubscriptions.cancelAll()
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
        logHandler.unsubscribe(event.player.uniqueId)
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
