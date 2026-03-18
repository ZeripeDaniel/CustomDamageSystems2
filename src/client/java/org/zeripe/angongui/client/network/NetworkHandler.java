package org.zeripe.angongui.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.zeripe.angongcommon.network.StatPayload;
import org.zeripe.angongui.client.ClientState;
import org.zeripe.angongui.client.DamageNumberRenderer;
import org.zeripe.angongui.client.LocalStatManager;

import java.util.ArrayList;
import java.util.List;

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
                    ClientState.get().clear();
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

    public static void requestStat() {
        if (!serverAuthoritative) return;
        if (!ClientPlayNetworking.canSend(StatPayload.TYPE)) return;
        ClientPlayNetworking.send(StatPayload.of("{\"action\":\"get\"}"));
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
        DamageNumberRenderer.add(x, y, z, amount, crit, type);
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
}
