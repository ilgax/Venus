package dev.ilgax.venus

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.backend.BackendRuntime
import dev.ilgax.venus.config.FabricVenusConfig
import dev.ilgax.venus.log.FabricLogRelay
import dev.ilgax.venus.network.AuthResponsePayload
import dev.ilgax.venus.network.ClientKeyPayload
import dev.ilgax.venus.network.CmdPayload
import dev.ilgax.venus.network.ErrorPayload
import dev.ilgax.venus.network.HelloPayload
import dev.ilgax.venus.network.VenusPayloads
import dev.ilgax.venus.platform.FabricBackendPlatform
import dev.ilgax.venus.platform.toBackendPlayer
import dev.ilgax.venus.protocol.VenusChannels
import kotlinx.serialization.json.Json
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import org.slf4j.LoggerFactory

class VenusServerMod : DedicatedServerModInitializer {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: MinecraftServer? = null
    private lateinit var runtime: BackendRuntime
    private lateinit var config: FabricVenusConfig
    private lateinit var logRelay: FabricLogRelay

    override fun onInitializeServer() {
        LOGGER.info("Venus Fabric server entrypoint loaded")
        val dataFolder =
            FabricLoader
                .getInstance()
                .configDir
                .resolve("venus")
                .toFile()
        config = FabricVenusConfig(dataFolder, LOGGER)
        config.load()
        val keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        AuthorizedKeys.init(dataFolder)

        val platform = FabricBackendPlatform({ server }, LOGGER, config::backendConfig)
        runtime = BackendRuntime.create(platform, json, keyManager)
        logRelay = FabricLogRelay(runtime.logHandler)

        registerPayloads()
        registerReceivers()
        registerCommands()
        ServerLifecycleEvents.SERVER_STARTED.register { startedServer ->
            server = startedServer
            logRelay.start()
            LOGGER.info("Venus Fabric server backend ready")
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            logRelay.stop()
            runtime.shutdown()
            server = null
        }
        ServerTickEvents.END_SERVER_TICK.register { logRelay.flush() }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            LOGGER.info("Venus player joined: ${handler.player.name.string}; waiting for hello")
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            runtime.onPlayerQuit(handler.player.toBackendPlayer())
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
        VenusPayloads.registerPayloadTypes()
    }

    private fun registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE) { _, context ->
            val player = context.player()
            LOGGER.info("Venus hello received from ${player.name.string}")
            runtime.channelHandler.handle(VenusChannels.HELLO, player.toBackendPlayer(), "")
        }
        ServerPlayNetworking.registerGlobalReceiver(ClientKeyPayload.TYPE) { payload, context ->
            val player = context.player()
            LOGGER.info("Venus client key received from ${player.name.string}")
            runtime.channelHandler.handle(VenusChannels.KEY, player.toBackendPlayer(), payload.data)
        }
        ServerPlayNetworking.registerGlobalReceiver(AuthResponsePayload.TYPE) { payload, context ->
            val player = context.player()
            runtime.channelHandler.handle(VenusChannels.AUTH, player.toBackendPlayer(), payload.data)
        }
        ServerPlayNetworking.registerGlobalReceiver(ErrorPayload.TYPE) { payload, context ->
            val player = context.player()
            runtime.channelHandler.handle(VenusChannels.ERROR, player.toBackendPlayer(), payload.data)
        }
        ServerPlayNetworking.registerGlobalReceiver(CmdPayload.TYPE) { payload, context ->
            val player = context.player()
            runtime.channelHandler.handle(VenusChannels.CMD, player.toBackendPlayer(), payload.data)
        }
    }

    private fun handleAllow(source: CommandSourceStack): Int {
        if (!source.isConsole()) {
            source.sendFailure(Component.literal("This command can only be run from the console."))
            return 0
        }
        val result = runtime.approvals.allowNextPending()
        source.sendSystemMessage(Component.literal(result.message))
        return if (result.success) 1 else 0
    }

    private fun handleDeny(source: CommandSourceStack): Int {
        if (!source.isConsole()) {
            source.sendFailure(Component.literal("This command can only be run from the console."))
            return 0
        }
        val result = runtime.approvals.denyNextPending()
        source.sendSystemMessage(Component.literal(result.message))
        return if (result.success) 1 else 0
    }

    private fun handleReload(source: CommandSourceStack): Int {
        if (!source.isConsole() && !source.canReload()) {
            source.sendFailure(Component.literal("You don't have permission to use this command."))
            return 0
        }
        config.load()
        source.sendSystemMessage(Component.literal("Venus config reloaded."))
        LOGGER.info("Venus config reloaded by ${source.textName}.")
        return 1
    }

    private fun CommandSourceStack.isConsole(): Boolean = entity == null

    private fun CommandSourceStack.canReload(): Boolean =
        (
            permissions() as? LevelBasedPermissionSet
        )?.level()?.isEqualOrHigherThan(PermissionLevel.ADMINS) == true

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Venus")
    }
}
