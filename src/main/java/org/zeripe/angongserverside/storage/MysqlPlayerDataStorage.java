package org.zeripe.angongserverside.storage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.zeripe.angongserverside.stats.PlayerStatData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class MysqlPlayerDataStorage implements PlayerDataStorage {

    private final DatabaseManager db;
    private final String tablePrefix;
    private final Logger logger;
    private final Gson gson;

    private String statsTable;
    private String accessoriesTable;
    private String questsTable;
    private String skinsTable;

    public MysqlPlayerDataStorage(DatabaseManager db, String tablePrefix, Logger logger) {
        this.db = db;
        this.tablePrefix = tablePrefix;
        this.logger = logger;
        this.gson = new Gson();
        this.statsTable = tablePrefix + "player_stats";
        this.accessoriesTable = tablePrefix + "player_accessories";
        this.questsTable = tablePrefix + "player_quests";
        this.skinsTable = tablePrefix + "player_damage_skins";
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void init() {
        createStatsTable();
        createAccessoriesTable();
        createQuestsTable();
        createSkinsTable();
    }

    @Override
    public void shutdown() {
        logger.info("[MysqlPlayerDataStorage] Shutdown requested. DatabaseManager handles pool closure separately.");
    }

    // =========================================================================
    // Stats
    // =========================================================================

    @Override
    public PlayerStatData loadStats(String uuid, String name) {
        String sql = "SELECT * FROM " + statsTable + " WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToStatData(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("[MysqlPlayerDataStorage] Failed to load stats for uuid={}: {}", uuid, e.getMessage(), e);
        }

        // Not found - create default and persist
        PlayerStatData data = PlayerStatData.defaultFor(uuid, name);
        saveStats(data);
        return data;
    }

    @Override
    public void saveStats(PlayerStatData data) {
        String sql = "INSERT INTO " + statsTable + " ("
                + "uuid, name, "
                + "item_level, item_level_slot1, item_level_slot2, item_level_slot3, item_level_slot4, "
                + "equip_strength, equip_agility, equip_intelligence, equip_luck, "
                + "equip_hp, equip_defense, equip_attack, equip_magic_attack, "
                + "equip_crit_rate, equip_crit_damage, equip_armor_penetration, "
                + "equip_bonus_damage, equip_elemental_multiplier, equip_life_steal, "
                + "equip_attack_multiplier, equip_magic_attack_multiplier, "
                + "equip_move_speed, equip_buff_duration, equip_cooldown_reduction, equip_clear_gold_bonus, "
                + "gold, current_hp, current_mp"
                + ") VALUES ("
                + "?, ?, "
                + "?, ?, ?, ?, ?, "
                + "?, ?, ?, ?, "
                + "?, ?, ?, ?, "
                + "?, ?, ?, "
                + "?, ?, ?, "
                + "?, ?, "
                + "?, ?, ?, ?, "
                + "?, ?, ?"
                + ") ON DUPLICATE KEY UPDATE "
                + "name = VALUES(name), "
                + "item_level = VALUES(item_level), "
                + "item_level_slot1 = VALUES(item_level_slot1), "
                + "item_level_slot2 = VALUES(item_level_slot2), "
                + "item_level_slot3 = VALUES(item_level_slot3), "
                + "item_level_slot4 = VALUES(item_level_slot4), "
                + "equip_strength = VALUES(equip_strength), "
                + "equip_agility = VALUES(equip_agility), "
                + "equip_intelligence = VALUES(equip_intelligence), "
                + "equip_luck = VALUES(equip_luck), "
                + "equip_hp = VALUES(equip_hp), "
                + "equip_defense = VALUES(equip_defense), "
                + "equip_attack = VALUES(equip_attack), "
                + "equip_magic_attack = VALUES(equip_magic_attack), "
                + "equip_crit_rate = VALUES(equip_crit_rate), "
                + "equip_crit_damage = VALUES(equip_crit_damage), "
                + "equip_armor_penetration = VALUES(equip_armor_penetration), "
                + "equip_bonus_damage = VALUES(equip_bonus_damage), "
                + "equip_elemental_multiplier = VALUES(equip_elemental_multiplier), "
                + "equip_life_steal = VALUES(equip_life_steal), "
                + "equip_attack_multiplier = VALUES(equip_attack_multiplier), "
                + "equip_magic_attack_multiplier = VALUES(equip_magic_attack_multiplier), "
                + "equip_move_speed = VALUES(equip_move_speed), "
                + "equip_buff_duration = VALUES(equip_buff_duration), "
                + "equip_cooldown_reduction = VALUES(equip_cooldown_reduction), "
                + "equip_clear_gold_bonus = VALUES(equip_clear_gold_bonus), "
                + "gold = VALUES(gold), "
                + "current_hp = VALUES(current_hp), "
                + "current_mp = VALUES(current_mp)";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindStatData(ps, data);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("[MysqlPlayerDataStorage] Failed to save stats for uuid={}: {}", data.uuid, e.getMessage(), e);
        }
    }

    @Override
    public List<PlayerStatData> loadAllStats() {
        List<PlayerStatData> result = new ArrayList<>();
        String sql = "SELECT * FROM " + statsTable;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapResultSetToStatData(rs));
            }
        } catch (SQLException e) {
            logger.error("[MySQL] 전체 스탯 로드 실패: {}", e.getMessage());
        }
        return result;
    }

    // =========================================================================
    // Accessories
    // =========================================================================

    @Override
    public String loadAccessoryJson(UUID uuid) {
        String sql = "SELECT slot_index, item_id, registry_id, custom_model_data, item_data "
                + "FROM " + accessoriesTable + " WHERE uuid = ? ORDER BY slot_index ASC";

        JsonArray slots = new JsonArray();
        // Pre-fill with nulls for potential sparse indexes
        int maxSlot = -1;

        List<SlotEntry> entries = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SlotEntry entry = new SlotEntry();
                    entry.slotIndex = rs.getInt("slot_index");
                    entry.itemId = rs.getString("item_id");
                    entry.registryId = rs.getString("registry_id");
                    entry.customModelData = rs.getInt("custom_model_data");
                    entry.itemData = rs.getString("item_data");
                    entries.add(entry);
                    if (entry.slotIndex > maxSlot) {
                        maxSlot = entry.slotIndex;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("[MysqlPlayerDataStorage] Failed to load accessories for uuid={}: {}", uuid, e.getMessage(), e);
            return buildEmptyAccessoryJson();
        }

        // Build a fixed-size array up to maxSlot (or empty)
        if (entries.isEmpty()) {
            return buildEmptyAccessoryJson();
        }

        for (int i = 0; i <= maxSlot; i++) {
            slots.add(com.google.gson.JsonNull.INSTANCE);
        }
        for (SlotEntry entry : entries) {
            JsonObject obj = new JsonObject();
            obj.addProperty("itemId", entry.itemId);
            obj.addProperty("registryId", entry.registryId);
            obj.addProperty("cmd", entry.customModelData);
            obj.addProperty("itemBase64", entry.itemData != null ? entry.itemData : "");
            slots.set(entry.slotIndex, obj);
        }

        JsonObject root = new JsonObject();
        root.add("slots", slots);
        return gson.toJson(root);
    }

    @Override
    public void saveAccessoryJson(UUID uuid, String json) {
        if (json == null || json.isEmpty()) {
            return;
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            logger.warn("[MysqlPlayerDataStorage] Invalid accessory JSON for uuid={}: {}", uuid, e.getMessage());
            return;
        }

        JsonArray slots = root.has("slots") ? root.getAsJsonArray("slots") : null;
        if (slots == null) {
            logger.warn("[MysqlPlayerDataStorage] Accessory JSON missing 'slots' array for uuid={}", uuid);
            return;
        }

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete existing rows
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM " + accessoriesTable + " WHERE uuid = ?")) {
                    del.setString(1, uuid.toString());
                    del.executeUpdate();
                }

                // Insert non-null slots
                String insertSql = "INSERT INTO " + accessoriesTable
                        + " (uuid, slot_index, item_id, registry_id, custom_model_data, item_data) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    for (int i = 0; i < slots.size(); i++) {
                        JsonElement elem = slots.get(i);
                        if (elem == null || elem.isJsonNull()) {
                            continue;
                        }
                        JsonObject slot = elem.getAsJsonObject();
                        ins.setString(1, uuid.toString());
                        ins.setInt(2, i);
                        ins.setString(3, getStringOrDefault(slot, "itemId", ""));
                        ins.setString(4, getStringOrDefault(slot, "registryId", ""));
                        ins.setInt(5, getIntOrDefault(slot, "cmd", 0));
                        ins.setString(6, getStringOrDefault(slot, "itemBase64", ""));
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("[MysqlPlayerDataStorage] Failed to save accessories for uuid={}: {}", uuid, e.getMessage(), e);
        }
    }

    // =========================================================================
    // Quest Data
    // =========================================================================

    @Override
    public String loadQuestDataJson(UUID uuid) {
        String sql = "SELECT quest_data FROM " + questsTable + " WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("quest_data");
            }
        } catch (SQLException e) {
            logger.error("[MysqlStorage] 퀘스트 데이터 로드 실패: {} - {}", uuid, e.getMessage());
        }
        return null;
    }

    @Override
    public void saveQuestDataJson(UUID uuid, String json) {
        String sql = "INSERT INTO " + questsTable + " (uuid, quest_data) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE quest_data = VALUES(quest_data)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("[MysqlStorage] 퀘스트 데이터 저장 실패: {} - {}", uuid, e.getMessage());
        }
    }

    private void createQuestsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + questsTable + " ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "quest_data JSON NOT NULL, "
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (uuid)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("[MysqlStorage] 퀘스트 테이블 생성 실패: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Damage Skins
    // =========================================================================

    @Override
    public List<String> loadOwnedSkins(UUID uuid) {
        String sql = "SELECT owned_skins FROM " + skinsTable + " WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return parseJsonStringArray(rs.getString("owned_skins"));
                }
            }
        } catch (SQLException e) {
            logger.error("[MysqlPlayerDataStorage] Failed to load owned skins for uuid={}: {}", uuid, e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    @Override
    public String loadSelectedSkin(UUID uuid) {
        String sql = "SELECT selected_skin FROM " + skinsTable + " WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String skin = rs.getString("selected_skin");
                    return skin != null ? skin : "none";
                }
            }
        } catch (SQLException e) {
            logger.error("[MysqlPlayerDataStorage] Failed to load selected skin for uuid={}: {}", uuid, e.getMessage(), e);
        }
        return "none";
    }

    @Override
    public void saveSkinData(UUID uuid, List<String> ownedSkins, String selectedSkin) {
        String sql = "INSERT INTO " + skinsTable + " (uuid, owned_skins, selected_skin) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE owned_skins = VALUES(owned_skins), selected_skin = VALUES(selected_skin)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, gson.toJson(ownedSkins != null ? ownedSkins : Collections.emptyList()));
            ps.setString(3, selectedSkin != null ? selectedSkin : "none");
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("[MysqlPlayerDataStorage] Failed to save skin data for uuid={}: {}", uuid, e.getMessage(), e);
        }
    }

    // =========================================================================
    // DDL - Table Creation
    // =========================================================================

    private void createStatsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + statsTable + " ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "name VARCHAR(64) NOT NULL DEFAULT '', "
                + "item_level DOUBLE NOT NULL DEFAULT 0.0, "
                + "item_level_slot1 DOUBLE NOT NULL DEFAULT 0.0, "
                + "item_level_slot2 DOUBLE NOT NULL DEFAULT 0.0, "
                + "item_level_slot3 DOUBLE NOT NULL DEFAULT 0.0, "
                + "item_level_slot4 DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_strength INT NOT NULL DEFAULT 0, "
                + "equip_agility INT NOT NULL DEFAULT 0, "
                + "equip_intelligence INT NOT NULL DEFAULT 0, "
                + "equip_luck INT NOT NULL DEFAULT 0, "
                + "equip_hp INT NOT NULL DEFAULT 0, "
                + "equip_defense INT NOT NULL DEFAULT 0, "
                + "equip_attack INT NOT NULL DEFAULT 0, "
                + "equip_magic_attack INT NOT NULL DEFAULT 0, "
                + "equip_crit_rate DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_crit_damage DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_armor_penetration DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_bonus_damage INT NOT NULL DEFAULT 0, "
                + "equip_elemental_multiplier DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_life_steal DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_attack_multiplier DOUBLE NOT NULL DEFAULT 100.0, "
                + "equip_magic_attack_multiplier DOUBLE NOT NULL DEFAULT 100.0, "
                + "equip_move_speed DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_buff_duration DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_cooldown_reduction DOUBLE NOT NULL DEFAULT 0.0, "
                + "equip_clear_gold_bonus DOUBLE NOT NULL DEFAULT 0.0, "
                + "gold BIGINT NOT NULL DEFAULT 0, "
                + "current_hp INT NOT NULL DEFAULT 10, "
                + "current_mp INT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (uuid), "
                + "INDEX idx_name (name)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        executeUpdate(sql, "player_stats");
    }

    private void createAccessoriesTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + accessoriesTable + " ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "slot_index TINYINT NOT NULL, "
                + "item_id VARCHAR(128) NOT NULL DEFAULT '', "
                + "registry_id VARCHAR(128) NOT NULL DEFAULT '', "
                + "custom_model_data INT NOT NULL DEFAULT 0, "
                + "item_data MEDIUMTEXT, "
                + "PRIMARY KEY (uuid, slot_index)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        executeUpdate(sql, "player_accessories");
    }

    private void createSkinsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + skinsTable + " ("
                + "uuid VARCHAR(36) NOT NULL, "
                + "owned_skins TEXT DEFAULT ('[]'), "
                + "selected_skin VARCHAR(64) NOT NULL DEFAULT 'none', "
                + "PRIMARY KEY (uuid)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        executeUpdate(sql, "player_damage_skins");
    }

    private void executeUpdate(String sql, String tableName) {
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            logger.info("[MysqlPlayerDataStorage] Table '{}' ensured.", tableName);
        } catch (SQLException e) {
            logger.error("[MysqlPlayerDataStorage] Failed to create table '{}': {}", tableName, e.getMessage(), e);
        }
    }

    // =========================================================================
    // Mapping Helpers
    // =========================================================================

    private PlayerStatData mapResultSetToStatData(ResultSet rs) throws SQLException {
        PlayerStatData d = new PlayerStatData();
        d.uuid = rs.getString("uuid");
        d.name = rs.getString("name");

        d.itemLevel = rs.getDouble("item_level");
        d.itemLevelSlot1 = rs.getDouble("item_level_slot1");
        d.itemLevelSlot2 = rs.getDouble("item_level_slot2");
        d.itemLevelSlot3 = rs.getDouble("item_level_slot3");
        d.itemLevelSlot4 = rs.getDouble("item_level_slot4");

        d.equipStrength = rs.getInt("equip_strength");
        d.equipAgility = rs.getInt("equip_agility");
        d.equipIntelligence = rs.getInt("equip_intelligence");
        d.equipLuck = rs.getInt("equip_luck");

        d.equipHp = rs.getInt("equip_hp");
        d.equipDefense = rs.getInt("equip_defense");
        d.equipAttack = rs.getInt("equip_attack");
        d.equipMagicAttack = rs.getInt("equip_magic_attack");
        d.equipCritRate = rs.getDouble("equip_crit_rate");
        d.equipCritDamage = rs.getDouble("equip_crit_damage");
        d.equipArmorPenetration = rs.getDouble("equip_armor_penetration");
        d.equipBonusDamage = rs.getInt("equip_bonus_damage");
        d.equipElementalMultiplier = rs.getDouble("equip_elemental_multiplier");
        d.equipLifeSteal = rs.getDouble("equip_life_steal");
        d.equipAttackMultiplier = rs.getDouble("equip_attack_multiplier");
        d.equipMagicAttackMultiplier = rs.getDouble("equip_magic_attack_multiplier");
        d.equipMoveSpeed = rs.getDouble("equip_move_speed");
        d.equipBuffDuration = rs.getDouble("equip_buff_duration");
        d.equipCooldownReduction = rs.getDouble("equip_cooldown_reduction");
        d.equipClearGoldBonus = rs.getDouble("equip_clear_gold_bonus");

        d.gold = rs.getLong("gold");
        d.currentHp = rs.getInt("current_hp");
        d.currentMp = rs.getInt("current_mp");

        return d;
    }

    private void bindStatData(PreparedStatement ps, PlayerStatData d) throws SQLException {
        int i = 1;
        ps.setString(i++, d.uuid);
        ps.setString(i++, d.name);

        ps.setDouble(i++, d.itemLevel);
        ps.setDouble(i++, d.itemLevelSlot1);
        ps.setDouble(i++, d.itemLevelSlot2);
        ps.setDouble(i++, d.itemLevelSlot3);
        ps.setDouble(i++, d.itemLevelSlot4);

        ps.setInt(i++, d.equipStrength);
        ps.setInt(i++, d.equipAgility);
        ps.setInt(i++, d.equipIntelligence);
        ps.setInt(i++, d.equipLuck);

        ps.setInt(i++, d.equipHp);
        ps.setInt(i++, d.equipDefense);
        ps.setInt(i++, d.equipAttack);
        ps.setInt(i++, d.equipMagicAttack);
        ps.setDouble(i++, d.equipCritRate);
        ps.setDouble(i++, d.equipCritDamage);
        ps.setDouble(i++, d.equipArmorPenetration);
        ps.setInt(i++, d.equipBonusDamage);
        ps.setDouble(i++, d.equipElementalMultiplier);
        ps.setDouble(i++, d.equipLifeSteal);
        ps.setDouble(i++, d.equipAttackMultiplier);
        ps.setDouble(i++, d.equipMagicAttackMultiplier);
        ps.setDouble(i++, d.equipMoveSpeed);
        ps.setDouble(i++, d.equipBuffDuration);
        ps.setDouble(i++, d.equipCooldownReduction);
        ps.setDouble(i++, d.equipClearGoldBonus);

        ps.setLong(i++, d.gold);
        ps.setInt(i++, d.currentHp);
        ps.setInt(i, d.currentMp);
    }

    private String buildEmptyAccessoryJson() {
        return "{\"slots\":[]}";
    }

    private List<String> parseJsonStringArray(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            List<String> list = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                list.add(el.getAsString());
            }
            return list;
        } catch (Exception e) {
            logger.warn("[MysqlPlayerDataStorage] Failed to parse skin list JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String getStringOrDefault(JsonObject obj, String key, String def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return def;
    }

    private static int getIntOrDefault(JsonObject obj, String key, int def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return def;
    }

    // =========================================================================
    // Internal DTO
    // =========================================================================

    private static final class SlotEntry {
        int slotIndex;
        String itemId;
        String registryId;
        int customModelData;
        String itemData;
    }
}
