package dev.ilgax.venus.keybind

import com.mojang.blaze3d.platform.InputConstants
import dev.ilgax.venus.channel.ChannelClient
import dev.ilgax.venus.client.ui.ModularVenusScreen
import dev.ilgax.venus.client.ui.editor.UiEditorScreen
import dev.ilgax.venus.client.ui.module.UiScreenServices
import dev.ilgax.venus.client.ui.profile.FactoryUiProfile
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
                val window = client.window.handle()
                val safeMode =
                    GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                        GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
                toggle(client, channelClient, profiles, safeMode)
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

    fun matchesRecovery(keyEvent: KeyEvent): Boolean = keyEvent.modifiers() and GLFW.GLFW_MOD_SHIFT != 0 && keybind.matches(keyEvent)

    private fun toggle(
        client: Minecraft,
        channelClient: ChannelClient,
        profiles: UiProfileController,
        safeMode: Boolean,
    ) {
        val services = services(client, channelClient, profiles)
        if (safeMode) {
            client.setScreen(ModularVenusScreen(services, FactoryUiProfile.profile, safeMode = true))
        } else if (client.screen is ModularVenusScreen || client.screen is UiEditorScreen) {
            client.setScreen(null)
        } else {
            val profile = profiles.resolve(SessionState.serverAddress)
            client.setScreen(ModularVenusScreen(services, profile))
        }
    }

    private fun services(
        client: Minecraft,
        channelClient: ChannelClient,
        profiles: UiProfileController,
    ): UiScreenServices {
        lateinit var services: UiScreenServices
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
                openEditor = { profile ->
                    client.setScreen(UiEditorScreen(services, profile, SessionState.serverAddress))
                },
            )
        return services
    }
}
