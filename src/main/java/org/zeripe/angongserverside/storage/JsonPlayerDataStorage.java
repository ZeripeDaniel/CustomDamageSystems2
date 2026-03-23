package org.zeripe.angongserverside.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.zeripe.angongserverside.stats.PlayerStatData;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JsonPlayerDataStorage implements PlayerDataStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path statDir;
    private final Path accessoryDir;
    private final Path questDir;
    private final Path skinDir;
    private final Logger logger;

    public JsonPlayerDataStorage(Path worldRoot, Path configDir, Logger logger) {
        this.logger = logger;
        Path base = worldRoot.resolve("customdamagesystem");
        this.statDir = base.resolve("statdata");
        this.accessoryDir = base.resolve("accessorydata");
        this.questDir = base.resolve("questdata");
        this.skinDir = configDir.resolve("damage_skins");
    }

    @Override
    public void init() {
        try {
            Files.createDirectories(statDir);
            Files.createDirectories(accessoryDir);
            Files.createDirectories(questDir);
            Files.createDirectories(skinDir);
        } catch (IOException e) {
            logger.error("[JsonStorage] 디렉토리 생성 실패: {}", e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        // no-op
    }

    // === Stats ===

    @Override
    public PlayerStatData loadStats(String uuid, String name) {
        Path p = statDir.resolve(uuid + ".json");
        if (Files.exists(p)) {
            try {
                PlayerStatData d = GSON.fromJson(Files.readString(p), PlayerStatData.class);
                if (d != null) return d;
            } catch (Exception e) {
                logger.warn("[JsonStorage] 스탯 로드 실패 {}: {}", uuid, e.getMessage());
            }
        }
        PlayerStatData fresh = PlayerStatData.defaultFor(uuid, name);
        saveStats(fresh);
        return fresh;
    }

    @Override
    public void saveStats(PlayerStatData d) {
        try {
            Files.createDirectories(statDir);
            Files.writeString(statDir.resolve(d.uuid + ".json"), GSON.toJson(d));
        } catch (IOException e) {
            logger.warn("[JsonStorage] 스탯 저장 실패 {}: {}", d.uuid, e.getMessage());
        }
    }

    @Override
    public List<PlayerStatData> loadAllStats() {
        List<PlayerStatData> result = new ArrayList<>();
        try (var stream = Files.list(statDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    PlayerStatData d = GSON.fromJson(Files.readString(p), PlayerStatData.class);
                    if (d != null) result.add(d);
                } catch (Exception e) {
                    logger.warn("[JsonStorage] 스탯 파일 읽기 실패: {} - {}", p.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warn("[JsonStorage] 스탯 디렉토리 스캔 실패: {}", e.getMessage());
        }
        return result;
    }

    // === Accessories ===

    @Override
    public String loadAccessoryJson(UUID uuid) {
        Path file = accessoryDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return null;
        try {
            return Files.readString(file);
        } catch (Exception e) {
            logger.error("[JsonStorage] 악세서리 로드 실패: {} - {}", uuid, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveAccessoryJson(UUID uuid, String json) {
        try {
            Files.createDirectories(accessoryDir);
            Files.writeString(accessoryDir.resolve(uuid + ".json"), json);
        } catch (IOException e) {
            logger.error("[JsonStorage] 악세서리 저장 실패: {} - {}", uuid, e.getMessage());
        }
    }

    // === Quest Data ===

    @Override
    public String loadQuestDataJson(UUID uuid) {
        Path file = questDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return null;
        try {
            return Files.readString(file);
        } catch (Exception e) {
            logger.error("[JsonStorage] 퀘스트 데이터 로드 실패: {} - {}", uuid, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveQuestDataJson(UUID uuid, String json) {
        try {
            Files.createDirectories(questDir);
            Files.writeString(questDir.resolve(uuid + ".json"), json);
        } catch (IOException e) {
            logger.error("[JsonStorage] 퀘스트 데이터 저장 실패: {} - {}", uuid, e.getMessage());
        }
    }

    // === Damage Skins ===

    @Override
    public List<String> loadOwnedSkins(UUID uuid) {
        SkinData data = loadSkinFile(uuid);
        return data.ownedSkins;
    }

    @Override
    public String loadSelectedSkin(UUID uuid) {
        SkinData data = loadSkinFile(uuid);
        return data.selectedSkin;
    }

    @Override
    public void saveSkinData(UUID uuid, List<String> ownedSkins, String selectedSkin) {
        SkinData data = new SkinData();
        data.ownedSkins = ownedSkins != null ? new ArrayList<>(ownedSkins) : new ArrayList<>();
        data.selectedSkin = selectedSkin != null ? selectedSkin : "none";
        try {
            Files.createDirectories(skinDir);
            Files.writeString(skinDir.resolve(uuid + ".json"), GSON.toJson(data));
        } catch (IOException e) {
            logger.warn("[JsonStorage] 스킨 저장 실패 {}: {}", uuid, e.getMessage());
        }
    }

    private SkinData loadSkinFile(UUID uuid) {
        Path file = skinDir.resolve(uuid + ".json");
        if (Files.exists(file)) {
            try {
                SkinData data = GSON.fromJson(Files.readString(file), SkinData.class);
                if (data != null) {
                    if (data.ownedSkins == null) data.ownedSkins = new ArrayList<>();
                    if (data.selectedSkin == null) data.selectedSkin = "none";
                    return data;
                }
            } catch (Exception e) {
                logger.warn("[JsonStorage] 스킨 로드 실패 {}: {}", uuid, e.getMessage());
            }
        }
        return new SkinData();
    }

    private static class SkinData {
        List<String> ownedSkins = new ArrayList<>();
        String selectedSkin = "none";
    }
}
