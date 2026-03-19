package org.zeripe.angongserverside.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EquipmentSlot;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Item base-stat override config.
 * JSON values define base stats for mapped items and can ignore vanilla item attributes.
 */
public final class EquipmentStatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "customdamagesystem-item-stats.json";

    public String _comment = "Defines item base stats by item id. If overrideVanillaMainhand/Armor is true, vanilla attributes are ignored for that item.";
    public String _usage = "Edit values and restart server. Additional random/enchant values can still be handled via NBT/custom logic.";

    public List<ItemEntry> items = new ArrayList<>(List.of(
            ItemEntry.woodenSwordExample(),
            ItemEntry.netheriteSwordExample(),
            ItemEntry.leatherHelmetExample(),
            ItemEntry.woodenRingExample(),
            ItemEntry.woodenNecklaceExample(),
            ItemEntry.woodenEarringExample()
    ));

    public static EquipmentStatConfig load(Logger logger) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                EquipmentStatConfig loaded = GSON.fromJson(Files.readString(path), EquipmentStatConfig.class);
                if (loaded != null) {
                    loaded.ensureDocs();
                    if (loaded.items == null) loaded.items = new ArrayList<>();
                    Files.writeString(path, GSON.toJson(loaded));
                    return loaded;
                }
            } catch (Exception e) {
                logger.warn("[EquipmentStatConfig] failed to load config: {}", e.getMessage());
            }
        }

        EquipmentStatConfig defaults = new EquipmentStatConfig();
        defaults.ensureDocs();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(defaults));
            logger.info("[EquipmentStatConfig] default config created: {}", path);
        } catch (IOException e) {
            logger.warn("[EquipmentStatConfig] failed to create config: {}", e.getMessage());
        }
        return defaults;
    }

    public ItemEntry find(String itemId, EquipmentSlot slot) {
        if (itemId == null || items == null || items.isEmpty()) return null;
        String slotName = slot.getName().toLowerCase();
        for (ItemEntry entry : items) {
            if (entry == null || entry.itemId == null) continue;
            if (!entry.itemId.equals(itemId)) continue;
            if (entry.slots == null || entry.slots.isEmpty()) return entry;
            for (String s : entry.slots) {
                if (s != null && s.equalsIgnoreCase(slotName)) return entry;
            }
        }
        return null;
    }

    private void ensureDocs() {
        if (_comment == null || _comment.isBlank()) {
            _comment = "Defines item base stats by item id. If overrideVanillaMainhand/Armor is true, vanilla attributes are ignored for that item.";
        }
        if (_usage == null || _usage.isBlank()) {
            _usage = "Edit values and restart server. Additional random/enchant values can still be handled via NBT/custom logic.";
        }
    }

    public static final class ItemEntry {
        public String itemId;
        public List<String> slots = new ArrayList<>();
        public double itemLevel = 0.0;
        public int itemLevelSlot = 0;

        public boolean overrideVanillaMainhand = true;
        public boolean overrideVanillaArmor = true;

        public double attack = 0.0;
        public double magicAttack = 0.0;
        public double defense = 0.0;
        public double hp = 0.0;
        public double critRate = 0.0;
        public double critDamage = 0.0;
        public double attackSpeed = 0.0;
        public double cooldownReduction = 0.0;
        public double moveSpeed = 0.0;
        public int strength = 0;
        public int agility = 0;
        public int intelligence = 0;
        public int luck = 0;
        public int weapons = 0;

        private static ItemEntry woodenSwordExample() {
            ItemEntry e = new ItemEntry();
            e.itemId = "minecraft:wooden_sword";
            e.slots = new ArrayList<>(List.of("mainhand"));
            e.itemLevel = 1.0;
            e.itemLevelSlot = 1;
            e.overrideVanillaMainhand = true;
            e.overrideVanillaArmor = false;
            e.attack = 8.0;
            e.critRate = 2.0;
            e.critDamage = 10.0;
            e.attackSpeed = 4.0;
            e.weapons = 1;
            return e;
        }

        private static ItemEntry netheriteSwordExample() {
            ItemEntry e = new ItemEntry();
            e.itemId = "minecraft:netherite_sword";
            e.slots = new ArrayList<>(List.of("mainhand"));
            e.itemLevel = 50.0;
            e.itemLevelSlot = 1;
            e.overrideVanillaMainhand = true;
            e.overrideVanillaArmor = false;
            e.attack = 120.0;
            e.magicAttack = 30.0;
            e.critRate = 8.0;
            e.critDamage = 40.0;
            e.attackSpeed = 4.0;
            e.strength = 10;
            e.agility = 5;
            e.weapons = 1;
            return e;
        }

        private static ItemEntry leatherHelmetExample() {
            ItemEntry e = new ItemEntry();
            e.itemId = "minecraft:leather_helmet";
            e.slots = new ArrayList<>(List.of("head"));
            e.itemLevel = 5.0;
            e.itemLevelSlot = 2;
            e.overrideVanillaMainhand = false;
            e.overrideVanillaArmor = true;
            e.defense = 4.0;
            e.hp = 5.0;
            return e;
        }

        private static ItemEntry woodenRingExample() {
            ItemEntry e = new ItemEntry();
            e.itemId = "customdamagesystem:wooden_ring";
            e.slots = new ArrayList<>(List.of("ring"));
            e.itemLevel = 1.0;
            e.strength = 2;
            e.luck = 1;
            return e;
        }

        private static ItemEntry woodenNecklaceExample() {
            ItemEntry e = new ItemEntry();
            e.itemId = "customdamagesystem:wooden_necklace";
            e.slots = new ArrayList<>(List.of("necklace"));
            e.itemLevel = 1.0;
            e.intelligence = 2;
            e.agility = 1;
            return e;
        }

        private static ItemEntry woodenEarringExample() {
            ItemEntry e = new ItemEntry();
            e.itemId = "customdamagesystem:wooden_earring";
            e.slots = new ArrayList<>(List.of("earring"));
            e.itemLevel = 1.0;
            e.agility = 2;
            e.luck = 1;
            return e;
        }
    }
}
