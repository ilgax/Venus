package dev.ilgax.venus.channel

import dev.ilgax.venus.auth.KeyManager
import dev.ilgax.venus.network.AuthResponsePayload
import dev.ilgax.venus.network.ClientKeyPayload
import dev.ilgax.venus.network.CmdPayload
import dev.ilgax.venus.network.ErrorPayload
import dev.ilgax.venus.network.HelloPayload
import dev.ilgax.venus.protocol.AuthChallengePacket
import dev.ilgax.venus.protocol.ConsoleCmdPacket
import dev.ilgax.venus.protocol.ConsoleLogSubscribePacket
import dev.ilgax.venus.protocol.FileActionPacket
import dev.ilgax.venus.protocol.FileDownloadStartPacket
import dev.ilgax.venus.protocol.FileListGetPacket
import dev.ilgax.venus.protocol.FileRootsGetPacket
import dev.ilgax.venus.protocol.FileUploadStartPacket
import dev.ilgax.venus.protocol.PlayerActionPacket
import dev.ilgax.venus.protocol.PlayerDetailGetPacket
import dev.ilgax.venus.protocol.PlayerListGetPacket
import dev.ilgax.venus.protocol.ServerKeyPacket
import dev.ilgax.venus.protocol.StatSubscribePacket
import dev.ilgax.venus.state.SessionState
import io.mockk.mockk
import io.netty.buffer.Unpooled
import kotlinx.serialization.json.Json
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.junit.Test
import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun resetState() {
        SessionState.reset()
    }

    @Test
    fun `HelloPayload codec works`() {
        val payload = HelloPayload
        assertEquals("venus:hello", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        HelloPayload.CODEC.encode(buf, payload)
        val decoded = HelloPayload.CODEC.decode(buf)
        assertEquals(payload, decoded)
    }

    @Test
    fun `ClientKeyPayload codec works`() {
        val payload = ClientKeyPayload("test_key")
        assertEquals("venus:key", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ClientKeyPayload.CODEC.encode(buf, payload)
        val decoded = ClientKeyPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `AuthResponsePayload codec works`() {
        val payload = AuthResponsePayload("test_auth")
        assertEquals("venus:auth", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        AuthResponsePayload.CODEC.encode(buf, payload)
        val decoded = AuthResponsePayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `ErrorPayload codec works`() {
        val payload = ErrorPayload("test_error")
        assertEquals("venus:error", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        ErrorPayload.CODEC.encode(buf, payload)
        val decoded = ErrorPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `CmdPayload codec works`() {
        val payload = CmdPayload("test_cmd")
        assertEquals("venus:cmd", payload.type().id().toString())

        val buf = FriendlyByteBuf(Unpooled.buffer())
        CmdPayload.CODEC.encode(buf, payload)
        val decoded = CmdPayload.CODEC.decode(buf)
        assertEquals(payload.data, decoded.data)
    }

    @Test
    fun `file browsing commands preserve request ids and arguments`() {
        val fixture = fixture()

        val rootsId = fixture.client.requestFileRoots()
        val roots = fixture.nextCommand<FileRootsGetPacket>()
        val listId = fixture.client.requestFileList("config", "plugins", 12)
        val list = fixture.nextCommand<FileListGetPacket>()
        val actionId = fixture.client.sendFileAction("config", "move", "old.yml", "new.yml", true)
        val action = fixture.nextCommand<FileActionPacket>()

        assertEquals(rootsId, roots.requestId)
        assertEquals(listId, list.requestId)
        assertEquals("config", list.rootId)
        assertEquals("plugins", list.path)
        assertEquals(12, list.offset)
        assertEquals(actionId, action.requestId)
        assertEquals("move", action.action)
        assertEquals("new.yml", action.destination)
        assertTrue(action.overwrite)
    }

    @Test
    fun `file transfer commands prepare local state and serialize metadata`() {
        val fixture = fixture()
        val directory = createTempDirectory("venus-channel")
        val source = directory.resolve("source.txt")
        val destination = directory.resolve("download.txt")
        Files.writeString(source, "upload")

        try {
            val uploadId = fixture.client.uploadFile(source.toString(), "config", "remote.txt", true, "expected")
            val upload = fixture.nextCommand<FileUploadStartPacket>()
            val downloadId = fixture.client.downloadFile("config", "remote.txt", destination.toString(), true)
            val download = fixture.nextCommand<FileDownloadStartPacket>()
            val editorId = fixture.client.openFileEditor("config", "server.properties")
            val editor = fixture.nextCommand<FileDownloadStartPacket>()
            val saveId = fixture.client.saveEditedFile("config", "server.properties", "content", "old-hash")
            val save = fixture.nextCommand<FileUploadStartPacket>()
            val forceId = fixture.client.saveEditedFile("config", "server.properties", "forced", "old-hash", true)
            val force = fixture.nextCommand<FileUploadStartPacket>()

            assertEquals(uploadId, upload.requestId)
            assertEquals(6, upload.sizeBytes)
            assertTrue(upload.overwrite)
            assertEquals("expected", upload.expectedSha256)
            assertEquals(downloadId, download.requestId)
            assertEquals("remote.txt", download.path)
            assertEquals(editorId, editor.requestId)
            assertEquals("server.properties", editor.path)
            assertEquals(saveId, save.requestId)
            assertEquals(7, save.sizeBytes)
            assertEquals("old-hash", save.expectedSha256)
            assertEquals(forceId, force.requestId)
            assertNull(force.expectedSha256)
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `session commands serialize subscriptions console and player operations`() {
        val fixture = fixture()

        fixture.client.sendHello()
        val hello = fixture.sent.removeFirst()
        fixture.client.sendConsoleCommand("say hi")
        val console = fixture.nextCommand<ConsoleCmdPacket>()
        fixture.client.sendLogSubscribe()
        val logs = fixture.nextCommand<ConsoleLogSubscribePacket>()
        fixture.client.sendStatSubscribe()
        val stats = fixture.nextCommand<StatSubscribePacket>()
        fixture.client.sendPlayerListGet()
        val players = fixture.nextCommand<PlayerListGetPacket>()
        fixture.client.sendPlayerDetailGet("player-id")
        val detail = fixture.nextCommand<PlayerDetailGetPacket>()
        val booleanId = fixture.client.sendPlayerAction("player-id", "set_operator", true)
        val booleanAction = fixture.nextCommand<PlayerActionPacket>()
        val stringId = fixture.client.sendPlayerAction("player-id", "set_game_mode", "creative")
        val stringAction = fixture.nextCommand<PlayerActionPacket>()

        assertTrue(hello === HelloPayload)
        assertEquals("say hi", console.command)
        assertEquals("log_subscribe", logs.type)
        assertEquals(1, stats.intervalSeconds)
        assertTrue(players.requestId.isNotBlank())
        assertEquals("player-id", detail.uuid)
        assertEquals(booleanId, booleanAction.requestId)
        assertEquals(true, booleanAction.value?.toString()?.toBooleanStrict())
        assertEquals(stringId, stringAction.requestId)
        assertEquals("\"creative\"", stringAction.value.toString())
    }

    @Test
    fun `handshake packet validation reports malformed types and base64`() {
        val failures = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val client = ChannelClient(json, mockk(relaxed = true), logs::add, failures::add)

        client.invokePrivate("handleServerKey", "not-json")
        client.invokePrivate(
            "handleServerKey",
            json.encodeToString(ServerKeyPacket.serializer(), ServerKeyPacket("wrong", "key")),
        )
        client.invokePrivate("handleAuthChallenge", "not-json")
        client.invokePrivate(
            "handleAuthChallenge",
            json.encodeToString(AuthChallengePacket.serializer(), AuthChallengePacket("wrong", "key", "signature")),
        )
        client.invokePrivate(
            "handleAuthChallenge",
            json.encodeToString(AuthChallengePacket.serializer(), AuthChallengePacket("auth_challenge", "%%%", "signature")),
        )
        client.invokePrivate(
            "handleAuthChallenge",
            json.encodeToString(
                AuthChallengePacket.serializer(),
                AuthChallengePacket("auth_challenge", Base64.getEncoder().encodeToString(byteArrayOf(1)), "%%%"),
            ),
        )

        assertEquals(
            listOf(
                "Invalid server key packet.",
                "Unexpected server key packet.",
                "Invalid auth challenge packet.",
                "Unexpected auth challenge packet.",
                "Invalid auth challenge.",
                "Invalid auth signature.",
            ),
            failures,
        )
        assertEquals(6, logs.size)
    }

    private fun fixture(): Fixture {
        val sent = mutableListOf<CustomPacketPayload>()
        val client = ChannelClient(json, mockk<KeyManager>(relaxed = true), {})
        client.payloadSender = sent::add
        return Fixture(client, sent)
    }

    private fun ChannelClient.invokePrivate(
        name: String,
        data: String,
    ) {
        val method = ChannelClient::class.java.getDeclaredMethod(name, String::class.java)
        method.isAccessible = true
        method.invoke(this, data)
    }

    private inner class Fixture(
        val client: ChannelClient,
        val sent: MutableList<CustomPacketPayload>,
    ) {
        inline fun <reified T> nextCommand(): T {
            val payload = sent.removeFirst() as CmdPayload
            return json.decodeFromString(payload.data)
        }
    }
}
