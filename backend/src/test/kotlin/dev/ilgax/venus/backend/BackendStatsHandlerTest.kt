package dev.ilgax.venus.backend

import dev.ilgax.venus.protocol.StatGetPacket
import dev.ilgax.venus.protocol.StatSubscribePacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test

class BackendStatsHandlerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val player = BackendPlayer(UUID.randomUUID(), "Admin")
    private val platform = mockk<BackendPlatform>(relaxed = true)
    private val subscriptions = mockk<BackendStatSubscriptionManager>(relaxed = true)
    private val handler = BackendStatsHandler(platform, json, subscriptions)

    @Test
    fun `subscribe forwards requested stats and interval`() {
        val packet = StatSubscribePacket("stat_subscribe", 5, listOf("tps", "ram"))

        handler.handleSubscribe(player, json.encodeToString(StatSubscribePacket.serializer(), packet))

        verify { subscriptions.subscribe(player.uuid, listOf("tps", "ram"), 5) }
    }

    @Test
    fun `malformed subscribe is rejected`() {
        handler.handleSubscribe(player, """{"type":"stat_subscribe","stats":"bad"}""")

        verify(exactly = 0) { subscriptions.subscribe(any(), any(), any()) }
        verify { platform.logger.warning(match { it.contains("malformed stat_subscribe") }) }
    }

    @Test
    fun `get sends the requested stats snapshot`() {
        val packet = StatGetPacket("stat_get", listOf("uptime", "players"))
        every { platform.buildStatsJson(packet.stats) } returns "snapshot"

        handler.handleGet(player, json.encodeToString(StatGetPacket.serializer(), packet))

        verify { platform.sendData(player, "snapshot") }
    }

    @Test
    fun `malformed get is rejected`() {
        handler.handleGet(player, """{"type":"stat_get","stats":"bad"}""")

        verify(exactly = 0) { platform.buildStatsJson(any()) }
        verify(exactly = 0) { platform.sendData(any(), any()) }
        verify { platform.logger.warning(match { it.contains("malformed stat_get") }) }
    }
}
