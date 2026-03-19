package org.zeripe.angongserverside.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import org.slf4j.Logger;
import org.zeripe.angongcommon.network.StatPayload;
import org.zeripe.angongserverside.combat.CombatWeaponManager;
import org.zeripe.angongserverside.combat.DamageSkinManager;
import org.zeripe.angongserverside.combat.StatManager;
import org.zeripe.customdamagesystem.item.AccessoryDataManager;
import org.zeripe.customdamagesystem.item.AccessoryMenu;

public class ServerNetworkHandler {
    private final Logger logger;
    private StatManager statManager;
    private DamageSkinManager skinManager;

    public ServerNetworkHandler(Logger logger) {
        this.logger = logger;
    }

    public void setStatManager(StatManager statManager) {
        this.statManager = statManager;
    }

    public void setSkinManager(DamageSkinManager skinManager) {
        this.skinManager = skinManager;
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
                    if ("combat_mode".equals(action)) {
                        boolean enabled = req.has("enabled") && req.get("enabled").getAsBoolean();
                        if (enabled) {
                            CombatWeaponManager.enterCombat(player);
                        } else {
                            CombatWeaponManager.leaveCombat(player);
                        }
                    }
                    if ("open_accessory".equals(action)) {
                        player.openMenu(new SimpleMenuProvider(
                                (syncId, inv, p) -> new AccessoryMenu(syncId, inv,
                                        AccessoryDataManager.getOrLoad(player.getUUID())),
                                Component.translatable("ui.customdamagesystem.acc.title")
                        ));
                    }
                    if ("skin_select".equals(action) && skinManager != null) {
                        String skinId = req.has("skinId") ? req.get("skinId").getAsString() : "none";
                        if (skinManager.selectSkin(player.getUUID(), skinId)) {
                            // 주변 플레이어에게 스킨 변경 브로드캐스트
                            String changeJson = DamageSkinManager.buildSkinChangePacket(player.getUUID(), skinId).toString();
                            for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                                sendStat(other, changeJson);
                            }
                        }
                    }
                    if ("skin_cycle".equals(action) && skinManager != null) {
                        String newSkin = skinManager.cycleNext(player.getUUID());
                        String changeJson = DamageSkinManager.buildSkinChangePacket(player.getUUID(), newSkin).toString();
                        for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                            sendStat(other, changeJson);
                        }
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

    /** 플레이어에게 스킨 목록 + 소유 정보 전송 */
    public void sendSkinList(ServerPlayer player) {
        if (skinManager == null) return;
        String json = skinManager.buildSkinListPacket(player.getUUID()).toString();
        sendStat(player, json);
    }

    /** 접속한 플레이어에게 모든 온라인 플레이어의 현재 스킨 선택 전송 */
    public void sendAllPlayerSkins(ServerPlayer joiningPlayer) {
        if (skinManager == null) return;
        for (ServerPlayer other : joiningPlayer.server.getPlayerList().getPlayers()) {
            String skinId = skinManager.getSelectedSkin(other.getUUID());
            if (!"none".equals(skinId)) {
                String json = DamageSkinManager.buildSkinChangePacket(other.getUUID(), skinId).toString();
                sendStat(joiningPlayer, json);
            }
        }
    }
}
