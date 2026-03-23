package org.zeripe.angongcommon.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * 파티 시스템용 페이로드.
 * StatPayload과 동일한 4바이트 int 길이 접두사 방식 사용 (Plugin 호환).
 */
public record PartyPayload(byte[] data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PartyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("angonggui", "party"));

    public static final StreamCodec<ByteBuf, PartyPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.data().length);
                        buf.writeBytes(payload.data());
                    },
                    buf -> {
                        int len = buf.readInt();
                        byte[] bytes = new byte[len];
                        buf.readBytes(bytes);
                        return new PartyPayload(bytes);
                    }
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public String asJson() {
        return new String(data, StandardCharsets.UTF_8);
    }

    public static PartyPayload of(String json) {
        return new PartyPayload(json.getBytes(StandardCharsets.UTF_8));
    }
}
