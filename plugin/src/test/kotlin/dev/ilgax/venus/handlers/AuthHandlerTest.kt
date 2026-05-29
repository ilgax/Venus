package dev.ilgax.venus.handlers

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.PendingSession
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.config.VenusConfig
import dev.ilgax.venus.protocol.AuthChallengePacket
import dev.ilgax.venus.protocol.AuthResponsePacket
import dev.ilgax.venus.protocol.ClientKeyPacket
import dev.ilgax.venus.protocol.ErrorPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import dev.ilgax.venus.stats.StatSubscriptionManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.util.Base64
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals

class AuthHandlerTest {
    private lateinit var plugin: JavaPlugin
    private lateinit var server: Server
    private lateinit var scheduler: BukkitScheduler
    private lateinit var logger: Logger
    private lateinit var keyManager: KeyManager
    private lateinit var player: Player
    private val uuid = UUID.randomUUID()
    private val playerName = "TestPlayer"

    private lateinit var sendKeyCalls: MutableList<String>
    private lateinit var sendAuthCalls: MutableList<String>
    private lateinit var sendReadyCalls: MutableList<String>
    private lateinit var sendErrorCalls: MutableList<String>

    private lateinit var handler: AuthHandler
    private lateinit var clientKeyPair: KeyPair
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        plugin = mockk(relaxed = true)
        server = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        keyManager = mockk(relaxed = true)
        player = mockk(relaxed = true)

        every { plugin.server } returns server
        every { plugin.logger } returns logger
        every { server.scheduler } returns scheduler
        every { player.uniqueId } returns uuid
        every { player.name } returns playerName
        every { server.onlineMode } returns true

        val serverKeyPair =
            java.security.KeyPairGenerator
                .getInstance("Ed25519")
                .generateKeyPair()
        every { keyManager.publicKeyBase64 } returns Base64.getEncoder().encodeToString(serverKeyPair.public.encoded)
        every { keyManager.privateKey } returns serverKeyPair.private

        clientKeyPair =
            java.security.KeyPairGenerator
                .getInstance("Ed25519")
                .generateKeyPair()

        sendKeyCalls = mutableListOf()
        sendAuthCalls = mutableListOf()
        sendReadyCalls = mutableListOf()
        sendErrorCalls = mutableListOf()

        mockkObject(SessionManager)
        mockkObject(AuthorizedKeys)
        mockkObject(VenusConfig)
        mockkObject(StatSubscriptionManager)

        every { VenusConfig.authTimeoutSeconds } returns 30
        every { VenusConfig.maxUsers } returns 10

        val task = mockk<BukkitTask>(relaxed = true)
        every { scheduler.runTaskLater(any(), any<Runnable>(), any()) } returns task

        every { SessionManager.removePending(any()) } returns null
        every { SessionManager.activate(any(), any()) } just Runs
        every { SessionManager.deactivate(any()) } just Runs
        every { SessionManager.getPending(any()) } returns null
        every { StatSubscriptionManager.cancel(any()) } just Runs

        handler =
            AuthHandler(
                plugin,
                json,
                keyManager,
                { _, data -> sendKeyCalls.add(data) },
                { _, data -> sendAuthCalls.add(data) },
                { _, data -> sendReadyCalls.add(data) },
                { _, data -> sendErrorCalls.add(data) },
            )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `handleHello sends server key`() {
        handler.handleHello(player)

        assertEquals(1, sendKeyCalls.size)
        val packet = json.decodeFromString<ServerKeyPacket>(sendKeyCalls[0])
        assertEquals("server_key", packet.type)
        assertEquals(keyManager.publicKeyBase64, packet.publicKey)
    }

    @Test
    fun `handleClientKey with authorized key sends challenge`() {
        val clientPubB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        val packet = ClientKeyPacket(type = "client_key", publicKey = clientPubB64)
        val data = Json.encodeToString(ClientKeyPacket.serializer(), packet)

        every { AuthorizedKeys.isAuthorized(clientPubB64) } returns true
        every { SessionManager.addPending(any(), any()) } just Runs

        handler.handleClientKey(player, data)

        assertEquals(1, sendAuthCalls.size)
        val authChallenge = json.decodeFromString<AuthChallengePacket>(sendAuthCalls[0])
        assertEquals("auth_challenge", authChallenge.type)
        verify { SessionManager.addPending(uuid, any()) }
    }

