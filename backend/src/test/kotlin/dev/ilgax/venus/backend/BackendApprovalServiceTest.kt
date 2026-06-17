package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.AuthorizedKeys
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
    private lateinit var approvals: BackendApprovalService

    @Before
    fun setup() {
        platform = mockk(relaxed = true)
        authHandler = mockk(relaxed = true)
        approvals = BackendApprovalService(platform, authHandler)

        mockkObject(SessionManager)
        mockkObject(AuthorizedKeys)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `allowNextPending returns no pending message`() {
        every { SessionManager.getNextPendingApproval() } returns null

        val result = approvals.allowNextPending()

        assertEquals(BackendApprovalResult(false, "No pending Venus requests."), result)
    }

    @Test
    fun `allowNextPending removes offline player approval`() {
        val uuid = UUID.randomUUID()
        val approval = pendingApproval()
        every { SessionManager.getNextPendingApproval() } returnsMany listOf(AbstractMap.SimpleEntry(uuid, approval), null)
        every { platform.player(uuid) } returns null
        every { SessionManager.removePendingApproval(uuid) } returns approval

        val result = approvals.allowNextPending()

        assertEquals(BackendApprovalResult(false, "No pending Venus requests."), result)
        verify { SessionManager.removePendingApproval(uuid) }
    }

    @Test
    fun `allowNextPending skips offline player and authorizes next online player`() {
        val offlineUuid = UUID.randomUUID()
        val onlineUuid = UUID.randomUUID()
        val offlineApproval = pendingApproval()
        val onlineApproval = pendingApproval()
        val player = BackendPlayer(onlineUuid, "TestPlayer")
        every {
            SessionManager.getNextPendingApproval()
        } returnsMany listOf(
            AbstractMap.SimpleEntry(offlineUuid, offlineApproval),
            AbstractMap.SimpleEntry(onlineUuid, onlineApproval),
        )
        every { platform.player(offlineUuid) } returns null
        every { platform.player(onlineUuid) } returns player
        every { SessionManager.removePendingApproval(offlineUuid) } returns offlineApproval
        every { SessionManager.removePendingApproval(onlineUuid) } returns onlineApproval
        every { AuthorizedKeys.authorize(any(), any()) } just Runs
        every { authHandler.startApprovedChallenge(any(), any()) } just Runs
        every { platform.logger.info(any()) } just Runs

        val result = approvals.allowNextPending()

        assertEquals(BackendApprovalResult(true, "TestPlayer authorized."), result)
        verify { SessionManager.removePendingApproval(offlineUuid) }
        verify { SessionManager.removePendingApproval(onlineUuid) }
        verify { AuthorizedKeys.authorize(onlineApproval.clientPublicKeyBase64, "TestPlayer") }
    }

    @Test
    fun `allowNextPending authorizes online player and starts challenge`() {
        val uuid = UUID.randomUUID()
        val approval = pendingApproval()
        val player = BackendPlayer(uuid, "TestPlayer")
        every { SessionManager.getNextPendingApproval() } returns AbstractMap.SimpleEntry(uuid, approval)
        every { platform.player(uuid) } returns player
        every { SessionManager.removePendingApproval(uuid) } returns approval
        every { AuthorizedKeys.authorize(any(), any()) } just Runs
        every { authHandler.startApprovedChallenge(any(), any()) } just Runs
        every { platform.logger.info(any()) } just Runs

        val result = approvals.allowNextPending()

        assertEquals(BackendApprovalResult(true, "TestPlayer authorized."), result)
        verify { AuthorizedKeys.authorize(approval.clientPublicKeyBase64, "TestPlayer") }
        verify { authHandler.startApprovedChallenge(player, approval.clientPublicKey) }
    }

    @Test
    fun `denyNextPending returns no pending message`() {
        every { SessionManager.getNextPendingApproval() } returns null

        val result = approvals.denyNextPending()

        assertEquals(BackendApprovalResult(false, "No pending Venus requests."), result)
    }

    @Test
    fun `denyNextPending notifies online player`() {
        val uuid = UUID.randomUUID()
        val approval = pendingApproval()
        val player = BackendPlayer(uuid, "DeniedPlayer")
        every { SessionManager.getNextPendingApproval() } returns AbstractMap.SimpleEntry(uuid, approval)
        every { platform.player(uuid) } returns player
        every { SessionManager.removePendingApproval(uuid) } returns approval
        every { authHandler.notifyDenied(player) } just Runs

        val result = approvals.denyNextPending()

        assertEquals(BackendApprovalResult(true, "DeniedPlayer denied."), result)
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
            SessionManager.getNextPendingApproval()
        } returnsMany listOf(
            AbstractMap.SimpleEntry(offlineUuid, offlineApproval),
            AbstractMap.SimpleEntry(onlineUuid, onlineApproval),
        )
        every { platform.player(offlineUuid) } returns null
        every { platform.player(onlineUuid) } returns player
        every { SessionManager.removePendingApproval(offlineUuid) } returns offlineApproval
        every { SessionManager.removePendingApproval(onlineUuid) } returns onlineApproval
        every { authHandler.notifyDenied(player) } just Runs

        val result = approvals.denyNextPending()

        assertEquals(BackendApprovalResult(true, "DeniedPlayer denied."), result)
        verify { SessionManager.removePendingApproval(offlineUuid) }
        verify { SessionManager.removePendingApproval(onlineUuid) }
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
