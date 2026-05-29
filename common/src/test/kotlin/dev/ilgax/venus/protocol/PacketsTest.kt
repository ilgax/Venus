package dev.ilgax.venus.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
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
        assertEquals(
            """{"type":"error","reason":"auth_denied"}""",
            json.encodeToString(ErrorPacket(type = "error", reason = "auth_denied")),
        )
    }

    @Test
    fun `console command sends command in typed json envelope`() {
        assertEquals(
            """{"type":"console_cmd","command":"say hi"}""",
            json.encodeToString(ConsoleCmdPacket(type = "console_cmd", command = "say hi")),
        )
        assertEquals(
            """{"type":"player_list_get"}""",
            json.encodeToString(PlayerListGetPacket(type = "player_list_get")),
        )
        assertEquals(
            """{"type":"player_detail_get","uuid":"123"}""",
            json.encodeToString(PlayerDetailGetPacket(type = "player_detail_get", uuid = "123")),
        )
        assertEquals(
            """{"type":"player_action","request_id":"req-1","uuid":"123","action":"set_whitelisted","value":true}""",
            json.encodeToString(
                PlayerActionPacket(
                    type = "player_action",
                    requestId = "req-1",
                    uuid = "123",
                    action = "set_whitelisted",
                    value = JsonPrimitive(true),
                ),
            ),
        )
        assertEquals(
            """{"type":"player_action_result","request_id":"req-1","uuid":"123","action":"heal","success":true,"message":"Player healed."}""",
            json.encodeToString(
                PlayerActionResultPacket(
                    type = "player_action_result",
                    requestId = "req-1",
                    uuid = "123",
                    action = "heal",
                    success = true,
                    message = "Player healed.",
                ),
            ),
        )
    }

    @Test
    fun `console log packets use typed json envelopes`() {
        assertEquals(
            """{"type":"log_subscribe"}""",
            json.encodeToString(ConsoleLogSubscribePacket(type = "log_subscribe")),
        )
        assertEquals(
            """{"type":"console_log","lines":["one","two"]}""",
            json.encodeToString(ConsoleLogPacket(type = "console_log", lines = listOf("one", "two"))),
        )
    }

    @Test
    fun `stat subscription encodes interval and requested stats`() {
        assertEquals(
            """{"type":"stat_subscribe","interval_seconds":5,"stats":["tps","ram","mspt"]}""",
            json.encodeToString(
                StatSubscribePacket(type = "stat_subscribe", intervalSeconds = 5, stats = listOf("tps", "ram", "mspt")),
            ),
        )
    }

    @Test
    fun `command response encodes command output lines`() {
        assertEquals(
            """{"type":"cmd_response","command":"say hi","lines":["hi","done"]}""",
            json.encodeToString(CmdResponsePacket(type = "cmd_response", command = "say hi", lines = listOf("hi", "done"))),
        )
    }

    @Test
    fun `player list and detail packets use typed json envelopes`() {
        assertEquals(
            """{"type":"player_list","online_count":2,"max_players":20,"online_players":[{"uuid":"1","name":"Alice","display_name":"Alice","online":true,"operator":false,"whitelisted":true,"blocked":false}],"whitelisted_players":[],"blocked_players":[]}""",
            json.encodeToString(
                PlayerListPacket(
                    type = "player_list",
                    onlineCount = 2,
                    maxPlayers = 20,
                    onlinePlayers =
                        listOf(
                            PlayerSummaryPacket(
                                uuid = "1",
                                name = "Alice",
                                displayName = "Alice",
                                online = true,
                                operator = false,
                                whitelisted = true,
                                blocked = false,
                            ),
                        ),
                    whitelistedPlayers = emptyList(),
                    blockedPlayers = emptyList(),
                ),
            ),
        )
        assertEquals(
            """{"type":"player_detail","player":{"uuid":"1","name":"Alice","display_name":"Alice","online":true,"operator":false,"whitelisted":true,"blocked":false,"game_mode":"survival","health":20.0,"max_health":20.0,"food_level":20,"level":3,"experience_progress":0.5,"world":"minecraft:overworld","x":1.0,"y":64.0,"z":-2.0}}""",
            json.encodeToString(
                PlayerDetailPacket(
                    type = "player_detail",
                    player =
                        PlayerDetail(
                            uuid = "1",
                            name = "Alice",
                            displayName = "Alice",
                            online = true,
                            operator = false,
                            whitelisted = true,
                            blocked = false,
                            gameMode = "survival",
                            health = 20.0,
                            maxHealth = 20.0,
                            foodLevel = 20,
                            level = 3,
                            experienceProgress = 0.5f,
                            world = "minecraft:overworld",
                            x = 1.0,
                            y = 64.0,
                            z = -2.0,
                        ),
                ),
            ),
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

    @Test
    fun `stats response encodes authoritative server fields with documented names`() {
        val encoded =
            json.encodeToString(
                StatsPacket(
                    type = "stats",
                    cpuLoad = 12.3,
                    onlinePlayers = 3,
                    maxPlayers = 20,
                    serverName = "Paper",
                    minecraftVersion = "1.21.11",
                ),
            )

        assertTrue(encoded.contains(""""cpu_load":12.3"""))
        assertTrue(encoded.contains(""""online_players":3"""))
        assertTrue(encoded.contains(""""max_players":20"""))
        assertTrue(encoded.contains(""""server_name":"Paper""""))
        assertTrue(encoded.contains(""""minecraft_version":"1.21.11""""))
    }

    @Test
    fun `stats response decodes when newer authoritative fields are missing`() {
        val decoded = json.decodeFromString<StatsPacket>("""{"type":"stats","tps":20.0}""")

        assertEquals("stats", decoded.type)
        assertEquals(20.0, decoded.tps)
        assertEquals(null, decoded.onlinePlayers)
        assertEquals(null, decoded.serverName)
    }
}
