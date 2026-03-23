package org.zeripe.angongui.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.zeripe.angongcommon.network.StatPayload;
import org.zeripe.angongui.client.AccessoryScreen;
import org.zeripe.customdamagesystem.item.AccessoryInventory;
import org.zeripe.customdamagesystem.item.AccessoryMenu;
import org.zeripe.angongui.client.ClientItemLevelCache;
import org.zeripe.angongui.client.ClientState;
import org.zeripe.angongui.client.CombatModeState;
import org.zeripe.angongui.client.DamageNumberRenderer;
import org.zeripe.angongui.client.ClientAccessoryRegistryCache;
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
                    // 서버/싱글 접속 시 이전 상태 초기화 → 서버 신호 올 때까지 바닐라 모드
                    ClientState.get().clear();
                    DamageNumberRenderer.clear();
                    DamageSkin.clearServerData();
                    ClientItemLevelCache.clear();
                    CombatModeState.reset();

                    if (ClientPlayNetworking.canSend(StatPayload.TYPE)) {
                        LOGGER.info("[CDS-Client] JOIN: CDS 서버 감지, waitingForConfig=true");
                        serverAuthoritative = true;
                        ClientState.get().setWaitingForConfig(true);
                        LocalStatManager.deactivate();
                        requestStat();
                    } else {
                        LOGGER.info("[CDS-Client] JOIN: 바닐라 서버 (CDS 채널 없음)");
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
                    try {
                        Class<?> registry = Class.forName("org.zeripe.angonggui.client.tooltip.ItemTooltipRegistry");
                        registry.getMethod("clear").invoke(null);
                    } catch (Exception ignored) {}
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

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("CDS-Client");

    private static void handleStatResponse(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String action = obj.has("action") ? obj.get("action").getAsString() : "";
            LOGGER.info("[CDS-Client] 패킷 수신: action={}", action);
            switch (action) {
                case "system_config" -> parseSystemConfig(obj);
                case "stat_data" -> parseFullStatData(obj);
                case "hp_update" -> parseHpUpdate(obj);
                case "damage_number" -> parseDamageNumber(obj);
                case "buff_update" -> parseBuffUpdate(obj);
                case "item_levels" -> parseItemLevels(obj);
                case "skin_list" -> parseSkinList(obj);
                case "skin_change" -> parseSkinChange(obj);
                case "economy_update" -> parseEconomyUpdate(obj);
                case "accessory_data" -> parseAccessoryData(obj);
                case "accessory_registry_sync" -> parseAccessoryRegistrySync(obj);
            }
        } catch (Exception ignored) {
        }
    }

    private static void parseSystemConfig(JsonObject obj) {
        LOGGER.info("[CDS-Client] system_config 수신: hud={}, health={}, damage={}, stat={}",
                obj.has("customHudEnabled") ? obj.get("customHudEnabled") : "없음",
                obj.has("customHealthEnabled") ? obj.get("customHealthEnabled") : "없음",
                obj.has("damageSystemEnabled") ? obj.get("damageSystemEnabled") : "없음",
                obj.has("statSystemEnabled") ? obj.get("statSystemEnabled") : "없음");
        ClientState.get().setSystemConfig(
                !obj.has("statSystemEnabled") || obj.get("statSystemEnabled").getAsBoolean(),
                !obj.has("damageSystemEnabled") || obj.get("damageSystemEnabled").getAsBoolean(),
                !obj.has("customHealthEnabled") || obj.get("customHealthEnabled").getAsBoolean(),
                !obj.has("customHudEnabled") || obj.get("customHudEnabled").getAsBoolean()
        );
        // AngongGui 설정
        if (obj.has("angongGui")) {
            JsonObject gui = obj.getAsJsonObject("angongGui");
            ClientState.get().setAngongGuiConfig(
                    !gui.has("overlayEnabled") || gui.get("overlayEnabled").getAsBoolean(),
                    !gui.has("menuBarEnabled") || gui.get("menuBarEnabled").getAsBoolean(),
                    !gui.has("moneyDisplayEnabled") || gui.get("moneyDisplayEnabled").getAsBoolean(),
                    !gui.has("partyWindowEnabled") || gui.get("partyWindowEnabled").getAsBoolean(),
                    !gui.has("questWindowEnabled") || gui.get("questWindowEnabled").getAsBoolean(),
                    !gui.has("customPauseScreenEnabled") || gui.get("customPauseScreenEnabled").getAsBoolean()
            );
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
                s.has("absorptionHp") ? s.get("absorptionHp").getAsInt() : 0,
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
                obj.has("absorptionHp") ? obj.get("absorptionHp").getAsInt() : 0,
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

    private static void parseEconomyUpdate(JsonObject obj) {
        long gold = obj.has("gold") ? obj.get("gold").getAsLong() : 0;
        // AngongGui 종속성 없이 리플렉션으로 LocalEconomyManager.onServerGoldReceived 호출
        try {
            Class<?> cls = Class.forName("org.zeripe.angonggui.client.LocalEconomyManager");
            cls.getMethod("onServerGoldReceived", long.class).invoke(null, gold);
        } catch (Exception ignored) {
            // AngongGui 미설치 시 무시
        }
    }

    /** 플러그인 서버에서 받은 악세서리 데이터로 AccessoryScreen을 로컬에서 열기 */
    private static void parseAccessoryData(JsonObject obj) {
        JsonArray slots = obj.getAsJsonArray("slots");
        if (slots == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AccessoryInventory accInv = new AccessoryInventory();
        for (int i = 0; i < Math.min(slots.size(), AccessoryInventory.SIZE); i++) {
            var el = slots.get(i);
            if (el.isJsonNull()) continue;

            final int slot = i;

            // 새 포맷: JsonObject (itemId + registryId + cmd + displayName(JSON) + lore(JSON))
            if (el.isJsonObject()) {
                JsonObject slotData = el.getAsJsonObject();
                String itemId = slotData.has("itemId") ? slotData.get("itemId").getAsString() : null;
                int cmd = slotData.has("cmd") ? slotData.get("cmd").getAsInt() : 0;
                String registryId = slotData.has("registryId") ? slotData.get("registryId").getAsString() : null;
                String displayName = slotData.has("displayName") ? slotData.get("displayName").getAsString() : null;
                JsonArray loreArr = slotData.has("lore") ? slotData.getAsJsonArray("lore") : null;

                if (itemId != null) {
                    ResourceLocation loc = ResourceLocation.tryParse(itemId);
                    if (loc != null) {
                        BuiltInRegistries.ITEM.get(loc).ifPresent(ref -> {
                            ItemStack stack = new ItemStack(ref.value());
                            // CustomModelData 복원 (리소스팩 텍스처)
                            if (cmd != 0) {
                                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                                        new net.minecraft.world.item.component.CustomModelData(
                                                List.of((float) cmd), List.of(), List.of(), List.of()));
                            }
                            // registry_id 복원 → 커스텀 툴팁 연결용 (PublicBukkitValues 호환 형식)
                            if (registryId != null) {
                                net.minecraft.world.item.component.CustomData existing =
                                        stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                                net.minecraft.world.item.component.CustomData.EMPTY);
                                net.minecraft.nbt.CompoundTag tag = existing.copyTag();
                                net.minecraft.nbt.CompoundTag pbv = tag.contains("PublicBukkitValues")
                                        ? tag.getCompound("PublicBukkitValues")
                                        : new net.minecraft.nbt.CompoundTag();
                                pbv.putString("customdamagesystem:registry_id", registryId);
                                tag.put("PublicBukkitValues", pbv);
                                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                        net.minecraft.world.item.component.CustomData.of(tag));
                            }
                            // DisplayName 복원 — JSON Component 파싱 (Adventure 호환)
                            if (displayName != null) {
                                Component nameComp = parseComponentJson(displayName);
                                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, nameComp);
                            }
                            // Lore 복원 — JSON Component 파싱 (§ 코드 스타일 보존)
                            if (loreArr != null && !loreArr.isEmpty()) {
                                List<Component> loreLines = new ArrayList<>();
                                for (var loreLine : loreArr) {
                                    loreLines.add(parseComponentJson(loreLine.getAsString()));
                                }
                                stack.set(net.minecraft.core.component.DataComponents.LORE,
                                        new net.minecraft.world.item.component.ItemLore(loreLines));
                            }
                            accInv.setItem(slot, stack);
                        });
                    }
                }
            }
            // 구 포맷: String (material ID만) — 하위 호환
            else if (el.isJsonPrimitive()) {
                String itemId = el.getAsString();
                ResourceLocation loc = ResourceLocation.tryParse(itemId);
                if (loc != null) {
                    BuiltInRegistries.ITEM.get(loc).ifPresent(ref ->
                            accInv.setItem(slot, new ItemStack(ref.value())));
                }
            }
        }

        // 로컬에서 AccessoryMenu + AccessoryScreen 열기 (clientOnly 모드)
        int syncId = mc.player.containerMenu.containerId + 1;
        AccessoryMenu menu = new AccessoryMenu(syncId, mc.player.getInventory(), accInv);
        menu.setClientOnly(true);  // 서버 패킷 전송 없이 클라이언트에서만 슬롯 조작
        // containerMenu를 교체해야 슬롯 클릭(getCarried 등)이 정상 동작
        mc.player.containerMenu = menu;
        mc.setScreen(new AccessoryScreen(menu, mc.player.getInventory(),
                Component.translatable("ui.customdamagesystem.acc.title")));
    }

    /** 플러그인 서버에서 받은 악세서리 레지스트리를 클라이언트 AccessoryRegistry에 등록 */
    private static void parseAccessoryRegistrySync(JsonObject obj) {
        JsonArray entries = obj.getAsJsonArray("entries");
        if (entries == null) return;

        for (var el : entries) {
            JsonObject e = el.getAsJsonObject();
            String itemId = e.get("item").getAsString();
            String typeStr = e.get("type").getAsString();
            String registryId = e.has("registryId") ? e.get("registryId").getAsString() : null;
            int cmd = e.has("cmd") ? e.get("cmd").getAsInt() : 0;
            int str = e.has("str") ? e.get("str").getAsInt() : 0;
            int agi = e.has("agi") ? e.get("agi").getAsInt() : 0;
            int intel = e.has("int") ? e.get("int").getAsInt() : 0;
            int luk = e.has("luk") ? e.get("luk").getAsInt() : 0;

            AccessoryType accType;
            try {
                accType = AccessoryType.valueOf(typeStr);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)).ifPresent(ref ->
                    AccessoryRegistry.register(ref.value(),
                            new AccessoryDefinition(accType, str, agi, intel, luk)));

            // registryId 캐시 저장 (클라이언트 → 서버 전송 시 사용)
            if (registryId != null) {
                ClientAccessoryRegistryCache.put(itemId, cmd, registryId);
            }

            // 툴팁 레지스트리에 등록 (AngongGui 커스텀 툴팁용)
            try {
                Class<?> registry = Class.forName("org.zeripe.angonggui.client.tooltip.ItemTooltipRegistry");
                registry.getMethod("register", JsonObject.class).invoke(null, e);
                LOGGER.info("[CDS-Client] 툴팁 레지스트리 등록 성공: {}", registryId);
            } catch (Exception ex) {
                LOGGER.error("[CDS-Client] 툴팁 레지스트리 등록 실패: {}", ex.toString());
            }
        }
    }

    /**
     * JSON 문자열을 Minecraft Component로 파싱.
     * Adventure GsonComponentSerializer 형식(서버)과 호환.
     * 파싱 실패 시 legacy § 코드 수동 파싱으로 fallback.
     */
    private static Component parseComponentJson(String json) {
        if (json == null || json.isEmpty()) return Component.empty();
        // JSON Component 파싱 시도
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Component comp = Component.Serializer.fromJson(json, mc.level.registryAccess());
                if (comp != null) return comp;
            }
        } catch (Exception ignored) {}
        // fallback: § 코드를 Component 스타일로 변환
        return parseLegacyFormatting(json);
    }

    /** § 코드가 포함된 문자열을 올바른 Component 스타일 트리로 변환 */
    private static Component parseLegacyFormatting(String text) {
        net.minecraft.network.chat.MutableComponent result = Component.empty();
        net.minecraft.ChatFormatting[] formats = net.minecraft.ChatFormatting.values();
        net.minecraft.network.chat.Style current = net.minecraft.network.chat.Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\u00A7' && i + 1 < text.length()) {
                if (buf.length() > 0) {
                    result.append(Component.literal(buf.toString()).withStyle(current));
                    buf.setLength(0);
                }
                net.minecraft.ChatFormatting fmt = net.minecraft.ChatFormatting.getByCode(text.charAt(i + 1));
                if (fmt != null) {
                    if (fmt == net.minecraft.ChatFormatting.RESET) {
                        current = net.minecraft.network.chat.Style.EMPTY;
                    } else if (fmt.isColor()) {
                        current = net.minecraft.network.chat.Style.EMPTY.withColor(fmt);
                    } else {
                        current = current.applyFormat(fmt);
                    }
                }
                i++;
            } else {
                buf.append(text.charAt(i));
            }
        }
        if (buf.length() > 0) {
            result.append(Component.literal(buf.toString()).withStyle(current));
        }
        return result;
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
