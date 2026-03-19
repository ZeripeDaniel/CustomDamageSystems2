package org.zeripe.customdamagesystem.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AccessoryDataManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AccessoryDataManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, AccessoryInventory> inventories = new ConcurrentHashMap<>();
    private static Path storagePath;

    private AccessoryDataManager() {}

    public static void init(MinecraftServer server) {
        storagePath = server.getWorldPath(LevelResource.ROOT)
                .resolve("customdamagesystem").resolve("accessorydata");
        try {
            Files.createDirectories(storagePath);
            LOGGER.info("[AccessoryDataManager] 저장 경로 초기화: {}", storagePath);
        } catch (IOException e) {
            LOGGER.error("[AccessoryDataManager] 저장 경로 생성 실패: {}", e.getMessage());
        }
    }

    public static AccessoryInventory getOrLoad(UUID uuid) {
        return inventories.computeIfAbsent(uuid, k -> load(k));
    }

    public static void save(UUID uuid) {
        AccessoryInventory inv = inventories.get(uuid);
        if (inv == null) {
            LOGGER.warn("[AccessoryDataManager] save 호출됨 - 인벤토리 없음: {}", uuid);
            return;
        }
        if (storagePath == null) {
            LOGGER.warn("[AccessoryDataManager] save 호출됨 - storagePath 미초기화: {}", uuid);
            return;
        }

        JsonObject obj = new JsonObject();
        JsonArray slots = new JsonArray();
        int nonEmpty = 0;
        for (int i = 0; i < AccessoryInventory.SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                slots.add((String) null);
            } else {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                slots.add(itemId);
                nonEmpty++;
            }
        }
        obj.add("slots", slots);

        Path file = storagePath.resolve(uuid + ".json");
        try {
            Files.writeString(file, GSON.toJson(obj));
            LOGGER.debug("[AccessoryDataManager] 저장 완료 ({}개 아이템): {} -> {}", nonEmpty, uuid, file);
        } catch (IOException e) {
            LOGGER.error("[AccessoryDataManager] 저장 실패: {} - {}", uuid, e.getMessage());
        }
    }

    public static void remove(UUID uuid) {
        save(uuid);
        inventories.remove(uuid);
    }

    public static void saveAll() {
        for (UUID uuid : inventories.keySet()) {
            save(uuid);
        }
    }

    public static void shutdown() {
        LOGGER.info("[AccessoryDataManager] 종료 - {}명 저장 중", inventories.size());
        saveAll();
        inventories.clear();
        storagePath = null;
    }

    private static AccessoryInventory load(UUID uuid) {
        AccessoryInventory inv = new AccessoryInventory();
        if (storagePath == null) {
            LOGGER.warn("[AccessoryDataManager] load 호출됨 - storagePath 미초기화: {}", uuid);
            return inv;
        }

        Path file = storagePath.resolve(uuid + ".json");
        if (!Files.exists(file)) {
            LOGGER.debug("[AccessoryDataManager] 저장 파일 없음 (새 플레이어): {}", uuid);
            return inv;
        }

        try {
            String content = Files.readString(file);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            JsonArray slots = obj.getAsJsonArray("slots");
            int loaded = 0;
            for (int i = 0; i < Math.min(slots.size(), AccessoryInventory.SIZE); i++) {
                JsonElement el = slots.get(i);
                if (el.isJsonNull()) continue;
                String id = el.getAsString();
                ResourceLocation loc = ResourceLocation.parse(id);
                int slotIndex = i;
                BuiltInRegistries.ITEM.get(loc).ifPresent(ref -> {
                    Item item = ref.value();
                    if (item != Items.AIR) {
                        inv.setItem(slotIndex, new ItemStack(item));
                    }
                });
                loaded++;
            }
            LOGGER.info("[AccessoryDataManager] 로드 완료 ({}개 아이템): {}", loaded, uuid);
        } catch (Exception e) {
            LOGGER.error("[AccessoryDataManager] 로드 실패: {} - {}", uuid, e.getMessage());
        }

        return inv;
    }
}
