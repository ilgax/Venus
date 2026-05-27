package dev.ilgax.venus

import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.ServerKeyStore
import dev.ilgax.venus.channel.ChannelClient
import dev.ilgax.venus.channel.PacketHandler
import dev.ilgax.venus.state.SessionState
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import java.io.File

class VenusMod : ClientModInitializer {
    private lateinit var channelClient: ChannelClient

    override fun onInitializeClient() {
        println("Venus mod loaded!")

        val venusFolder = File(Minecraft.getInstance().gameDirectory, "config/venus")
        val keyManager = KeyManager(venusFolder, "client_private.key", "client_public.key")
        keyManager.loadOrGenerate()
        println("Venus client keypair loaded")
        ServerKeyStore.init(venusFolder)

        val json = Json { ignoreUnknownKeys = true }
        channelClient = ChannelClient(json, keyManager)
        val packetHandler = PacketHandler(json, channelClient::sendCommand)
        channelClient.register(packetHandler)

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            channelClient.sendHello()
        }

        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            if (SessionState.sessionActive && message.startsWith("$")) {
                channelClient.sendConsoleCommand(message.removePrefix("$").trim())
                false
            } else {
                true
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SessionState.reset()
        }
    }
}
