package org.zeripe.angongui.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.zeripe.angongcommon.network.StatPayload;
import org.zeripe.angongui.client.ClientItemLevelCache;
import org.zeripe.angongui.client.ClientState;
import org.zeripe.angongui.client.CombatModeState;
import org.zeripe.angongui.client.DamageNumberRenderer;
import org.zeripe.angongui.client.DamageSkin;
import org.zeripe.angongui.client.LocalStatManager;
import org.zeripe.customdamagesystem.item.AccessoryDefinition;
import org.zeripe.customdamagesystem.item.AccessoryRegistry;
import org.zeripe.customdamagesystem.item.AccessoryType;

import java.util.*;

public final class NetworkHandler {
    private static boolean serverAuthoritative = false;

    private NetworkHandler() {}

    public static void register() {
        tryRegisterPayloads();
        ClientPlayNetworking.registerGlobalReceiver(StatPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleStatResponse(payload.asJson())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> {
                    if (ClientPlayNetworking.canSend(StatPayload.TYPE)) {
                        serverAuthoritative = true;
                        LocalStatManager.deactivate();
                        requestStat();
                    } else {
                        serverAuthoritative = false;
                        LocalStatManager.activate();
                    }
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> {
                    serverAuthoritative = false;
                    LocalStatManager.deactivate();
                    DamageNumberRenderer.clear();
                    DamageSkin.clearServerData();
                    ClientState.get().clear();
                    ClientItemLevelCache.clear();
                    CombatModeState.reset();
                }));
    }

