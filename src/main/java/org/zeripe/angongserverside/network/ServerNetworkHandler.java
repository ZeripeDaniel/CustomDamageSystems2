package org.zeripe.angongserverside.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import org.slf4j.Logger;
import org.zeripe.angongcommon.network.PartyPayload;
import org.zeripe.angongcommon.network.StatPayload;
import org.zeripe.angongserverside.combat.CombatWeaponManager;
import org.zeripe.angongserverside.combat.DamageSkinManager;
import org.zeripe.angongserverside.combat.StatManager;
import org.zeripe.angongserverside.config.AngongGuiConfig;
import org.zeripe.angongserverside.config.EquipmentStatConfig;
import org.zeripe.angongserverside.config.ServerConfig;
import org.zeripe.angongserverside.party.PartyManager;
import org.zeripe.angongserverside.quest.QuestManager;
import org.zeripe.customdamagesystem.item.AccessoryDataManager;
import org.zeripe.customdamagesystem.item.AccessoryMenu;

public class ServerNetworkHandler {
    private final Logger logger;
    private StatManager statManager;
    private DamageSkinManager skinManager;
    private ServerConfig serverConfig;
    private AngongGuiConfig angongGuiConfig;
    private QuestManager questManager;
    private PartyManager partyManager;
    private EquipmentStatConfig equipmentStatConfig;

    public ServerNetworkHandler(Logger logger) {
        this.logger = logger;
    }

    public void setStatManager(StatManager statManager) {
        this.statManager = statManager;
    }

    public void setSkinManager(DamageSkinManager skinManager) {
        this.skinManager = skinManager;
    }

    public void setServerConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public void setAngongGuiConfig(AngongGuiConfig angongGuiConfig) {
        this.angongGuiConfig = angongGuiConfig;
    }

    public void setQuestManager(QuestManager questManager) {
        this.questManager = questManager;
    }

