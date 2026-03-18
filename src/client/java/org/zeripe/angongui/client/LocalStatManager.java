package org.zeripe.angongui.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 서버가 없는 환경에서도 작동하도록 로컬 HP/MP 상태를 유지한다.
 */
public final class LocalStatManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_MAX_HP = 10;
    private static final int DEFAULT_MAX_MP = 0;

    private static boolean active = false;
    private static int maxHp = DEFAULT_MAX_HP;
    private static int currentHp = DEFAULT_MAX_HP;
    private static int maxMp = DEFAULT_MAX_MP;
    private static int currentMp = DEFAULT_MAX_MP;
    private static float lastVanillaHp = -1f;
    private static float lastVanillaMaxHp = -1f;
    private static String worldKey = null;
    private static boolean fileDirty = false;

    private LocalStatManager() {}

    public static void activate() {
        worldKey = null;
        lastVanillaHp = -1f;
        maxHp = DEFAULT_MAX_HP;
        currentHp = DEFAULT_MAX_HP;
        maxMp = DEFAULT_MAX_MP;
        currentMp = DEFAULT_MAX_MP;
        fileDirty = false;
        active = true;
        push();
    }

    public static void deactivate() {
        if (active && fileDirty) saveToFile();
        active = false;
        lastVanillaHp = -1f;
        worldKey = null;
        fileDirty = false;
    }

    public static void tick(Minecraft mc) {
        if (!active || mc.player == null) return;

        if (worldKey == null) {
            worldKey = resolveWorldKey(mc);
            loadFromFile(mc);
            push();
        }

        float vh = mc.player.getHealth();
        float vhm = mc.player.getMaxHealth();

        if (lastVanillaHp < 0f) {
            lastVanillaHp = vh;
            lastVanillaMaxHp = vhm;
            if (vhm > 0f) currentHp = Math.max(0, Math.min(maxHp, Math.round(vh / vhm * maxHp)));
            push();
            return;
        }

        if (Math.abs(vh - lastVanillaHp) > 0.01f) {
            float delta = vh - lastVanillaHp;
            if (lastVanillaMaxHp > 0f) {
                int customDelta = Math.round(delta / lastVanillaMaxHp * maxHp);
                currentHp = Math.max(0, Math.min(maxHp, currentHp + customDelta));
            }
            lastVanillaHp = vh;
            lastVanillaMaxHp = vhm;
            fileDirty = true;
            push();
            saveToFile(mc);
        }
    }

    private static void push() {
        ClientState.get().updateHpMp(currentHp, maxHp, currentMp, maxMp);
    }

    private static String resolveWorldKey(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) {
            String name = mc.getSingleplayerServer().getWorldData().getLevelName();
            return "sp_" + name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        }
        if (mc.getCurrentServer() != null) {
            return "mp_" + mc.getCurrentServer().ip.replace(":", "_").replace("/", "_");
        }
        return "default";
    }

    private static Path filePath(Minecraft mc) {
        String key = worldKey != null ? worldKey : resolveWorldKey(mc);
        return mc.gameDirectory.toPath()
                .resolve("config")
                .resolve("customdamagesystem")
                .resolve("local_stats_" + key + ".json");
    }

    private static void loadFromFile(Minecraft mc) {
        Path path = filePath(mc);
        if (!Files.exists(path)) return;
        try {
            JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            maxHp = obj.has("maxHp") ? obj.get("maxHp").getAsInt() : DEFAULT_MAX_HP;
            currentHp = obj.has("currentHp") ? obj.get("currentHp").getAsInt() : maxHp;
            maxMp = obj.has("maxMp") ? obj.get("maxMp").getAsInt() : DEFAULT_MAX_MP;
            currentMp = obj.has("currentMp") ? obj.get("currentMp").getAsInt() : DEFAULT_MAX_MP;
            currentHp = Math.max(0, Math.min(maxHp, currentHp));
            currentMp = Math.max(0, Math.min(maxMp, currentMp));
        } catch (Exception ignored) {
            maxHp = DEFAULT_MAX_HP;
            currentHp = DEFAULT_MAX_HP;
        }
    }

    private static void saveToFile(Minecraft mc) {
        Path path = filePath(mc);
        try {
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("maxHp", maxHp);
            obj.addProperty("currentHp", currentHp);
            obj.addProperty("maxMp", maxMp);
            obj.addProperty("currentMp", currentMp);
            Files.writeString(path, GSON.toJson(obj));
            fileDirty = false;
        } catch (Exception ignored) {
        }
    }

    private static void saveToFile() {
        saveToFile(Minecraft.getInstance());
    }
}