    private static void tryRegisterPayloads() {
        try {
            PayloadTypeRegistry.playC2S().register(StatPayload.TYPE, StatPayload.CODEC);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            PayloadTypeRegistry.playS2C().register(StatPayload.TYPE, StatPayload.CODEC);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public static boolean isServerAuthoritative() {
        return serverAuthoritative;
    }

    public static void requestStat() {
        if (!serverAuthoritative) return;
        if (!ClientPlayNetworking.canSend(StatPayload.TYPE)) return;
        ClientPlayNetworking.send(StatPayload.of("{\"action\":\"get\"}"));
    }

    /** 서버에 스킨 순환 요청 */
    public static void requestSkinCycle() {
        if (!serverAuthoritative) return;
        if (!ClientPlayNetworking.canSend(StatPayload.TYPE)) return;
        ClientPlayNetworking.send(StatPayload.of("{\"action\":\"skin_cycle\"}"));
    }

    /** 서버에 특정 스킨 선택 요청 */
    public static void requestSkinSelect(String skinId) {
        if (!serverAuthoritative) return;
        if (!ClientPlayNetworking.canSend(StatPayload.TYPE)) return;
        ClientPlayNetworking.send(StatPayload.of("{\"action\":\"skin_select\",\"skinId\":\"" + skinId + "\"}"));
    }

    private static void handleStatResponse(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String action = obj.has("action") ? obj.get("action").getAsString() : "";
            switch (action) {
                case "stat_data" -> parseFullStatData(obj);
                case "hp_update" -> parseHpUpdate(obj);
                case "damage_number" -> parseDamageNumber(obj);
                case "buff_update" -> parseBuffUpdate(obj);
                case "item_levels" -> parseItemLevels(obj);
                case "skin_list" -> parseSkinList(obj);
                case "skin_change" -> parseSkinChange(obj);
            }
        } catch (Exception ignored) {
        }
    }

    private static void parseFullStatData(JsonObject obj) {
        JsonObject s = obj.getAsJsonObject("stat");
        if (s == null) return;

        serverAuthoritative = true;
        LocalStatManager.deactivate();
        ClientState.get().setPlayerStats(new ClientState.PlayerStats(
                s.has("name") ? s.get("name").getAsString() : "",
                s.has("itemLevel") ? s.get("itemLevel").getAsDouble() : 0.0,
                s.has("combatPower") ? s.get("combatPower").getAsInt() : 0,
                s.has("currentHp") ? s.get("currentHp").getAsInt() : 10,
                s.has("maxHp") ? s.get("maxHp").getAsInt() : 10,
                s.has("currentMp") ? s.get("currentMp").getAsInt() : 0,
                s.has("maxMp") ? s.get("maxMp").getAsInt() : 0,
                s.has("attack") ? s.get("attack").getAsInt() : 0,
                s.has("magicAttack") ? s.get("magicAttack").getAsInt() : 0,
                s.has("defense") ? s.get("defense").getAsInt() : 0,
                s.has("critRate") ? s.get("critRate").getAsDouble() : 0,
                s.has("critDamage") ? s.get("critDamage").getAsDouble() : 150,
                s.has("armorPenetration") ? s.get("armorPenetration").getAsDouble() : 0,
                s.has("bonusDamage") ? s.get("bonusDamage").getAsInt() : 0,
                s.has("elementalMultiplier") ? s.get("elementalMultiplier").getAsDouble() : 100,
                s.has("lifeSteal") ? s.get("lifeSteal").getAsDouble() : 0,
                s.has("attackMultiplier") ? s.get("attackMultiplier").getAsDouble() : 100,
                s.has("magicAttackMultiplier") ? s.get("magicAttackMultiplier").getAsDouble() : 100,
                s.has("moveSpeed") ? s.get("moveSpeed").getAsDouble() : 100,
                s.has("buffDuration") ? s.get("buffDuration").getAsDouble() : 100,
                s.has("cooldownReduction") ? s.get("cooldownReduction").getAsDouble() : 0,
                s.has("clearGoldBonus") ? s.get("clearGoldBonus").getAsDouble() : 0,
                s.has("strength") ? s.get("strength").getAsInt() : 0,
                s.has("agility") ? s.get("agility").getAsInt() : 0,
                s.has("intelligence") ? s.get("intelligence").getAsInt() : 0,
                s.has("luck") ? s.get("luck").getAsInt() : 0,
                s.has("equipStrength") ? s.get("equipStrength").getAsInt() : 0,
                s.has("equipAgility") ? s.get("equipAgility").getAsInt() : 0,
                s.has("equipIntelligence") ? s.get("equipIntelligence").getAsInt() : 0,
                s.has("equipLuck") ? s.get("equipLuck").getAsInt() : 0
        ));
    }

    private static void parseHpUpdate(JsonObject obj) {
        ClientState.get().updateHpMp(
                obj.has("currentHp") ? obj.get("currentHp").getAsInt() : 0,
                obj.has("maxHp") ? obj.get("maxHp").getAsInt() : 10,
                obj.has("currentMp") ? obj.get("currentMp").getAsInt() : 0,
                obj.has("maxMp") ? obj.get("maxMp").getAsInt() : 0
        );
    }

    private static void parseDamageNumber(JsonObject obj) {
        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : 0;
        double z = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 0;
        boolean crit = obj.has("crit") && obj.get("crit").getAsBoolean();
        String type = obj.has("type") ? obj.get("type").getAsString() : "PHYSICAL";
        String skinId = obj.has("skinId") ? obj.get("skinId").getAsString() : null;
        DamageNumberRenderer.add(x, y, z, amount, crit, type, skinId);
    }

    private static void parseSkinList(JsonObject obj) {
        int cellW = obj.has("cellWidth") ? obj.get("cellWidth").getAsInt() : 16;
        int cellH = obj.has("cellHeight") ? obj.get("cellHeight").getAsInt() : 24;

        List<DamageSkin.SkinPackInfo> packs = new ArrayList<>();
        JsonArray packsArr = obj.getAsJsonArray("packs");
        if (packsArr != null) {
            for (var el : packsArr) {
                JsonObject p = el.getAsJsonObject();
                packs.add(new DamageSkin.SkinPackInfo(
                        p.get("id").getAsString(),
                        p.has("displayName") ? p.get("displayName").getAsString() : p.get("id").getAsString(),
                        p.has("namespace") ? p.get("namespace").getAsString() : "customdamagesystem",
                        p.has("texturePath") ? p.get("texturePath").getAsString() : "",
                        p.has("cellWidth") ? p.get("cellWidth").getAsInt() : cellW,
                        p.has("cellHeight") ? p.get("cellHeight").getAsInt() : cellH
                ));
            }
        }

        Set<String> owned = new LinkedHashSet<>();
        JsonArray ownedArr = obj.getAsJsonArray("owned");
        if (ownedArr != null) {
            for (var el : ownedArr) owned.add(el.getAsString());
        }

        String selected = obj.has("selected") ? obj.get("selected").getAsString() : "none";

        DamageSkin.setServerPacks(packs, cellW, cellH, owned, selected);
    }

    private static void parseSkinChange(JsonObject obj) {
        String uuidStr = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
        String skinId = obj.has("skinId") ? obj.get("skinId").getAsString() : "none";
        if (uuidStr == null) return;

        try {
            UUID uuid = UUID.fromString(uuidStr);
            DamageSkin.setPlayerSkin(uuid, skinId);

            // 본인이면 내 선택도 업데이트
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.getUUID().equals(uuid)) {
                DamageSkin.setMySelectedSkin(skinId);
                DamageSkin.showSwitchMessage();
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void parseBuffUpdate(JsonObject obj) {
        JsonArray arr = obj.getAsJsonArray("buffs");
        if (arr == null) return;
        List<ClientState.BuffEntry> buffs = new ArrayList<>();
        for (var el : arr) {
            JsonObject b = el.getAsJsonObject();
            buffs.add(new ClientState.BuffEntry(
                    b.get("id").getAsString(),
                    b.get("name").getAsString(),
                    b.get("remainingSeconds").getAsInt()
            ));
        }
        ClientState.get().setActiveBuffs(buffs);
    }

    private static void parseItemLevels(JsonObject obj) {
        JsonObject data = obj.getAsJsonObject("data");
        if (data != null) {
            ClientItemLevelCache.clear();
            for (var entry : data.entrySet()) {
                ClientItemLevelCache.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }
        JsonArray weapons = obj.getAsJsonArray("weapons");
        if (weapons != null) {
            for (var el : weapons) {
                String itemId = el.getAsString();
                BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)).ifPresent(ref -> {
                    if (!AccessoryRegistry.isAccessory(ref.value())) {
                        AccessoryRegistry.register(ref.value(),
                                new AccessoryDefinition(AccessoryType.WEAPON, 0, 0, 0, 0));
                    }
                });
            }
        }
    }
}
