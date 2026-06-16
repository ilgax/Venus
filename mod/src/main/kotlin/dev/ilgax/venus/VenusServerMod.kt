package dev.ilgax.venus

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.backend.BackendAuthHandler
import dev.ilgax.venus.backend.BackendChannelHandler
import dev.ilgax.venus.backend.BackendConsoleHandler
import dev.ilgax.venus.backend.BackendLogHandler
import dev.ilgax.venus.backend.BackendPacketRouter
import dev.ilgax.venus.backend.BackendPlayersHandler
import dev.ilgax.venus.backend.BackendStatSubscriptionManager
import dev.ilgax.venus.backend.BackendStatsHandler
import dev.ilgax.venus.network.AuthResponsePayload
import dev.ilgax.venus.network.ClientKeyPayload
import dev.ilgax.venus.network.CmdPayload
import dev.ilgax.venus.network.ErrorPayload
import dev.ilgax.venus.network.HelloPayload
import dev.ilgax.venus.network.VenusRawAuthPayload
import dev.ilgax.venus.network.VenusRawDataPayload
import dev.ilgax.venus.network.VenusRawPayload
import dev.ilgax.venus.network.VenusRawReadyPayload
import dev.ilgax.venus.platform.FabricBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
import kotlinx.serialization.json.Json
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

class VenusServerMod : DedicatedServerModInitializer {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: MinecraftServer? = null
    private lateinit var authHandler: BackendAuthHandler
    private lateinit var channelHandler: BackendChannelHandler
    private lateinit var statSubscriptions: BackendStatSubscriptionManager
    private lateinit var logHandler: BackendLogHandler

    override fun onInitializeServer() {
        LOGGER.info("Venus Fabric server entrypoint loaded")
        val dataFolder =
            FabricLoader
                .getInstance()
                .configDir
                .resolve("venus")
                .toFile()
        val keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        AuthorizedKeys.init(dataFolder)

        val platform = FabricBackendPlatform({ server }, LOGGER)
        statSubscriptions = BackendStatSubscriptionManager(platform)
        logHandler = BackendLogHandler(platform, json)
        authHandler = BackendAuthHandler(platform, json, keyManager, statSubscriptions)
        channelHandler =
            BackendChannelHandler(
                authHandler,
                BackendPacketRouter(
                    platform,
                    json,
                    BackendConsoleHandler(platform, json) { player, marker ->
                        logHandler.suppressNextFor(player.uuid, marker)
                    },
                    BackendStatsHandler(platform, json, statSubscriptions),
                    logHandler,
                    BackendPlayersHandler(platform, json),
                ),
            )

        registerPayloads()
        registerReceivers()
        registerCommands()
        ServerLifecycleEvents.SERVER_STARTED.register { startedServer ->
            server = startedServer
            LOGGER.info("Venus Fabric server backend ready")
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            authHandler.cancelAllTimeouts()
            statSubscriptions.cancelAll()
            SessionManager.clearAll()
            server = null
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            LOGGER.info("Venus player joined: ${handler.player.name.string}; waiting for hello")
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            authHandler.onPlayerQuit(player.toBackendPlayer())
            logHandler.unsubscribe(player.uuid)
        }
    }

    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands
                    .literal("venus")
                    .then(Commands.literal("allow").executes { handleAllow(it.source) })
                    .then(Commands.literal("deny").executes { handleDeny(it.source) })
                    .then(Commands.literal("reload").executes { handleReload(it.source) }),
            )
        }
    }

    private fun registerPayloads() {
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ClientKeyPayload.TYPE, ClientKeyPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AuthResponsePayload.TYPE, AuthResponsePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ErrorPayload.TYPE, ErrorPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CmdPayload.TYPE, CmdPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ErrorPayload.TYPE, ErrorPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawPayload.TYPE, VenusRawPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawAuthPayload.TYPE, VenusRawAuthPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawReadyPayload.TYPE, VenusRawReadyPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawDataPayload.TYPE, VenusRawDataPayload.CODEC)
    }

    private fun registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE) { _, context ->
            val player = context.player()
            LOGGER.info("Venus hello received from ${player.name.string}")
            channelHandler.handle("venus:hello", player.toBackendPlayer(), "")
        }
        ServerPlayNetworking.registerGlobalReceiver(ClientKeyPayload.TYPE) { payload, context ->
            val player = context.player()
            LOGGER.info("Venus client key received from ${player.name.string}")
            channelHandler.handle("venus:key", player.toBackendPlayer(), payload.data)
        }
        ServerPlayNetworking.registerGlobalReceiver(AuthResponsePayload.TYPE) { payload, context ->
            val player = context.player()
            channelHandler.handle("venus:auth", player.toBackendPlayer(), payload.data)
        }
        ServerPlayNetworking.registerGlobalReceiver(ErrorPayload.TYPE) { payload, context ->
            val player = context.player()
            channelHandler.handle("venus:error", player.toBackendPlayer(), payload.data)
        }
        ServerPlayNetworking.registerGlobalReceiver(CmdPayload.TYPE) { payload, context ->
            val player = context.player()
            channelHandler.handle("venus:cmd", player.toBackendPlayer(), payload.data)
        }
    }

    private fun handleAllow(source: CommandSourceStack): Int {
        if (!source.isConsole()) {
            source.sendFailure(Component.literal("This command can only be run from the console."))
            return 0
        }
        val entry = SessionManager.getNextPendingApproval()
        if (entry == null) {
            source.sendSystemMessage(Component.literal("No pending Venus requests."))
            return 0
        }
        val (uuid, approval) = entry
        val player = server?.playerList?.getPlayer(uuid)
        if (player == null) {
            SessionManager.removePendingApproval(uuid)
            source.sendSystemMessage(Component.literal("Player is no longer online."))
            return 0
        }

        AuthorizedKeys.authorize(approval.clientPublicKeyBase64, player.name.string)
        SessionManager.removePendingApproval(uuid)
        authHandler.startApprovedChallenge(player.toBackendPlayer(), approval.clientPublicKey)
        source.sendSystemMessage(Component.literal("${player.name.string} authorized."))
        LOGGER.info("${player.name.string} authorized via console.")
        return 1
    }

    private fun handleDeny(source: CommandSourceStack): Int {
        if (!source.isConsole()) {
            source.sendFailure(Component.literal("This command can only be run from the console."))
            return 0
        }
        val entry = SessionManager.getNextPendingApproval()
        if (entry == null) {
            source.sendSystemMessage(Component.literal("No pending Venus requests."))
            return 0
        }
        val (uuid, _) = entry
        val player = server?.playerList?.getPlayer(uuid)
        val playerName = player?.name?.string ?: uuid.toString()
        SessionManager.removePendingApproval(uuid)
        if (player != null) {
            authHandler.notifyDenied(player.toBackendPlayer())
        }
        source.sendSystemMessage(Component.literal("$playerName denied."))
        return 1
    }

    private fun handleReload(source: CommandSourceStack): Int {
        source.sendSystemMessage(Component.literal("Venus Fabric config reload is not implemented yet."))
        return 1
    }

    private fun CommandSourceStack.isConsole(): Boolean = entity == null

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Venus")
    }
}