    @Test
    fun `handleClientKey with unauthorized key adds pending approval`() {
        val clientPubB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        val packet = ClientKeyPacket(type = "client_key", publicKey = clientPubB64)
        val data = Json.encodeToString(ClientKeyPacket.serializer(), packet)

        every { AuthorizedKeys.isAuthorized(clientPubB64) } returns false
        every { AuthorizedKeys.count() } returns 1
        every { SessionManager.addPendingApproval(any(), any()) } just Runs

        handler.handleClientKey(player, data)

        assertEquals(0, sendAuthCalls.size)
        verify { SessionManager.addPendingApproval(uuid, any()) }
    }

    @Test
    fun `handleClientKey with max users sends auth max users error`() {
        val clientPubB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        val packet = ClientKeyPacket(type = "client_key", publicKey = clientPubB64)
        val data = Json.encodeToString(ClientKeyPacket.serializer(), packet)

        every { AuthorizedKeys.isAuthorized(clientPubB64) } returns false
        every { AuthorizedKeys.count() } returns VenusConfig.maxUsers

        handler.handleClientKey(player, data)

        val errorPacket = json.decodeFromString<ErrorPacket>(sendErrorCalls.single())
        assertEquals("auth_max_users", errorPacket.reason)
    }

    @Test
    fun `handleAuthResponse successful response activates session`() {
        val challengeBytes = Handshake.generateChallenge()
        val pendingSession = PendingSession(clientKeyPair.public, challengeBytes)
        every { SessionManager.getPending(uuid) } returns pendingSession
        every { SessionManager.removePending(uuid) } returns null
        every { SessionManager.activate(uuid, any()) } just Runs

        val clientSigBytes = Handshake.sign(challengeBytes, clientKeyPair.private)
        val packet =
            AuthResponsePacket(
                type = "auth_response",
                challenge = Base64.getEncoder().encodeToString(challengeBytes),
                clientSignature = Base64.getEncoder().encodeToString(clientSigBytes),
            )
        val data = Json.encodeToString(AuthResponsePacket.serializer(), packet)

        handler.handleAuthResponse(player, data)

        assertEquals(1, sendReadyCalls.size)
        verify { SessionManager.activate(uuid, clientKeyPair.public) }
        verify { SessionManager.removePending(uuid) }
    }

    @Test
    fun `handleAuthResponse invalid signature sends auth invalid response error`() {
        val challengeBytes = Handshake.generateChallenge()
        val pendingSession = PendingSession(clientKeyPair.public, challengeBytes)
        every { SessionManager.getPending(uuid) } returns pendingSession
        every { SessionManager.removePending(uuid) } returns null

        val otherKeyPair =
            java.security.KeyPairGenerator
                .getInstance("Ed25519")
                .generateKeyPair()
        val clientSigBytes = Handshake.sign(challengeBytes, otherKeyPair.private)
        val packet =
            AuthResponsePacket(
                type = "auth_response",
                challenge = Base64.getEncoder().encodeToString(challengeBytes),
                clientSignature = Base64.getEncoder().encodeToString(clientSigBytes),
            )
        val data = Json.encodeToString(AuthResponsePacket.serializer(), packet)

        handler.handleAuthResponse(player, data)

        val errorPacket = json.decodeFromString<ErrorPacket>(sendErrorCalls.single())
        assertEquals("auth_invalid_response", errorPacket.reason)
        verify { SessionManager.removePending(uuid) }
    }

    @Test
    fun `notifyDenied sends auth denied error`() {
        handler.notifyDenied(player)

        val errorPacket = json.decodeFromString<ErrorPacket>(sendErrorCalls.single())
        assertEquals("auth_denied", errorPacket.reason)
    }

    @Test
    fun `handleClientError mitm_key_mismatch`() {
        val packet = ErrorPacket(type = "error", reason = "mitm_key_mismatch")
        val data = Json.encodeToString(ErrorPacket.serializer(), packet)

        handler.handleClientError(player, data)

        verify { logger.warning(match<String> { it.contains("mitm_key_mismatch") || it.contains("server key mismatch") }) }
    }

    @Test
    fun `onPlayerQuit deactivates session`() {
        every { SessionManager.isActive(uuid) } returns true

        handler.onPlayerQuit(player)

        verify { SessionManager.deactivate(uuid) }
        verify { StatSubscriptionManager.cancel(uuid) }
    }
}
