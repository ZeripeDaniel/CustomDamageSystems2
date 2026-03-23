package org.zeripe.angongserverside.combat;

import com.google.gson.*;
import org.slf4j.Logger;
import org.zeripe.angongserverside.config.DamageSkinConfig;
import org.zeripe.angongserverside.storage.PlayerDataStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DamageSkinManager {

    private final Logger logger;
    private final PlayerDataStorage storage;
    private DamageSkinConfig config;

    private final Map<String, DamageSkinConfig.SkinPackEntry> registeredPacks = new LinkedHashMap<>();
    private final Map<UUID, SkinData> playerData = new ConcurrentHashMap<>();

    public DamageSkinManager(PlayerDataStorage storage, DamageSkinConfig config, Logger logger) {
        this.storage = storage;
        this.config = config;
        this.logger = logger;
        loadPacks();
    }

    private void loadPacks() {
        registeredPacks.clear();
        if (config.packs != null) {
            for (DamageSkinConfig.SkinPackEntry pack : config.packs) {
                if (pack.id != null && !pack.id.isBlank()) {
                    registeredPacks.put(pack.id, pack);
                }
            }
        }
        logger.info("[DamageSkinManager] 스킨 팩 {} 개 로드됨", registeredPacks.size());
    }

    public void reload(DamageSkinConfig newConfig) {
        this.config = newConfig;
        loadPacks();
        for (var entry : playerData.entrySet()) {
            SkinData data = entry.getValue();
            if (!"none".equals(data.selectedSkin) && !registeredPacks.containsKey(data.selectedSkin)) {
                data.selectedSkin = "none";
            }
            data.ownedSkins.removeIf(s -> !registeredPacks.containsKey(s));
        }
    }

    // ── 스킨 팩 조회 ──

    public Collection<DamageSkinConfig.SkinPackEntry> getRegisteredPacks() {
        return Collections.unmodifiableCollection(registeredPacks.values());
    }

    public DamageSkinConfig.SkinPackEntry getPack(String id) {
        return registeredPacks.get(id);
    }

    public boolean packExists(String id) {
        return registeredPacks.containsKey(id);
    }

    public int getCellWidth() { return config.cellWidth; }
    public int getCellHeight() { return config.cellHeight; }

    // ── 플레이어 스킨 데이터 ──

    public void onPlayerJoin(UUID uuid) {
        SkinData data = loadPlayerSkinData(uuid);
        for (var pack : registeredPacks.values()) {
            if (pack.freeForAll && !data.ownedSkins.contains(pack.id)) {
                data.ownedSkins.add(pack.id);
            }
        }
        playerData.put(uuid, data);
    }

    public void onPlayerQuit(UUID uuid) {
        SkinData data = playerData.remove(uuid);
        if (data != null) {
            savePlayerSkinData(uuid, data);
        }
    }

    public List<String> getOwnedSkins(UUID uuid) {
        SkinData data = playerData.get(uuid);
        if (data == null) return List.of();
        return Collections.unmodifiableList(data.ownedSkins);
    }

    public String getSelectedSkin(UUID uuid) {
        SkinData data = playerData.get(uuid);
        return data != null ? data.selectedSkin : "none";
    }

    public boolean selectSkin(UUID uuid, String skinId) {
        SkinData data = playerData.get(uuid);
        if (data == null) return false;

        if ("none".equals(skinId)) {
            data.selectedSkin = "none";
            return true;
        }
        if (!registeredPacks.containsKey(skinId)) return false;
        if (!data.ownedSkins.contains(skinId)) return false;

        data.selectedSkin = skinId;
        return true;
    }

    public String cycleNext(UUID uuid) {
        SkinData data = playerData.get(uuid);
        if (data == null || data.ownedSkins.isEmpty()) return "none";

        List<String> cycle = new ArrayList<>();
        cycle.add("none");
        cycle.addAll(data.ownedSkins);

        int idx = cycle.indexOf(data.selectedSkin);
        idx = (idx + 1) % cycle.size();
        data.selectedSkin = cycle.get(idx);
        return data.selectedSkin;
    }

    // ── 권한 관리 ──

    public boolean grantSkin(UUID uuid, String skinId) {
        if (!registeredPacks.containsKey(skinId)) return false;
        SkinData data = playerData.get(uuid);
        if (data == null) {
            data = loadPlayerSkinData(uuid);
            playerData.put(uuid, data);
        }
        if (data.ownedSkins.contains(skinId)) return false;
        data.ownedSkins.add(skinId);
        savePlayerSkinData(uuid, data);
        return true;
    }

    public boolean revokeSkin(UUID uuid, String skinId) {
        SkinData data = playerData.get(uuid);
        if (data == null) {
            data = loadPlayerSkinData(uuid);
            playerData.put(uuid, data);
        }
        boolean removed = data.ownedSkins.remove(skinId);
        if (removed && skinId.equals(data.selectedSkin)) {
            data.selectedSkin = "none";
        }
        if (removed) savePlayerSkinData(uuid, data);
        return removed;
    }

    public boolean hasSkin(UUID uuid, String skinId) {
        SkinData data = playerData.get(uuid);
        if (data == null) return false;
        return data.ownedSkins.contains(skinId);
    }

    // ── 전체 저장 ──

    public void saveAll() {
        for (var entry : playerData.entrySet()) {
            savePlayerSkinData(entry.getKey(), entry.getValue());
        }
        logger.info("[DamageSkinManager] {} 명의 스킨 데이터 저장 완료", playerData.size());
    }

    // ── JSON 빌더 (네트워크 전송용) ──

    public JsonObject buildSkinListPacket(UUID uuid) {
        JsonObject packet = new JsonObject();
        packet.addProperty("action", "skin_list");
        packet.addProperty("cellWidth", config.cellWidth);
        packet.addProperty("cellHeight", config.cellHeight);

        JsonArray packsArr = new JsonArray();
        for (var pack : registeredPacks.values()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", pack.id);
            p.addProperty("displayName", pack.displayName);
            p.addProperty("namespace", pack.namespace);
            p.addProperty("texturePath", pack.texturePath);
            p.addProperty("cellWidth", pack.cellWidth > 0 ? pack.cellWidth : config.cellWidth);
            p.addProperty("cellHeight", pack.cellHeight > 0 ? pack.cellHeight : config.cellHeight);
            packsArr.add(p);
        }
        packet.add("packs", packsArr);

        JsonArray ownedArr = new JsonArray();
        List<String> owned = getOwnedSkins(uuid);
        for (String s : owned) ownedArr.add(s);
        packet.add("owned", ownedArr);

        packet.addProperty("selected", getSelectedSkin(uuid));

        return packet;
    }

    public static JsonObject buildSkinChangePacket(UUID playerUuid, String skinId) {
        JsonObject packet = new JsonObject();
        packet.addProperty("action", "skin_change");
        packet.addProperty("uuid", playerUuid.toString());
        packet.addProperty("skinId", skinId);
        return packet;
    }

    // ── Storage 위임 ──

    private SkinData loadPlayerSkinData(UUID uuid) {
        List<String> owned = storage.loadOwnedSkins(uuid);
        String selected = storage.loadSelectedSkin(uuid);
        SkinData data = new SkinData();
        data.ownedSkins = owned != null ? new ArrayList<>(owned) : new ArrayList<>();
        data.selectedSkin = selected != null ? selected : "none";
        return data;
    }

    private void savePlayerSkinData(UUID uuid, SkinData data) {
        storage.saveSkinData(uuid, data.ownedSkins, data.selectedSkin);
    }

    private static class SkinData {
        List<String> ownedSkins = new ArrayList<>();
        String selectedSkin = "none";
    }
}
