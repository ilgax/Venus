package dev.ilgax.venus

import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.ServerKeyStore
import dev.ilgax.venus.channel.ChannelClient
import dev.ilgax.venus.channel.PacketHandler
import dev.ilgax.venus.gui.AuthToasts
import dev.ilgax.venus.keybind.PanelKeybind
import dev.ilgax.venus.state.SessionState
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.io.File

class VenusMod : ClientModInitializer {
    private lateinit var channelClient: ChannelClient
    private var pendingHelloAttempts = 0

    override fun onInitializeClient() {
        LOGGER.info("Venus mod loaded")

        val venusFolder = File(Minecraft.getInstance().gameDirectory, "config/venus")
        val keyManager = KeyManager(venusFolder, "client_private.key", "client_public.key")
        keyManager.loadOrGenerate()
        ServerKeyStore.init(venusFolder)

        val json = Json { ignoreUnknownKeys = true }
        val log: (String) -> Unit = { LOGGER.info(it) }
        channelClient = ChannelClient(json, keyManager, log, AuthToasts::failure)
        val packetHandler = PacketHandler(json, channelClient::sendCommand, log, AuthToasts::success, AuthToasts::failure)
        channelClient.register(packetHandler)
        PanelKeybind.register(channelClient)

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            val server = Minecraft.getInstance().currentServer
            SessionState.setServerInfo(server?.ip, server?.name)
            if (channelClient.canSendHello()) {
                LOGGER.info("Venus hello channel available; sending hello")
                channelClient.sendHello()
            } else {
                LOGGER.warn("Venus hello channel not available on join; retrying")
                pendingHelloAttempts = HELLO_RETRY_TICKS
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (pendingHelloAttempts <= 0 || Minecraft.getInstance().player == null) return@register

            if (channelClient.canSendHello()) {
                LOGGER.info("Venus hello channel became available; sending hello")
                channelClient.sendHello()
                pendingHelloAttempts = 0
                return@register
            }

            pendingHelloAttempts -= 1
            if (pendingHelloAttempts == 0) {
                LOGGER.warn("Venus hello channel was not advertised by this server")
            }
        }

        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            if (SessionState.sessionActive && message.startsWith("$")) {
                val command = message.removePrefix("$").trim()
                if (command.isNotBlank()) {
                    channelClient.sendConsoleCommand(command)
                }
                false
            } else {
                true
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SessionState.reset()
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Venus")
        private const val HELLO_RETRY_TICKS = 40
    }
}
