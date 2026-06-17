package dev.ilgax.venus.network

import dev.ilgax.venus.protocol.VenusChannels
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

object VenusPayloads {
    fun registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ClientKeyPayload.TYPE, ClientKeyPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AuthResponsePayload.TYPE, AuthResponsePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ErrorPayload.TYPE, ErrorPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CmdPayload.TYPE, CmdPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ErrorPayload.TYPE, ErrorPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawPayload.TYPE, VenusRawPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawAuthPayload.TYPE, VenusRawAuthPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawReadyPayload.TYPE, VenusRawReadyPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VenusRawDataPayload.TYPE, VenusRawDataPayload.CODEC)
    }
}

data object HelloPayload : CustomPacketPayload {
    val TYPE =
        CustomPacketPayload.Type<HelloPayload>(
            channelId(VenusChannels.HELLO),
        )
    val CODEC: StreamCodec<FriendlyByteBuf, HelloPayload> =
        StreamCodec.unit(HelloPayload)

    override fun type(): CustomPacketPayload.Type<HelloPayload> = TYPE
}

data class ClientKeyPayload(
    val data: String,
) : CustomPacketPayload {
    companion object {
        val TYPE =
            CustomPacketPayload.Type<ClientKeyPayload>(
                channelId(VenusChannels.KEY),
            )
        val CODEC: StreamCodec<FriendlyByteBuf, ClientKeyPayload> = textCodec(::ClientKeyPayload) { it.data }
    }

    override fun type(): CustomPacketPayload.Type<ClientKeyPayload> = TYPE
}

data class AuthResponsePayload(
    val data: String,
) : CustomPacketPayload {
    companion object {
        val TYPE =
            CustomPacketPayload.Type<AuthResponsePayload>(
                channelId(VenusChannels.AUTH),
            )
        val CODEC: StreamCodec<FriendlyByteBuf, AuthResponsePayload> = textCodec(::AuthResponsePayload) { it.data }
    }

    override fun type(): CustomPacketPayload.Type<AuthResponsePayload> = TYPE
}

data class ErrorPayload(
    val data: String,
) : CustomPacketPayload {
    companion object {
        val TYPE =
            CustomPacketPayload.Type<ErrorPayload>(
                channelId(VenusChannels.ERROR),
            )
        val CODEC: StreamCodec<FriendlyByteBuf, ErrorPayload> = textCodec(::ErrorPayload) { it.data }
    }

    override fun type(): CustomPacketPayload.Type<ErrorPayload> = TYPE
}

data class CmdPayload(
    val data: String,
) : CustomPacketPayload {
    companion object {
        val TYPE =
            CustomPacketPayload.Type<CmdPayload>(
                channelId(VenusChannels.CMD),
            )
        val CODEC: StreamCodec<FriendlyByteBuf, CmdPayload> = textCodec(::CmdPayload) { it.data }
    }

    override fun type(): CustomPacketPayload.Type<CmdPayload> = TYPE
}

private fun <T : CustomPacketPayload> textCodec(
    create: (String) -> T,
    extract: (T) -> String,
): StreamCodec<FriendlyByteBuf, T> =
    StreamCodec.of(
        { buf, payload -> buf.writeBytes(extract(payload).toByteArray(Charsets.UTF_8)) },
        { buf ->
            val bytes = ByteArray(buf.readableBytes())
            buf.readBytes(bytes)
            create(bytes.toString(Charsets.UTF_8))
        },
    )

private fun channelId(channel: String): Identifier = Identifier.fromNamespaceAndPath("venus", channel.substringAfter(':'))
