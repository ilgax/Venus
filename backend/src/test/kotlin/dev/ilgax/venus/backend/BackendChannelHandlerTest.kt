package dev.ilgax.venus.backend

import dev.ilgax.venus.auth.SessionManager
import dev.ilgax.venus.protocol.MAX_PACKET_SIZE
import dev.ilgax.venus.protocol.VenusChannels
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackendChannelHandlerTest {
    private val player = BackendPlayer(UUID.randomUUID(), "TestPlayer")

    @Test
    fun `registered inbound channels map to backend handlers`() {
        assertEquals(BackendIncomingChannel.HELLO, BackendIncomingChannel.fromChannel(VenusChannels.HELLO))
        assertEquals(BackendIncomingChannel.KEY, BackendIncomingChannel.fromChannel(VenusChannels.KEY))
        assertEquals(BackendIncomingChannel.AUTH, BackendIncomingChannel.fromChannel(VenusChannels.AUTH))
        assertEquals(BackendIncomingChannel.ERROR, BackendIncomingChannel.fromChannel(VenusChannels.ERROR))
        assertEquals(BackendIncomingChannel.CMD, BackendIncomingChannel.fromChannel(VenusChannels.CMD))
    }

    @Test
    fun `unknown inbound channel is ignored`() {
        assertNull(BackendIncomingChannel.fromChannel("venus:transfer"))
    }

    @Test
    fun `handle routes hello`() {
        val handler = createHandler(activeSession = false)

        handler.handle(VenusChannels.HELLO, player, "hello data")

        verify { handler.auth.handleHello(player) }
    }

    @Test
    fun `handle routes cmd`() {
        val handler = createHandler(activeSession = true)

        handler.handle(VenusChannels.CMD, player, "cmd data")

        verify { handler.router.handleCommand(player, "cmd data") }
    }

    @Test
    fun `oversized packet is ignored`() {
        val handler = createHandler(activeSession = false)
        val oversized = "x".repeat(MAX_PACKET_SIZE + 1)

        handler.handle(VenusChannels.HELLO, player, oversized)

        verify(exactly = 0) { handler.auth.handleHello(any()) }
    }

    @Test
    fun `multi-byte oversized packet is ignored by byte size`() {
        val handler = createHandler(activeSession = false)
        val oversized = "€".repeat(MAX_PACKET_SIZE / 2)

        handler.handle(VenusChannels.HELLO, player, oversized)

        verify(exactly = 0) { handler.auth.handleHello(any()) }
    }

    @Test
    fun `unknown channel is logged and ignored`() {
        val handler = createHandler(activeSession = false)

        handler.handle("venus:unknown", player, "data")

        verify(exactly = 0) { handler.auth.handleHello(any()) }
    }

    private fun createHandler(activeSession: Boolean): HandlerFixture {
        val auth = mockk<BackendAuthHandler>(relaxed = true)
        val router = mockk<BackendPacketRouter>(relaxed = true)
        val sessionManager = mockk<SessionManager>(relaxed = true)
        val logger = mockk<BackendLogger>(relaxed = true)
        every { sessionManager.isActive(player.uuid) } returns activeSession
        return HandlerFixture(
            auth = auth,
            router = router,
            handler = BackendChannelHandler(auth, router, sessionManager, logger),
        )
    }

    private data class HandlerFixture(
        val auth: BackendAuthHandler,
        val router: BackendPacketRouter,
        val handler: BackendChannelHandler,
    ) {
        fun handle(
            channel: String,
            player: BackendPlayer,
            data: String,
        ) = handler.handle(channel, player, data)
    }
}
