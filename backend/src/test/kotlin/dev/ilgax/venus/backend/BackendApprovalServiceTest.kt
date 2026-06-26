package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.AuthorizedKeys
import dev.ilgax.venus.auth.Handshake
import dev.ilgax.venus.auth.PendingApproval
import dev.ilgax.venus.auth.SessionManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.AbstractMap
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals

class BackendApprovalServiceTest {
    private lateinit var platform: BackendPlatform
    private lateinit var authHandler: BackendAuthHandler
    private lateinit var sessionManager: SessionManager
    private lateinit var approvals: BackendApprovalService

    @Before
    fun setup() {
        platform = mockk(relaxed = true)
        authHandler = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        approvals = BackendApprovalService(platform, authHandler, sessionManager)

        mockkObject(AuthorizedKeys)
        every { platform.config } returns BackendConfig(maxUsers = 10, authTimeoutSeconds = 60)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `allowNextPending returns no pending message`() {
        every { sessionManager.getNextPendingApproval() } returns null

        val result = approvals.allowNextPending()

        assertEquals(BackendApprovalResult(false, "No pending Venus requests."), result)
    }

    @Test
    fun `allowNextPending removes offline player approval`() {
        val uuid = UUID.randomUUID()
        val approval = pendingApproval()
        every { sessionManager.getNextPendingApproval() } returnsMany
            listOf(AbstractMap.SimpleEntry(uuid, approval), null)
        every { platform.player(uuid) } returns null
        every { sessionManager.removePendingApproval(uuid) } returns approval

        val result = approvals.allowNextPending()

        assertEquals(BackendApprovalResult(false, "No pending Venus requests."), result)
        verify { sessionManager.removePendingApproval(uuid) }
    }

    @Test
    fun `allowNextPending skips offline player and authorizes next online player`() {
        val offlineUuid = UUID.randomUUID()
        val onlineUuid = UUID.randomUUID()
        val offlineApproval = pendingApproval()
        val onlineApproval = pendingApproval()
        val player = BackendPlayer(onlineUuid, "TestPlayer")
        every {
            sessionManager.getNextPendingApproval()
        } returnsMany
            listOf(
                AbstractMap.SimpleEntry(offlineUuid, offlineApproval),
                AbstractMap.SimpleEntry(onlineUuid, onlineApproval),
            )
        every { platform.player(offlineUuid) } returns null
        every { platform.player(onlineUuid) } returns player
        every { sessionManager.removePendingApproval(offlineUuid) } returns offlineApproval
        every { sessionManager.removePendingApproval(onlineUuid) } returns onlineApproval
        every { AuthorizedKeys.tryAuthorize(any(), any(), any()) } returns true
        every { authHandler.startApprovedChallenge(any(), any()) } just Runs
        every { platform.logger.info(any()) } just Runs

        val result = approvals.allowNextPending()

        val expected = "TestPlayer (key ${Handshake.fingerprint(onlineApproval.clientPublicKey)}) authorized."
        assertEquals(BackendApprovalResult(true, expected), result)
        verify { sessionManager.removePendingApproval(offlineUuid) }
        verify { sessionManager.removePendingApproval(onlineUuid) }
        verify { AuthorizedKeys.tryAuthorize(onlineApproval.clientPublicKeyBase64, "TestPlayer", 10) }
    }

    @Test
    fun `allowNextPending authorizes online player and starts challenge`() {
        val uuid = UUID.randomUUID()
        val approval = pendingApproval()
        val player = BackendPlayer(uuid, "TestPlayer")
        every { sessionManager.getNextPendingApproval() } returns AbstractMap.SimpleEntry(uuid, approval)
        every { platform.player(uuid) } returns player
        every { sessionManager.removePendingApproval(uuid) } returns approval
        every { AuthorizedKeys.tryAuthorize(any(), any(), any()) } returns true
        every { authHandler.startApprovedChallenge(any(), any()) } just Runs
        every { platform.logger.info(any()) } just Runs

        val result = approvals.allowNextPending()

        val expected = "TestPlayer (key ${Handshake.fingerprint(approval.clientPublicKey)}) authorized."
        assertEquals(BackendApprovalResult(true, expected), result)
        verify { AuthorizedKeys.tryAuthorize(approval.clientPublicKeyBase64, "TestPlayer", 10) }
        verify { authHandler.startApprovedChallenge(player, approval.clientPublicKey) }
    }

    @Test
    fun `allowNextPending rejects queued approval when max users reached`() {
        val uuid = UUID.randomUUID()
        val approval = pendingApproval()
        val player = BackendPlayer(uuid, "TestPlayer")
        every { platform.config } returns BackendConfig(maxUsers = 1, authTimeoutSeconds = 60)
        every { sessionManager.getNextPendingApproval() } returns AbstractMap.SimpleEntry(uuid, approval)
        every { platform.player(uuid) } returns player
        every { sessionManager.removePendingApproval(uuid) } returns approval
        every { AuthorizedKeys.tryAuthorize(any(), any(), any()) } returns false
        every { authHandler.notifyMaxUsers(player) } just Runs
        every { authHandler.cancelPendingApproval(uuid) } just Runs
        every { platform.logger.warning(any()) } just Runs

        val result = approvals.allowNextPending()

        assertEquals(
            BackendApprovalResult(false, "Cannot authorize TestPlayer: max_users (1) reached."),
            result,
        )
        verify { sessionManager.removePendingApproval(uuid) }
        verify { authHandler.cancelPendingApproval(uuid) }
        verify { authHandler.notifyMaxUsers(player) }
        verify(exactly = 0) { authHandler.startApprovedChallenge(any(), any()) }
    }

    @Test
    fun `deactivateSessionsForKey removes active sessions and invokes cleanup`() {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val uuid = UUID.randomUUID()
        val cleanupCalls = mutableListOf<UUID>()
        approvals = BackendApprovalService(platform, authHandler, sessionManager, cleanupCalls::add)
        every { sessionManager.deactivateByPublicKey(keyPair.public) } returns listOf(uuid)

        val count = approvals.deactivateSessionsForKey(publicKeyBase64)

        assertEquals(1, count)
        assertEquals(listOf(uuid), cleanupCalls)
    }

    @Test
    fun `denyNextPending returns no pending message`() {
        every { sessionManager.getNextPendingApproval() } returns null

        val result = approvals.denyNextPending()

        assertEquals(BackendApprovalResult(false, "No pending Venus requests."), result)
    }

    @Test
    fun `denyNextPending notifies online player`() {
        val uuid = UUID.randomUUID()
        val approval = pendingApproval()
        val player = BackendPlayer(uuid, "DeniedPlayer")
        every { sessionManager.getNextPendingApproval() } returns AbstractMap.SimpleEntry(uuid, approval)
        every { platform.player(uuid) } returns player
        every { sessionManager.removePendingApproval(uuid) } returns approval
        every { authHandler.notifyDenied(player) } just Runs

        val result = approvals.denyNextPending()

        val expected = "DeniedPlayer (key ${Handshake.fingerprint(approval.clientPublicKey)}) denied."
        assertEquals(BackendApprovalResult(true, expected), result)
        verify { authHandler.notifyDenied(player) }
    }

    @Test
    fun `denyNextPending skips offline player and denies next online player`() {
        val offlineUuid = UUID.randomUUID()
        val onlineUuid = UUID.randomUUID()
        val offlineApproval = pendingApproval()
        val onlineApproval = pendingApproval()
        val player = BackendPlayer(onlineUuid, "DeniedPlayer")
        every {
            sessionManager.getNextPendingApproval()
        } returnsMany
            listOf(
                AbstractMap.SimpleEntry(offlineUuid, offlineApproval),
                AbstractMap.SimpleEntry(onlineUuid, onlineApproval),
            )
        every { platform.player(offlineUuid) } returns null
        every { platform.player(onlineUuid) } returns player
        every { sessionManager.removePendingApproval(offlineUuid) } returns offlineApproval
        every { sessionManager.removePendingApproval(onlineUuid) } returns onlineApproval
        every { authHandler.notifyDenied(player) } just Runs

        val result = approvals.denyNextPending()

        val expected = "DeniedPlayer (key ${Handshake.fingerprint(onlineApproval.clientPublicKey)}) denied."
        assertEquals(BackendApprovalResult(true, expected), result)
        verify { sessionManager.removePendingApproval(offlineUuid) }
        verify { sessionManager.removePendingApproval(onlineUuid) }
        verify { authHandler.notifyDenied(player) }
    }

    private fun pendingApproval(): PendingApproval {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        return PendingApproval(
            clientPublicKey = keyPair.public,
            clientPublicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded),
        )
    }
}
