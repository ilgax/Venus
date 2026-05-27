package dev.ilgax.venus.protocol

import kotlinx.serialization.encodeToString
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
    fun `stats response omits fields not requested`() {
        val encoded = json.encodeToString(StatsPacket(type = "stats", tps = 19.8, ramUsed = 412, ramMax = 1024))

        assertTrue(encoded.contains(""""type":"stats""""))
        assertTrue(encoded.contains(""""ram_used":412"""))
        assertTrue(encoded.contains(""""ram_max":1024"""))
        assertTrue(!encoded.contains("mspt"))
        assertTrue(!encoded.contains("uptime"))
    }
}
