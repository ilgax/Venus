package dev.ilgax.venus

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.backend.BackendIncomingChannel
import dev.ilgax.venus.backend.BackendRuntime
import dev.ilgax.venus.channel.ChannelListener
import dev.ilgax.venus.commands.VenusCommand
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.handlers.LogHandler
import dev.ilgax.venus.platform.PaperBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
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
    private var runtime: BackendRuntime? = null
    private var logHandler: LogHandler? = null
    private var channelListener: ChannelListener? = null

    override fun onEnable() {
        VenusConfig.load(this)
        logger.info("Venus enabled")
        val keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        AuthorizedKeys.init(dataFolder)

        val sendData = { player: Player, data: String -> sendPayloadToPlayer(player, VenusChannels.DATA, data) }
        val platform =
            PaperBackendPlatform(
                this,
                sendKeyPacket = { player, data -> sendPayloadToPlayer(player, VenusChannels.KEY, data) },
                sendAuthPacket = { player, data -> sendPayloadToPlayer(player, VenusChannels.AUTH, data) },
                sendReadyPacket = { player, data -> sendPayloadToPlayer(player, VenusChannels.READY, data) },
                sendErrorPacket = { player, data -> sendPayloadToPlayer(player, VenusChannels.ERROR, data) },
                sendDataPacket = sendData,
            )
        runtime = BackendRuntime.create(platform, json, keyManager)
        val rt = runtime!!
        logHandler = LogHandler(this, rt.logHandler)
        channelListener = ChannelListener(rt.channelHandler)
        logHandler!!.start()

        registerCommand("venus", VenusCommand(this, rt.approvals))
        server.pluginManager.registerEvents(this, this)

        BackendIncomingChannel.entries.forEach { channel ->
            server.messenger.registerIncomingPluginChannel(this, channel.channel, channelListener!!)
        }
    }

    override fun onDisable() {
        logger.info("Venus disabled")
        logHandler?.stop()
        runtime?.shutdown()
        BackendIncomingChannel.entries.forEach { channel ->
            server.messenger.unregisterIncomingPluginChannel(this, channel.channel)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        runtime?.onPlayerQuit(event.player.toBackendPlayer())
    }

    private fun sendPayloadToPlayer(
        player: Player,
        channel: String,
        data: String,
    ) {
        val id = channelId(channel)
        val packet = ClientboundCustomPayloadPacket(DiscardedPayload(id, data.toByteArray(Charsets.UTF_8)))
        (player as CraftPlayer).handle.connection.send(packet)
    }

    private fun channelId(channel: String): Identifier = Identifier.fromNamespaceAndPath("venus", channel.substringAfter(':'))
}
