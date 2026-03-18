package org.zeripe.angongserverside.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.zeripe.angongcommon.network.StatPayload;
import org.zeripe.angongserverside.combat.StatManager;

public class ServerNetworkHandler {
    private final Logger logger;
    private StatManager statManager;

    public ServerNetworkHandler(Logger logger) {
        this.logger = logger;
    }

    public void setStatManager(StatManager statManager) {
        this.statManager = statManager;
    }

    public void registerPayloads() {
        tryRegisterS2C(StatPayload.TYPE, StatPayload.CODEC);
        tryRegisterC2S(StatPayload.TYPE, StatPayload.CODEC);
    }

    private <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload>
    void tryRegisterS2C(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type,
                        net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T> codec) {
        try {
            PayloadTypeRegistry.playS2C().register(type, codec);
        } catch (IllegalArgumentException e) {
            logger.debug("[ServerNetworkHandler] S2C 채널 이미 등록됨: {}", type.id());
        }
    }

    private <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload>
    void tryRegisterC2S(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type,
                        net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T> codec) {
        try {
            PayloadTypeRegistry.playC2S().register(type, codec);
        } catch (IllegalArgumentException e) {
            logger.debug("[ServerNetworkHandler] C2S 채널 이미 등록됨: {}", type.id());
        }
    }

    public void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(StatPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                try {
                    JsonObject req = JsonParser.parseString(payload.json()).getAsJsonObject();
                    String action = req.has("action") ? req.get("action").getAsString() : "";
                    if ("get".equals(action) && statManager != null) {
                        statManager.sendFullStat(player, statManager.getData(player.getUUID()));
                    }
                } catch (Exception e) {
                    logger.warn("[ServerNetworkHandler] 요청 파싱 실패: {}", e.getMessage());
                }
            });
        });
    }

    public static void sendStat(ServerPlayer player, String json) {
        ServerPlayNetworking.send(player, StatPayload.of(json));
    }
}
