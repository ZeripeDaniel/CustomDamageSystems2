package org.zeripe.angongserverside.combat;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.zeripe.angongserverside.config.DamageSkinConfig;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 주도 데미지 스킨 관리자.
 *
 * - 서버 설정(JSON)으로 스킨 팩 등록/제거
 * - 플레이어별 소유(permission) 및 선택 관리
 * - 선택된 스킨은 주변 플레이어에게 브로드캐스트
 * - UUID→데이터 파일로 영속 저장
 */
public final class DamageSkinManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PLAYER_DATA_TYPE = new TypeToken<PlayerSkinData>() {}.getType();

    private final Logger logger;
    private final Path dataDir;
    private DamageSkinConfig config;

    /** 등록된 스킨 팩 (id → 설정) */
    private final Map<String, DamageSkinConfig.SkinPackEntry> registeredPacks = new LinkedHashMap<>();

    /** 플레이어별 스킨 데이터 */
    private final Map<UUID, PlayerSkinData> playerData = new ConcurrentHashMap<>();

    public DamageSkinManager(Path dataDir, DamageSkinConfig config, Logger logger) {
        this.dataDir = dataDir.resolve("damage_skins");
        this.config = config;
        this.logger = logger;
        loadPacks();
    }

    /** 설정 파일에서 스킨 팩 로드 */
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

    /** 설정 리로드 (런타임 중 서버 명령으로 호출 가능) */
    public void reload(DamageSkinConfig newConfig) {
        this.config = newConfig;
        loadPacks();
        // 제거된 스킨을 선택 중인 플레이어들 → "none" 으로 리셋
        for (var entry : playerData.entrySet()) {
            PlayerSkinData data = entry.getValue();
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

    /** 플레이어 접속 시 데이터 로드 */
    public void onPlayerJoin(UUID uuid) {
        PlayerSkinData data = loadPlayerData(uuid);
        // freeForAll 스킨 자동 부여
        for (var pack : registeredPacks.values()) {
            if (pack.freeForAll && !data.ownedSkins.contains(pack.id)) {
                data.ownedSkins.add(pack.id);
            }
        }
        playerData.put(uuid, data);
    }

    /** 플레이어 퇴장 시 저장 후 제거 */
    public void onPlayerQuit(UUID uuid) {
        PlayerSkinData data = playerData.remove(uuid);
        if (data != null) {
            savePlayerData(uuid, data);
        }
    }

    /** 플레이어가 소유한 스킨 목록 */
    public List<String> getOwnedSkins(UUID uuid) {
        PlayerSkinData data = playerData.get(uuid);
        if (data == null) return List.of();
        return Collections.unmodifiableList(data.ownedSkins);
    }

    /** 플레이어의 현재 선택 스킨 ID ("none" = 텍스트 모드) */
    public String getSelectedSkin(UUID uuid) {
        PlayerSkinData data = playerData.get(uuid);
        return data != null ? data.selectedSkin : "none";
    }

    /** 플레이어 스킨 선택 (소유한 스킨만 가능) */
    public boolean selectSkin(UUID uuid, String skinId) {
        PlayerSkinData data = playerData.get(uuid);
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

    /** 다음 스킨으로 순환 (소유 스킨 내에서) */
    public String cycleNext(UUID uuid) {
        PlayerSkinData data = playerData.get(uuid);
        if (data == null || data.ownedSkins.isEmpty()) return "none";

        List<String> cycle = new ArrayList<>();
        cycle.add("none"); // 텍스트 모드
        cycle.addAll(data.ownedSkins);

        int idx = cycle.indexOf(data.selectedSkin);
        idx = (idx + 1) % cycle.size();
        data.selectedSkin = cycle.get(idx);
        return data.selectedSkin;
    }

    // ── 권한 관리 (상점 연동용) ──

    /** 스킨 소유권 부여 */
    public boolean grantSkin(UUID uuid, String skinId) {
        if (!registeredPacks.containsKey(skinId)) return false;
        PlayerSkinData data = playerData.get(uuid);
        if (data == null) {
            data = loadPlayerData(uuid);
            playerData.put(uuid, data);
        }
        if (data.ownedSkins.contains(skinId)) return false;
        data.ownedSkins.add(skinId);
        savePlayerData(uuid, data);
        return true;
    }

    /** 스킨 소유권 제거 */
    public boolean revokeSkin(UUID uuid, String skinId) {
        PlayerSkinData data = playerData.get(uuid);
        if (data == null) {
            data = loadPlayerData(uuid);
            playerData.put(uuid, data);
        }
        boolean removed = data.ownedSkins.remove(skinId);
        if (removed && skinId.equals(data.selectedSkin)) {
            data.selectedSkin = "none";
        }
        if (removed) savePlayerData(uuid, data);
        return removed;
    }

    /** 스킨 소유 여부 확인 */
    public boolean hasSkin(UUID uuid, String skinId) {
        PlayerSkinData data = playerData.get(uuid);
        if (data == null) return false;
        return data.ownedSkins.contains(skinId);
    }

    // ── 전체 저장 (서버 종료 시) ──

    public void saveAll() {
        for (var entry : playerData.entrySet()) {
            savePlayerData(entry.getKey(), entry.getValue());
        }
        logger.info("[DamageSkinManager] {} 명의 스킨 데이터 저장 완료", playerData.size());
    }

    // ── JSON 빌더 (네트워크 전송용) ──

    /** 클라이언트에 보낼 스킨 목록 JSON */
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

        // 이 플레이어가 소유한 스킨 목록
        JsonArray ownedArr = new JsonArray();
        List<String> owned = getOwnedSkins(uuid);
        for (String s : owned) ownedArr.add(s);
        packet.add("owned", ownedArr);

        // 현재 선택 스킨
        packet.addProperty("selected", getSelectedSkin(uuid));

        return packet;
    }

    /** 스킨 변경 브로드캐스트 패킷 */
    public static JsonObject buildSkinChangePacket(UUID playerUuid, String skinId) {
        JsonObject packet = new JsonObject();
        packet.addProperty("action", "skin_change");
        packet.addProperty("uuid", playerUuid.toString());
        packet.addProperty("skinId", skinId);
        return packet;
    }

    // ── 영속 저장 ──

    private PlayerSkinData loadPlayerData(UUID uuid) {
        Path file = dataDir.resolve(uuid + ".json");
        if (Files.exists(file)) {
            try {
                PlayerSkinData data = GSON.fromJson(Files.readString(file), PLAYER_DATA_TYPE);
                if (data != null) {
                    if (data.ownedSkins == null) data.ownedSkins = new ArrayList<>();
                    if (data.selectedSkin == null) data.selectedSkin = "none";
                    return data;
                }
            } catch (Exception e) {
                logger.warn("[DamageSkinManager] 플레이어 스킨 데이터 로드 실패 {}: {}", uuid, e.getMessage());
            }
        }
        return new PlayerSkinData();
    }

    private void savePlayerData(UUID uuid, PlayerSkinData data) {
        try {
            Files.createDirectories(dataDir);
            Files.writeString(dataDir.resolve(uuid + ".json"), GSON.toJson(data));
        } catch (IOException e) {
            logger.warn("[DamageSkinManager] 플레이어 스킨 데이터 저장 실패 {}: {}", uuid, e.getMessage());
        }
    }

    /** 플레이어별 스킨 데이터 */
    private static class PlayerSkinData {
        List<String> ownedSkins = new ArrayList<>();
        String selectedSkin = "none";
    }
}
