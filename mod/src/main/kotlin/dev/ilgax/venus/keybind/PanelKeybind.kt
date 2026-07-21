package dev.ilgax.venus.keybind

import com.mojang.blaze3d.platform.InputConstants
import dev.ilgax.venus.channel.ChannelClient
import dev.ilgax.venus.client.ui.ModularVenusScreen
import dev.ilgax.venus.client.ui.module.UiScreenServices
import dev.ilgax.venus.client.ui.profile.UiProfileController
import dev.ilgax.venus.state.SessionState
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

    fun register(
        channelClient: ChannelClient,
        profiles: UiProfileController,
    ) {
        KeyBindingHelper.registerKeyBinding(keybind)

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (keybind.consumeClick()) {
                toggle(client, channelClient, profiles)
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
        profiles: UiProfileController,
    ) {
        if (client.screen is ModularVenusScreen) {
            client.setScreen(null)
        } else {
            val profile = profiles.resolve(SessionState.serverAddress)
            client.setScreen(
                ModularVenusScreen(
                    services =
                        UiScreenServices(
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
                            subscribeStats = channelClient::sendStatSubscribe,
                            requestFileRoots = channelClient::requestFileRoots,
                            requestFileList = channelClient::requestFileList,
                            sendFileAction = channelClient::sendFileAction,
                            uploadFile = { local, root, destination, overwrite ->
                                channelClient.uploadFile(local, root, destination, overwrite)
                            },
                            downloadFile = channelClient::downloadFile,
                            openFileEditor = channelClient::openFileEditor,
                            saveEditedFile = channelClient::saveEditedFile,
                            cancelFileTransfer = channelClient.fileTransfers::cancel,
                            profiles = profiles,
                        ),
                    profile = profile,
                ),
            )
        }
    }
}
