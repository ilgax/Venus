package dev.ilgax.venus.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data object HelloPayload : CustomPacketPayload {
    val TYPE =
        CustomPacketPayload.Type<HelloPayload>(
            Identifier.fromNamespaceAndPath("venus", "hello"),
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
                Identifier.fromNamespaceAndPath("venus", "key"),
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
                Identifier.fromNamespaceAndPath("venus", "auth"),
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
                Identifier.fromNamespaceAndPath("venus", "error"),
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
                Identifier.fromNamespaceAndPath("venus", "cmd"),
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