    public void setPartyManager(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    public void setEquipmentStatConfig(EquipmentStatConfig equipmentStatConfig) {
        this.equipmentStatConfig = equipmentStatConfig;
    }

    /** 플레이어에게 서버 시스템 설정(토글) 전송 — 접속 시 호출 */
    public void sendSystemConfig(ServerPlayer player) {
        if (serverConfig == null) return;
        JsonObject resp = new JsonObject();
        resp.addProperty("action", "system_config");
        resp.addProperty("statSystemEnabled", serverConfig.statSystemEnabled);
        resp.addProperty("damageSystemEnabled", serverConfig.damageSystemEnabled);
        resp.addProperty("customHealthEnabled", serverConfig.customHealthEnabled);
        resp.addProperty("customHudEnabled", serverConfig.customHudEnabled);
        resp.addProperty("questSystemEnabled", serverConfig.questSystemEnabled);
        // AngongGui 설정
        if (angongGuiConfig != null) {
            JsonObject gui = new JsonObject();
            gui.addProperty("overlayEnabled", angongGuiConfig.overlayEnabled);
            gui.addProperty("menuBarEnabled", angongGuiConfig.menuBarEnabled);
            gui.addProperty("moneyDisplayEnabled", angongGuiConfig.moneyDisplayEnabled);
            gui.addProperty("partyWindowEnabled", angongGuiConfig.partyWindowEnabled);
            gui.addProperty("questWindowEnabled", angongGuiConfig.questWindowEnabled);
            gui.addProperty("customPauseScreenEnabled", angongGuiConfig.customPauseScreenEnabled);
            resp.add("angongGui", gui);
        }
        sendStat(player, resp.toString());
    }

    public void registerPayloads() {
        tryRegisterS2C(StatPayload.TYPE, StatPayload.CODEC);
        tryRegisterC2S(StatPayload.TYPE, StatPayload.CODEC);
        tryRegisterS2C(PartyPayload.TYPE, PartyPayload.CODEC);
        tryRegisterC2S(PartyPayload.TYPE, PartyPayload.CODEC);
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
        registerPartyReceiver();
        ServerPlayNetworking.registerGlobalReceiver(StatPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                try {
                    JsonObject req = JsonParser.parseString(payload.json()).getAsJsonObject();
                    String action = req.has("action") ? req.get("action").getAsString() : "";
                    if ("get".equals(action)) {
                        // 클라이언트가 준비됐다는 신호 → system_config 먼저 보내고 stat + 레지스트리 전송
                        sendSystemConfig(player);
                        sendAccessoryRegistry(player);
                        if (statManager != null) {
                            statManager.sendFullStat(player, statManager.getData(player.getUUID()));
                        }
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
                    if ("get_economy".equals(action) && statManager != null) {
                        var data = statManager.getData(player.getUUID());
                        if (data != null) statManager.sendEconomy(player, data);
                    }
                    if ("skin_cycle".equals(action) && skinManager != null) {
                        String newSkin = skinManager.cycleNext(player.getUUID());
                        String changeJson = DamageSkinManager.buildSkinChangePacket(player.getUUID(), newSkin).toString();
                        for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                            sendStat(other, changeJson);
                        }
                    }
                    // ── 퀘스트 액션 ──
                    if ("quest_list".equals(action) && questManager != null) {
                        String questJson = questManager.buildQuestListJson(player.getUUID()).toString();
                        sendStat(player, questJson);
                    }
                    if ("quest_accept".equals(action) && questManager != null) {
                        String questId = req.has("questId") ? req.get("questId").getAsString() : "";
                        questManager.acceptQuest(player.getUUID(), questId);
                    }
                    if ("quest_reject".equals(action) && questManager != null) {
                        String questId = req.has("questId") ? req.get("questId").getAsString() : "";
                        questManager.rejectQuest(player.getUUID(), questId);
                        // 거절 시 퀘스트가 제거되므로 전체 목록 갱신
                        sendStat(player, questManager.buildQuestListJson(player.getUUID()).toString());
                    }
                    if ("quest_claim".equals(action) && questManager != null) {
                        String questId = req.has("questId") ? req.get("questId").getAsString() : "";
                        questManager.claimReward(player.getUUID(), questId);
                        // 보상 수령 후 전체 목록 갱신 (nextQuest가 추가될 수 있으므로)
                        sendStat(player, questManager.buildQuestListJson(player.getUUID()).toString());
                    }
                } catch (Exception e) {
                    logger.warn("[ServerNetworkHandler] 요청 파싱 실패: {}", e.getMessage());
                }
            });
        });
    }

    private void registerPartyReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(PartyPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (partyManager == null) return;
                try {
                    JsonObject req = JsonParser.parseString(payload.asJson()).getAsJsonObject();
                    String action = req.has("action") ? req.get("action").getAsString() : "";
                    switch (action) {
                        case "get" -> {
                            JsonObject data = partyManager.getPartyData(player, context.server());
                            sendParty(player, data.toString());
                        }
                        case "create" -> {
                            JsonObject res = partyManager.createParty(player);
                            sendParty(player, res.toString());
                        }
                        case "invite" -> {
                            String target = req.has("target") ? req.get("target").getAsString() : "";
                            JsonObject res = partyManager.invitePlayer(player, target, context.server());
                            sendParty(player, res.toString());
                        }
                        case "accept" -> {
                            String from = req.has("from") ? req.get("from").getAsString() : "";
                            JsonObject res = partyManager.acceptInvite(player, from, context.server());
                            sendParty(player, res.toString());
                        }
                        case "reject" -> {
                            String from = req.has("from") ? req.get("from").getAsString() : "";
                            JsonObject res = partyManager.rejectInvite(player, from, context.server());
                            sendParty(player, res.toString());
                        }
                        case "leave" -> {
                            JsonObject res = partyManager.leaveParty(player, context.server());
                            sendParty(player, res.toString());
                        }
                        case "disband" -> {
                            JsonObject res = partyManager.disbandParty(player, context.server());
                            sendParty(player, res.toString());
                        }
                        case "kick" -> {
                            String target = req.has("target") ? req.get("target").getAsString() : "";
                            JsonObject res = partyManager.kickMember(player, target, context.server());
                            sendParty(player, res.toString());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("[ServerNetworkHandler] 파티 요청 파싱 실패: {}", e.getMessage());
                }
            });
        });
    }

    public static void sendParty(ServerPlayer player, String json) {
        ServerPlayNetworking.send(player, PartyPayload.of(json));
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

    /** 플레이어에게 퀘스트 목록 전송 */
    public void sendQuestList(ServerPlayer player) {
        if (questManager == null) return;
        String json = questManager.buildQuestListJson(player.getUUID()).toString();
        sendStat(player, json);
    }

    /** 특정 플레이어에게 퀘스트 업데이트 전송 */
    public void sendQuestUpdate(ServerPlayer player, String questUpdateJson) {
        sendStat(player, questUpdateJson);
    }

    /** 등록 장비 레지스트리를 클라이언트에 전송 (커스텀 툴팁용) */
    public void sendAccessoryRegistry(ServerPlayer player) {
        if (equipmentStatConfig == null || equipmentStatConfig.items == null) return;

        JsonObject packet = new JsonObject();
        packet.addProperty("action", "accessory_registry_sync");
        JsonArray entries = new JsonArray();

        for (EquipmentStatConfig.ItemEntry entry : equipmentStatConfig.items) {
            if (entry == null || entry.itemId == null || entry.slots == null || entry.slots.isEmpty()) continue;
            String slotType = entry.slots.getFirst().toLowerCase();
            String accType = switch (slotType) {
                case "ring" -> "RING";
                case "necklace" -> "NECKLACE";
                case "earring" -> "EARRING";
                case "mainhand" -> "WEAPON";
                case "head" -> "HELMET";
                case "chest" -> "CHESTPLATE";
                case "legs" -> "LEGGINGS";
                case "feet" -> "BOOTS";
                default -> "UNKNOWN";
            };

            JsonObject e = new JsonObject();
            e.addProperty("item", entry.itemId);
            e.addProperty("type", accType);
            if (entry.registryId != null) e.addProperty("registryId", entry.registryId);
            if (entry.customModelData != 0) e.addProperty("cmd", entry.customModelData);
            e.addProperty("rarity", entry.rarity != null ? entry.rarity : "NORMAL");
            e.addProperty("itemLevel", entry.itemLevel);
            e.addProperty("attack", entry.attack);
            e.addProperty("magicAttack", entry.magicAttack);
            e.addProperty("defense", entry.defense);
            e.addProperty("hp", entry.hp);
            e.addProperty("critRate", entry.critRate);
            e.addProperty("critDamage", entry.critDamage);
            e.addProperty("attackSpeed", entry.attackSpeed);
            e.addProperty("str", entry.strength);
            e.addProperty("agi", entry.agility);
            e.addProperty("int", entry.intelligence);
            e.addProperty("luk", entry.luck);
            if (entry.displayName != null) e.addProperty("displayName", entry.displayName);
            entries.add(e);
        }

        if (entries.isEmpty()) {
            logger.info("[ServerNetworkHandler] sendAccessoryRegistry: 등록 아이템 없음 (entries 비어있음)");
            return;
        }
        packet.add("entries", entries);
        logger.info("[ServerNetworkHandler] sendAccessoryRegistry: {} 에게 {}개 아이템 전송", player.getName().getString(), entries.size());
        sendStat(player, packet.toString());
    }
}
