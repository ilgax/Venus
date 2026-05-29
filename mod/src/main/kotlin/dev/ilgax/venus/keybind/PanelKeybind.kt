package dev.ilgax.venus.keybind

import com.mojang.blaze3d.platform.InputConstants
import dev.ilgax.venus.channel.ChannelClient
import dev.ilgax.venus.gui.PanelScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object PanelKeybind {
    private val category =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("venus", "venus"),
        )

    private val keybind =
        KeyMapping(
            "key.venus.panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            category,
        )

    fun register(channelClient: ChannelClient) {
        KeyBindingHelper.registerKeyBinding(keybind)

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (keybind.consumeClick()) {
                toggle(client, channelClient)
            }
        }
    }

    fun matches(
        keyEvent: KeyEvent,
        textInputFocused: Boolean = false,
    ): Boolean {
        if (textInputFocused) {
            return keyEvent.key() == GLFW.GLFW_KEY_F6 && keyEvent.modifiers() == 0
        }
        return keyEvent.modifiers() == 0 && keybind.matches(keyEvent)
    }

    private fun toggle(
        client: Minecraft,
        channelClient: ChannelClient,
    ) {
        if (client.screen is PanelScreen) {
            client.setScreen(null)
        } else {
            client.setScreen(
                PanelScreen(
                    sendConsoleCommand = channelClient::sendConsoleCommand,
                    subscribeLogs = channelClient::sendLogSubscribe,
                    requestPlayerList = channelClient::sendPlayerListGet,
                    requestPlayerDetail = channelClient::sendPlayerDetailGet,
                    sendPlayerAction = { uuid, action, value ->
                        when (value) {
                            is Boolean -> channelClient.sendPlayerAction(uuid, action, value)
                            is String -> channelClient.sendPlayerAction(uuid, action, value)
                            else -> channelClient.sendPlayerAction(uuid, action, null)
                        }
                    },
                ),
            )
        }
    }
}
