package dev.ilgax.venus

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.PendingApproval
import dev.ilgax.venus.auth.PendingSession
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.commands.VenusCommand
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.protocol.AuthChallengePacket
import dev.ilgax.venus.protocol.AuthResponsePacket
import dev.ilgax.venus.protocol.ClientKeyPacket
import dev.ilgax.venus.protocol.CmdResponsePacket
import dev.ilgax.venus.protocol.ConsoleCmdPacket
import dev.ilgax.venus.protocol.ErrorPacket
import dev.ilgax.venus.protocol.ReadyPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import dev.ilgax.venus.protocol.StatGetPacket
import dev.ilgax.venus.protocol.StatSubscribePacket
import dev.ilgax.venus.protocol.VenusChannels
import dev.ilgax.venus.stats.StatSubscriptionManager
import dev.ilgax.venus.stats.StatsCollector
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.DiscardedPayload
import net.minecraft.resources.Identifier
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener
import org.bukkit.scheduler.BukkitTask
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VenusPlugin :
    JavaPlugin(),
    PluginMessageListener,
    Listener {
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionTimeoutTasks = ConcurrentHashMap<UUID, BukkitTask>()

    lateinit var keyManager: KeyManager

    override fun onEnable() {
        VenusConfig.load(this)
        logger.info("Venus enabled")
        keyManager = KeyManager(dataFolder)
        keyManager.loadOrGenerate()
        logger.info("Server keypair loaded")
        AuthorizedKeys.init(dataFolder)

        registerCommand("venus", VenusCommand(this))
        server.pluginManager.registerEvents(this, this)

        server.messenger.registerIncomingPluginChannel(this, VenusChannels.HELLO, this)
        server.messenger.registerIncomingPluginChannel(this, VenusChannels.KEY, this)
        server.messenger.registerIncomingPluginChannel(this, VenusChannels.AUTH, this)
        server.messenger.registerIncomingPluginChannel(this, VenusChannels.ERROR, this)
        server.messenger.registerIncomingPluginChannel(this, VenusChannels.CMD, this)
    }

    override fun onDisable() {
        logger.info("Venus disabled")
        sessionTimeoutTasks.values.forEach { it.cancel() }
        sessionTimeoutTasks.clear()
        StatSubscriptionManager.cancelAll()
        SessionManager.clearAll()
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.HELLO)
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.KEY)
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.AUTH)
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.ERROR)
        server.messenger.unregisterIncomingPluginChannel(this, VenusChannels.CMD)
    }

    override fun onPluginMessageReceived(
        channel: String,
        player: Player,
        message: ByteArray,
    ) {
        when (channel) {
            VenusChannels.HELLO -> {
                sessionTimeoutTasks.remove(player.uniqueId)?.cancel()
                logger.info("Venus mod detected: ${player.name}")
                sendServerPublicKey(player)
            }

            VenusChannels.KEY -> {
                handleClientKey(player, message.toString(Charsets.UTF_8))
            }

            VenusChannels.AUTH -> {
                handleAuthResponse(player, message.toString(Charsets.UTF_8))
            }

            VenusChannels.ERROR -> {
                val packet = try {
                    json.decodeFromString<ErrorPacket>(message.toString(Charsets.UTF_8))
                } catch (e: SerializationException) {
                    logger.warning("${player.name} sent malformed error packet: ${e.message}")
                    return
                }
                if (packet.type != "error") {
                    logger.warning("${player.name} sent invalid error packet type: ${packet.type}")
                    return
                }
                when (val reason = packet.reason) {
                    "mitm_key_mismatch" -> {
                        logger.warning(
                            "${player.name} rejected connection - server key mismatch on client side (possible MITM)",
                        )
                    }

                    "mitm_sig_fail" -> {
                        logger.warning(
                            "${player.name} rejected connection - server signature verification failed on client side (possible MITM)",
                        )
                    }

                    else -> {
                        logger.warning("${player.name} sent error: $reason")
                    }
                }
            }

            VenusChannels.CMD -> {
                val data = message.toString(Charsets.UTF_8)
                handleCmdPacket(player, data)
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        if (!SessionManager.isActive(uuid)) return

        logger.info("${event.player.name} disconnected - starting ${VenusConfig.sessionTimeoutSeconds}s Venus session timeout")

        val task = server.scheduler.runTaskLater(
            this,
            Runnable {
                if (!SessionManager.isActive(uuid)) return@Runnable
                SessionManager.deactivate(uuid)
                StatSubscriptionManager.cancel(uuid)
                sessionTimeoutTasks.remove(uuid)
                logger.info("Venus session expired for ${event.player.name}")
            },
            (VenusConfig.sessionTimeoutSeconds * 20L),
        )
        sessionTimeoutTasks[uuid] = task
    }

    private fun sendServerPublicKey(player: Player) {
        val data =
            json.encodeToString(
                ServerKeyPacket.serializer(),
                ServerKeyPacket(type = "server_key", publicKey = keyManager.publicKeyBase64),
            )
        val keyBytes = data.toByteArray(Charsets.UTF_8)
        val id = Identifier.fromNamespaceAndPath("venus", "key")
        val payload = DiscardedPayload(id, keyBytes)
        val packet = ClientboundCustomPayloadPacket(payload)
        (player as CraftPlayer).handle.connection.send(packet)
        logger.info("Sent server public key to ${player.name}")
    }

    private fun handleClientKey(
        player: Player,
        data: String,
    ) {
        val packet = try {
            json.decodeFromString<ClientKeyPacket>(data)
        } catch (e: SerializationException) {
            logger.warning("Malformed client key packet from ${player.name}: ${e.message}")
            return
        }
        if (packet.type != "client_key") {
            logger.warning("Invalid client key packet type from ${player.name}: ${packet.type}")
            return
        }
        val clientPublicKeyBase64 = packet.publicKey
        if (clientPublicKeyBase64.isBlank()) {
            logger.warning("Empty client key from ${player.name}")
            return
        }

        val clientPublicKey =
            try {
                Handshake.decodePublicKey(clientPublicKeyBase64)
            } catch (e: Exception) {
                logger.warning("Invalid client key from ${player.name}: ${e.message}")
                return
            }

        if (server.onlineMode && VenusConfig.cacheVerifiedUuid) {
            val cachedKey = SessionManager.getCachedKey(player.uniqueId)
            if (cachedKey != null) {
                if (cachedKey != clientPublicKeyBase64) {
                    logger.warning("UUID cache mismatch for ${player.name} - key changed, falling through to full auth")
                    SessionManager.clearUUIDCache(player.uniqueId)
                } else {
                    logger.info("UUID cache hit for ${player.name} - skipping authorized_keys check")
                    val challenge = Handshake.generateChallenge()
                    val serverSig = Handshake.sign(challenge, keyManager.privateKey)
                    SessionManager.addPending(player.uniqueId, PendingSession(clientPublicKey, challenge))
                    scheduleAuthChallengeTimeout(player)
                    sendAuthChallenge(player, challenge, serverSig)
                    return
                }
            }
        }

        if (AuthorizedKeys.isAuthorized(clientPublicKeyBase64)) {
            val challenge = Handshake.generateChallenge()
            val serverSig = Handshake.sign(challenge, keyManager.privateKey)
            SessionManager.addPending(player.uniqueId, PendingSession(clientPublicKey, challenge))
            scheduleAuthChallengeTimeout(player)
            sendAuthChallenge(player, challenge, serverSig)
            logger.info("Authorized key recognized for ${player.name} - sending challenge")
        } else {
            if (AuthorizedKeys.count() >= VenusConfig.maxUsers) {
                logger.warning("${player.name} tried to connect to Venus but max_users (${VenusConfig.maxUsers}) reached - rejecting.")
                return
            }

            SessionManager.addPendingApproval(
                player.uniqueId,
                PendingApproval(clientPublicKey, clientPublicKeyBase64),
            )
            logger.info("${player.name} wants to connect to Venus. Type 'venus allow' or 'venus deny'")

            server.scheduler.runTaskLater(
                this,
                Runnable {
                    if (SessionManager.getPendingApproval(player.uniqueId) != null) {
                        SessionManager.removePendingApproval(player.uniqueId)
                        logger.info("Venus request from ${player.name} timed out.")
                    }
                },
                (VenusConfig.sessionTimeoutSeconds * 20L),
            )
        }
    }

    private fun sendAuthChallenge(
        player: Player,
        challenge: ByteArray,
        serverSig: ByteArray,
    ) {
        val challengeB64 = Base64.getEncoder().encodeToString(challenge)
        val sigB64 = Base64.getEncoder().encodeToString(serverSig)
        val payload =
            json.encodeToString(
                AuthChallengePacket.serializer(),
                AuthChallengePacket(type = "auth_challenge", challenge = challengeB64, serverSignature = sigB64),
            ).toByteArray(Charsets.UTF_8)
        val id = Identifier.fromNamespaceAndPath("venus", "auth")
        val packet = ClientboundCustomPayloadPacket(DiscardedPayload(id, payload))
        (player as CraftPlayer).handle.connection.send(packet)
    }

    private fun handleAuthResponse(
        player: Player,
        data: String,
    ) {
        val packet = try {
            json.decodeFromString<AuthResponsePacket>(data)
        } catch (e: SerializationException) {
            logger.warning("Malformed auth response packet from ${player.name}: ${e.message}")
            return
        }
        if (packet.type != "auth_response") {
            logger.warning("Invalid auth response packet type from ${player.name}: ${packet.type}")
            return
        }
        val challengeB64 = packet.challenge
        val clientSigB64 = packet.clientSignature

        val pending = SessionManager.getPending(player.uniqueId)
        if (pending == null) {
            logger.warning("No pending session for ${player.name}")
            return
        }

        val challenge = try {
            Base64.getDecoder().decode(challengeB64)
        } catch (_: IllegalArgumentException) {
            logger.warning("Invalid Base64 in auth challenge from ${player.name}")
            SessionManager.removePending(player.uniqueId)
            return
        }
        val clientSig = try {
            Base64.getDecoder().decode(clientSigB64)
        } catch (_: IllegalArgumentException) {
            logger.warning("Invalid Base64 in auth signature from ${player.name}")
            SessionManager.removePending(player.uniqueId)
            return
        }

        if (!challenge.contentEquals(pending.challenge)) {
            logger.warning("Challenge mismatch from ${player.name}")
            SessionManager.removePending(player.uniqueId)
            return
        }

        if (!Handshake.verify(challenge, clientSig, pending.clientPublicKey)) {
            logger.warning("Invalid signature from ${player.name} - rejecting")
            SessionManager.removePending(player.uniqueId)
            return
        }

        SessionManager.removePending(player.uniqueId)
        sessionTimeoutTasks.remove(player.uniqueId)?.cancel()
        SessionManager.activate(player.uniqueId, pending.clientPublicKey)

        if (server.onlineMode && VenusConfig.cacheVerifiedUuid) {
            SessionManager.cacheUUID(
                player.uniqueId,
                Base64.getEncoder().encodeToString(pending.clientPublicKey.encoded),
            )
        }

        logger.info("${player.name} authenticated successfully!")
        sendReady(player)
    }

    private fun handleCmdPacket(
        player: Player,
        data: String,
    ) {
        if (!SessionManager.isActive(player.uniqueId)) {
            logger.warning("${player.name} sent cmd packet without active session - ignoring")
            return
        }

        val jsonElement = try {
            json.parseToJsonElement(data)
        } catch (e: SerializationException) {
            logger.warning("${player.name} sent malformed cmd packet: ${e.message}")
            return
        }
        val type =
            jsonElement.jsonObject["type"]?.jsonPrimitive?.content
                ?: run {
                    logger.warning("${player.name} sent cmd packet without type field")
                    return
                }

        when (type) {
            "console_cmd" -> {
                val packet = try {
                    json.decodeFromString<ConsoleCmdPacket>(data)
                } catch (e: SerializationException) {
                    logger.warning("${player.name} sent malformed console_cmd packet: ${e.message}")
                    return
                }
                logger.info("${player.name} executed console command: ${packet.command}")
                val lines = mutableListOf<String>()
                val sender =
                    server.createCommandSender { component ->
                        lines.add(PlainTextComponentSerializer.plainText().serialize(component))
                    }
                server.dispatchCommand(sender, packet.command)
                if (lines.isNotEmpty()) {
                    val response =
                        json.encodeToString(
                            CmdResponsePacket.serializer(),
                            CmdResponsePacket(type = "cmd_response", command = packet.command, lines = lines),
                        )
                    sendDataToPlayer(player, response)
                }
            }

            "stat_subscribe" -> {
                val packet = try {
                    json.decodeFromString<StatSubscribePacket>(data)
                } catch (e: SerializationException) {
                    logger.warning("${player.name} sent malformed stat_subscribe packet: ${e.message}")
                    return
                }
                logger.info("${player.name} subscribed to stats: ${packet.stats} every ${packet.intervalSeconds}s")
                StatSubscriptionManager.subscribe(player.uniqueId, packet.stats, packet.intervalSeconds, this) { statsJson ->
                    sendDataToPlayer(player, statsJson)
                }
            }

            "stat_get" -> {
                val packet = try {
                    json.decodeFromString<StatGetPacket>(data)
                } catch (e: SerializationException) {
                    logger.warning("${player.name} sent malformed stat_get packet: ${e.message}")
                    return
                }
                val statsJson = StatsCollector.buildStatsJson(server, packet.stats)
                sendDataToPlayer(player, statsJson)
                logger.info("${player.name} requested one-time stats: ${packet.stats}")
            }

            else -> {
                logger.warning("${player.name} sent unknown cmd packet type: $type")
            }
        }
    }

    private fun sendDataToPlayer(
        player: Player,
        data: String,
    ) {
        val id = Identifier.fromNamespaceAndPath("venus", "data")
        val packet = ClientboundCustomPayloadPacket(DiscardedPayload(id, data.toByteArray(Charsets.UTF_8)))
        (player as CraftPlayer).handle.connection.send(packet)
    }

    fun sendReady(player: Player) {
        val data =
            json.encodeToString(
                ReadyPacket.serializer(),
                ReadyPacket(type = "ready"),
            )
        val id = Identifier.fromNamespaceAndPath("venus", "ready")
        val packet = ClientboundCustomPayloadPacket(DiscardedPayload(id, data.toByteArray(Charsets.UTF_8)))
        (player as CraftPlayer).handle.connection.send(packet)
        logger.info("Sent venus:ready to ${player.name}")
    }

    fun sendAuthChallengeTo(
        player: Player,
        challenge: ByteArray,
        serverSig: ByteArray,
    ) {
        sendAuthChallenge(player, challenge, serverSig)
    }

    private fun scheduleAuthChallengeTimeout(player: Player) {
        val uuid = player.uniqueId
        sessionTimeoutTasks[uuid]?.cancel()
        sessionTimeoutTasks[uuid] = server.scheduler.runTaskLater(
            this,
            Runnable {
                if (SessionManager.getPending(uuid) != null) {
                    SessionManager.removePending(uuid)
                    logger.info("Auth challenge expired for ${player.name}")
                }
                sessionTimeoutTasks.remove(uuid)
            },
            (VenusConfig.sessionTimeoutSeconds * 20L),
        )
    }
}
