package dev.ilgax.venus.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketsTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `channels define protocol transport names`() {
        assertEquals("venus:hello", VenusChannels.HELLO)
        assertEquals("venus:key", VenusChannels.KEY)
        assertEquals("venus:auth", VenusChannels.AUTH)
        assertEquals("venus:ready", VenusChannels.READY)
        assertEquals("venus:data", VenusChannels.DATA)
        assertEquals("venus:cmd", VenusChannels.CMD)
        assertEquals("venus:transfer", VenusChannels.TRANSFER)
        assertEquals("venus:error", VenusChannels.ERROR)
    }

    @Test
    fun `auth challenge encodes documented json field names`() {
        val encoded =
            json.encodeToString(
                AuthChallengePacket(type = "auth_challenge", challenge = "nonce", serverSignature = "signature"),
            )

        assertEquals(
            """{"type":"auth_challenge","challenge":"nonce","server_sig":"signature"}""",
            encoded,
        )
    }

    @Test
    fun `key auth response and ready packets use typed json envelopes`() {
        assertEquals(
            """{"type":"server_key","public_key":"server-public"}""",
            json.encodeToString(ServerKeyPacket(type = "server_key", publicKey = "server-public")),
        )
        assertEquals(
            """{"type":"client_key","public_key":"client-public"}""",
            json.encodeToString(ClientKeyPacket(type = "client_key", publicKey = "client-public")),
        )
        assertEquals(
            """{"type":"auth_response","challenge":"nonce","client_sig":"signature"}""",
            json.encodeToString(
                AuthResponsePacket(type = "auth_response", challenge = "nonce", clientSignature = "signature"),
            ),
        )
        assertEquals(
            """{"type":"ready"}""",
            json.encodeToString(ReadyPacket(type = "ready")),
        )
    }

    @Test
    fun `console command sends command in typed json envelope`() {
        assertEquals(
            """{"type":"console_cmd","command":"say hi"}""",
            json.encodeToString(ConsoleCmdPacket(type = "console_cmd", command = "say hi")),
        )
    }

    @Test
    fun `stats response omits fields not requested`() {
        val encoded = json.encodeToString(StatsPacket(type = "stats", tps = 19.8, ramUsed = 412, ramMax = 1024))

        assertTrue(encoded.contains(""""type":"stats""""))
        assertTrue(encoded.contains(""""ram_used":412"""))
        assertTrue(encoded.contains(""""ram_max":1024"""))
        assertTrue(!encoded.contains("mspt"))
        assertTrue(!encoded.contains("uptime"))
    }
}
