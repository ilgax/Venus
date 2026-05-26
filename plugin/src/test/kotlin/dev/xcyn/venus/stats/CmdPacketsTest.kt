package dev.xcyn.venus.stats

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CmdPacketsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `StatSubscribePacket serialization roundtrip`() {
        val packet =
            StatSubscribePacket(
                type = "stat_subscribe",
                intervalSeconds = 5,
                stats = listOf("tps", "ram", "mspt", "uptime"),
            )
        val encoded = json.encodeToString(StatSubscribePacket.serializer(), packet)
        val decoded = json.decodeFromString<StatSubscribePacket>(encoded)
        assertEquals(packet.type, decoded.type)
        assertEquals(packet.intervalSeconds, decoded.intervalSeconds)
        assertEquals(packet.stats, decoded.stats)
    }

    @Test
    fun `StatSubscribePacket defaults are applied when fields missing`() {
        val jsonStr = """{"type":"stat_subscribe"}"""
        val decoded = json.decodeFromString<StatSubscribePacket>(jsonStr)
        assertEquals("stat_subscribe", decoded.type)
        assertEquals(2, decoded.intervalSeconds)
        assertEquals(listOf("tps", "ram"), decoded.stats)
    }

    @Test
    fun `interval_seconds JSON key maps correctly`() {
        val jsonStr = """{"type":"stat_subscribe","interval_seconds":10,"stats":["tps"]}"""
        val decoded = json.decodeFromString<StatSubscribePacket>(jsonStr)
        assertEquals(10, decoded.intervalSeconds)
    }

    @Test
    fun `StatSubscribePacket partial defaults`() {
        val jsonStr = """{"type":"stat_subscribe","stats":["mspt"]}"""
        val decoded = json.decodeFromString<StatSubscribePacket>(jsonStr)
        assertEquals("stat_subscribe", decoded.type)
        assertEquals(2, decoded.intervalSeconds)
        assertEquals(listOf("mspt"), decoded.stats)
    }

    @Test
    fun `StatGetPacket serialization roundtrip`() {
        val packet = StatGetPacket(type = "stat_get", stats = listOf("tps", "ram"))
        val encoded = json.encodeToString(StatGetPacket.serializer(), packet)
        val decoded = json.decodeFromString<StatGetPacket>(encoded)
        assertEquals(packet.type, decoded.type)
        assertEquals(packet.stats, decoded.stats)
    }

    @Test
    fun `StatGetPacket defaults when fields missing`() {
        val jsonStr = """{"type":"stat_get"}"""
        val decoded = json.decodeFromString<StatGetPacket>(jsonStr)
        assertEquals("stat_get", decoded.type)
        assertEquals(listOf("tps", "ram"), decoded.stats)
    }

    @Test
    fun `ConsoleCmdPacket serialization roundtrip`() {
        val packet = ConsoleCmdPacket(type = "console_cmd", command = "say hello")
        val encoded = json.encodeToString(ConsoleCmdPacket.serializer(), packet)
        val decoded = json.decodeFromString<ConsoleCmdPacket>(encoded)
        assertEquals(packet.type, decoded.type)
        assertEquals(packet.command, decoded.command)
    }

    @Test
    fun `ConsoleCmdPacket with special characters in command`() {
        val packet = ConsoleCmdPacket(type = "console_cmd", command = "kick player \"reason with spaces\"")
        val encoded = json.encodeToString(ConsoleCmdPacket.serializer(), packet)
        val decoded = json.decodeFromString<ConsoleCmdPacket>(encoded)
        assertEquals(packet.command, decoded.command)
    }
}
