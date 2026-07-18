package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.auth.PendingApproval
import dev.ilgax.venus.auth.PendingSession
import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.AuthResponsePacket
import dev.ilgax.venus.protocol.ClientKeyPacket
import dev.ilgax.venus.protocol.ErrorPacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendAuthHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var platform: BackendPlatform
    private lateinit var keyManager: KeyManager
    private lateinit var subscriptions: BackendStatSubscriptionManager
    private lateinit var sessionManager: SessionManager
    private lateinit var authHandler: BackendAuthHandler
    private lateinit var scheduler: BackendScheduler
    private lateinit var logger: BackendLogger
    private lateinit var task: BackendTask

    private val serverKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val clientKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    @Before
    fun setup() {
        platform = mockk(relaxed = true)
        keyManager = mockk(relaxed = true)
        subscriptions = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        sessionManager = SessionManager()

        every { keyManager.publicKey } returns serverKeyPair.public
        every { keyManager.privateKey } returns serverKeyPair.private
        every { keyManager.publicKeyBase64 } returns
            Base64.getEncoder().encodeToString(serverKeyPair.public.encoded)
        every { platform.logger } returns logger
        every { platform.scheduler } returns scheduler
        every { platform.config } returns
            BackendConfig(maxUsers = 1, authTimeoutSeconds = 60)
        task = mockk(relaxed = true)
        every { scheduler.runLater(any(), any()) } returns task

        authHandler = BackendAuthHandler(platform, json, keyManager, subscriptions, sessionManager)
        mockkObject(AuthorizedKeys)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `handleHello sends server key packet`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        authHandler.handleHello(player)

        verify { platform.sendKey(player, any()) }
    }

    @Test
    fun `handleClientKey with authorized key starts challenge`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val clientKeyB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        every { AuthorizedKeys.isAuthorized(clientKeyB64) } returns true
        every { AuthorizedKeys.count() } returns 0
        val data = json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", clientKeyB64))

        authHandler.handleClientKey(player, data)

        verify { platform.sendAuth(player, any()) }
        assertTrue(sessionManager.getPending(player.uuid) != null)
    }

    @Test
    fun `handleClientKey with unauthorized key creates pending approval`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val clientKeyB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        every { AuthorizedKeys.isAuthorized(clientKeyB64) } returns false
        every { AuthorizedKeys.count() } returns 0
        val data = json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", clientKeyB64))

        authHandler.handleClientKey(player, data)

        assertTrue(sessionManager.getPendingApproval(player.uuid) != null)
        verify { logger.info(match<String> { it.contains("Venus connect request") }) }
    }

    @Test
    fun `handleClientKey rejects when max users reached`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val clientKeyB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        every { AuthorizedKeys.isAuthorized(clientKeyB64) } returns false
        every { AuthorizedKeys.count() } returns 1
        val data = json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", clientKeyB64))

        authHandler.handleClientKey(player, data)

        verify { platform.sendError(player, match<String> { it.contains("auth_max_users") }) }
    }

    @Test
    fun `handleClientKey rejects invalid base64 key`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val data = json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", "not-valid-base64!!!"))

        authHandler.handleClientKey(player, data)

        verify { logger.warning(match<String> { it.contains("Invalid client key") }) }
        verify(exactly = 0) { platform.sendAuth(any(), any()) }
    }

    @Test
    fun `handleAuthResponse with valid signature activates session`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val challenge = Handshake.generateChallenge()
        sessionManager.addPending(
            player.uuid,
            PendingSession(clientKeyPair.public, challenge),
        )
        every { AuthorizedKeys.isAuthorized(Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)) } returns true

        val clientSig =
            Handshake.signTranscript(
                serverKeyPair.public,
                clientKeyPair.public,
                challenge,
                Handshake.ROLE_CLIENT,
                clientKeyPair.private,
            )
        val data =
            json.encodeToString(
                AuthResponsePacket.serializer(),
                AuthResponsePacket(
                    "auth_response",
                    Base64.getEncoder().encodeToString(challenge),
                    Base64.getEncoder().encodeToString(clientSig),
                ),
            )

        authHandler.handleAuthResponse(player, data)

        assertTrue(sessionManager.isActive(player.uuid))
        verify { platform.sendReady(player, any()) }
    }

    @Test
    fun `V34 revoked key cannot complete pending challenge`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val challenge = Handshake.generateChallenge()
        val clientKeyBase64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        sessionManager.addPending(player.uuid, PendingSession(clientKeyPair.public, challenge))
        every { AuthorizedKeys.isAuthorized(clientKeyBase64) } returns false
        val clientSig =
            Handshake.signTranscript(
                serverKeyPair.public,
                clientKeyPair.public,
                challenge,
                Handshake.ROLE_CLIENT,
                clientKeyPair.private,
            )
        val data =
            json.encodeToString(
                AuthResponsePacket.serializer(),
                AuthResponsePacket(
                    "auth_response",
                    Base64.getEncoder().encodeToString(challenge),
                    Base64.getEncoder().encodeToString(clientSig),
                ),
            )

        authHandler.handleAuthResponse(player, data)

        assertFalse(sessionManager.isActive(player.uuid))
        assertTrue(sessionManager.getPending(player.uuid) == null)
        verify { platform.sendError(player, match<String> { it.contains("auth_denied") }) }
        verify(exactly = 0) { platform.sendReady(player, any()) }
    }

    @Test
    fun `handleAuthResponse with invalid signature rejects and removes pending`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val challenge = Handshake.generateChallenge()
        sessionManager.addPending(
            player.uuid,
            PendingSession(clientKeyPair.public, challenge),
        )

        val badSig = ByteArray(64)
        val data =
            json.encodeToString(
                AuthResponsePacket.serializer(),
                AuthResponsePacket(
                    "auth_response",
                    Base64.getEncoder().encodeToString(challenge),
                    Base64.getEncoder().encodeToString(badSig),
                ),
            )

        authHandler.handleAuthResponse(player, data)

        verify { platform.sendError(player, match<String> { it.contains("auth_invalid_response") }) }
        assertTrue(sessionManager.getPending(player.uuid) == null)
        assertTrue(!sessionManager.isActive(player.uuid))
    }

    @Test
    fun `handleAuthResponse with invalid signature cancels auth timeout task`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val clientKeyB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        every { AuthorizedKeys.isAuthorized(clientKeyB64) } returns true
        val keyData = json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", clientKeyB64))
        authHandler.handleClientKey(player, keyData)
        val challenge = sessionManager.getPending(player.uuid)!!.challenge
        val badSig = ByteArray(64)
        val response =
            json.encodeToString(
                AuthResponsePacket.serializer(),
                AuthResponsePacket(
                    "auth_response",
                    Base64.getEncoder().encodeToString(challenge),
                    Base64.getEncoder().encodeToString(badSig),
                ),
            )

        authHandler.handleAuthResponse(player, response)

        verify { task.cancel() }
        assertTrue(sessionManager.getPending(player.uuid) == null)
    }

    @Test
    fun `handleAuthResponse with challenge mismatch rejects`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val challenge = Handshake.generateChallenge()
        sessionManager.addPending(
            player.uuid,
            PendingSession(clientKeyPair.public, challenge),
        )

        val otherChallenge = Handshake.generateChallenge()
        val clientSig =
            Handshake.signTranscript(
                serverKeyPair.public,
                clientKeyPair.public,
                otherChallenge,
                Handshake.ROLE_CLIENT,
                clientKeyPair.private,
            )
        val data =
            json.encodeToString(
                AuthResponsePacket.serializer(),
                AuthResponsePacket(
                    "auth_response",
                    Base64.getEncoder().encodeToString(otherChallenge),
                    Base64.getEncoder().encodeToString(clientSig),
                ),
            )

        authHandler.handleAuthResponse(player, data)

        verify { platform.sendError(player, match<String> { it.contains("auth_invalid_response") }) }
        assertTrue(!sessionManager.isActive(player.uuid))
    }

    @Test
    fun `handleAuthResponse with no pending session logs warning`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val data =
            json.encodeToString(
                AuthResponsePacket.serializer(),
                AuthResponsePacket("auth_response", "dGVzdA==", "dGVzdA=="),
            )

        authHandler.handleAuthResponse(player, data)

        verify { logger.warning(match<String> { it.contains("No pending session") }) }
    }

    @Test
    fun `handleAuthResponse with malformed JSON sends error`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        authHandler.handleAuthResponse(player, """{"type":"auth_response"""")

        verify { platform.sendError(player, match<String> { it.contains("auth_invalid_response") }) }
    }

    @Test
    fun `onPlayerQuit deactivates active session and cancels subscriptions`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        sessionManager.activate(player.uuid, clientKeyPair.public)

        authHandler.onPlayerQuit(player)

        assertTrue(!sessionManager.isActive(player.uuid))
        verify { subscriptions.cancel(player.uuid) }
    }

    @Test
    fun `onPlayerQuit cleans up pending sessions and approvals`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        sessionManager.addPending(player.uuid, PendingSession(clientKeyPair.public, ByteArray(32)))
        sessionManager.addPendingApproval(player.uuid, PendingApproval(clientKeyPair.public, "key"))

        authHandler.onPlayerQuit(player)

        assertTrue(sessionManager.getPending(player.uuid) == null)
        assertTrue(sessionManager.getPendingApproval(player.uuid) == null)
    }

    @Test
    fun `handleClientError logs mitm_key_mismatch`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val data = json.encodeToString(ErrorPacket.serializer(), ErrorPacket("error", "mitm_key_mismatch"))

        authHandler.handleClientError(player, data)

        verify { logger.warning(match<String> { it.contains("server key mismatch") }) }
    }

    @Test
    fun `handleClientError logs mitm_sig_fail`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val data = json.encodeToString(ErrorPacket.serializer(), ErrorPacket("error", "mitm_sig_fail"))

        authHandler.handleClientError(player, data)

        verify { logger.warning(match<String> { it.contains("signature verification failed") }) }
    }

    @Test
    fun `handleHello clears in-flight pending session and restarts flow`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val challenge = Handshake.generateChallenge()
        sessionManager.addPending(player.uuid, PendingSession(clientKeyPair.public, challenge))

        authHandler.handleHello(player)

        assertTrue(sessionManager.getPending(player.uuid) == null)
        verify { platform.sendKey(player, any()) }
    }

    @Test
    fun `client key validation rejects malformed type and blank key`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        authHandler.handleClientKey(player, "not-json")
        authHandler.handleClientKey(
            player,
            json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("wrong_type", "key")),
        )
        authHandler.handleClientKey(
            player,
            json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", "   ")),
        )

        verify { logger.warning(match<String> { it.contains("Malformed client key packet") }) }
        verify { logger.warning(match<String> { it.contains("Invalid client key packet type") }) }
        verify { logger.warning(match<String> { it.contains("Empty client key") }) }
        verify(exactly = 0) { platform.sendAuth(any(), any()) }
    }

    @Test
    fun `auth response validation rejects wrong type and invalid base64 fields`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val challenge = Handshake.generateChallenge()
        sessionManager.addPending(player.uuid, PendingSession(clientKeyPair.public, challenge))

        authHandler.handleAuthResponse(
            player,
            json.encodeToString(AuthResponsePacket.serializer(), AuthResponsePacket("wrong", "key", "signature")),
        )
        sessionManager.addPending(player.uuid, PendingSession(clientKeyPair.public, challenge))
        authHandler.handleAuthResponse(
            player,
            json.encodeToString(AuthResponsePacket.serializer(), AuthResponsePacket("auth_response", "%%%", "signature")),
        )
        sessionManager.addPending(player.uuid, PendingSession(clientKeyPair.public, challenge))
        authHandler.handleAuthResponse(
            player,
            json.encodeToString(
                AuthResponsePacket.serializer(),
                AuthResponsePacket("auth_response", Base64.getEncoder().encodeToString(challenge), "%%%"),
            ),
        )

        verify(exactly = 3) { platform.sendError(player, match<String> { it.contains("auth_invalid_response") }) }
        assertTrue(sessionManager.getPending(player.uuid) == null)
    }

    @Test
    fun `client error validation handles malformed wrong type and sanitized generic reason`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

        authHandler.handleClientError(player, "not-json")
        authHandler.handleClientError(
            player,
            json.encodeToString(ErrorPacket.serializer(), ErrorPacket("wrong", "reason")),
        )
        authHandler.handleClientError(
            player,
            json.encodeToString(ErrorPacket.serializer(), ErrorPacket("error", "line\nbreak")),
        )

        verify { logger.warning(match<String> { it.contains("malformed error packet") }) }
        verify { logger.warning(match<String> { it.contains("invalid error packet type") }) }
        verify { logger.warning(match<String> { it.contains("line\\nbreak") }) }
    }

    @Test
    fun `scheduled challenge timeout removes pending session`() {
        val callback = slot<() -> Unit>()
        every { scheduler.runLater(any(), capture(callback)) } returns task
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val clientKeyB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        every { AuthorizedKeys.isAuthorized(clientKeyB64) } returns true

        authHandler.handleClientKey(
            player,
            json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", clientKeyB64)),
        )
        callback.captured()

        assertTrue(sessionManager.getPending(player.uuid) == null)
        verify { logger.info(match<String> { it.contains("Auth challenge expired") }) }
    }

    @Test
    fun `scheduled approval timeout notifies connected player`() {
        val callback = slot<() -> Unit>()
        every { scheduler.runLater(any(), capture(callback)) } returns task
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val clientKeyB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        every { AuthorizedKeys.isAuthorized(clientKeyB64) } returns false
        every { AuthorizedKeys.count() } returns 0
        every { platform.player(player.uuid) } returns player

        authHandler.handleClientKey(
            player,
            json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", clientKeyB64)),
        )
        callback.captured()

        assertTrue(sessionManager.getPendingApproval(player.uuid) == null)
        verify { platform.sendError(player, match<String> { it.contains("auth_timeout") }) }
        verify { logger.info(match<String> { it.contains("timed out") }) }
    }

    @Test
    fun `notification and cancellation helpers send reasons and cancel tasks`() {
        val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")
        val clientKeyB64 = Base64.getEncoder().encodeToString(clientKeyPair.public.encoded)
        every { AuthorizedKeys.isAuthorized(clientKeyB64) } returns true
        authHandler.handleClientKey(
            player,
            json.encodeToString(ClientKeyPacket.serializer(), ClientKeyPacket("client_key", clientKeyB64)),
        )

        authHandler.notifyDenied(player)
        authHandler.notifyMaxUsers(player)
        authHandler.cancelPendingAuth(player.uuid)
        authHandler.cancelPendingApproval(player.uuid)
        authHandler.cancelAllTimeouts()

        verify { platform.sendError(player, match<String> { it.contains("auth_denied") }) }
        verify { platform.sendError(player, match<String> { it.contains("auth_max_users") }) }
        verify(atLeast = 1) { task.cancel() }
    }
}
