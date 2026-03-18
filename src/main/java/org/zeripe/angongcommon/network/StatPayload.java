package org.zeripe.angongcommon.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

public record StatPayload(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StatPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("customdamagesystem", "stat"));

    public static final StreamCodec<ByteBuf, StatPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                byte[] bytes = payload.json().getBytes(StandardCharsets.UTF_8);
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            },
            buf -> {
                int len = buf.readInt();
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                return new StatPayload(new String(bytes, StandardCharsets.UTF_8));
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static StatPayload of(String json) {
        return new StatPayload(json);
    }

    public String asJson() {
        return json;
    }
}
