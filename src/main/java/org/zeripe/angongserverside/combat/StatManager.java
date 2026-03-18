package org.zeripe.angongserverside.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.zeripe.angongserverside.network.ServerNetworkHandler;
import org.zeripe.angongserverside.stats.PlayerStatData;
import org.zeripe.angongserverside.stats.StatStorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatManager {
    private final Map<UUID, PlayerStatData> playerData = new ConcurrentHashMap<>();

    private final StatStorage storage;
    private final StatCalculationEngine calcEngine;
    private final BuffSystem buffSystem;
    private final CustomHealthManager healthManager;
    private final Logger logger;

    public StatManager(StatStorage storage,
                       StatCalculationEngine calcEngine,
                       BuffSystem buffSystem,
                       CustomHealthManager healthManager,
                       Logger logger) {
        this.storage = storage;
        this.calcEngine = calcEngine;
        this.buffSystem = buffSystem;
        this.healthManager = healthManager;
        this.logger = logger;
        buffSystem.setOnBuffChanged(this::onBuffChanged);
    }

    public void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String name = player.getName().getString();

        PlayerStatData data = storage.load(uuid.toString(), name);
        data.name = name;

        BuffSystem.BuffSnapshot buffs = buffSystem.getSnapshot(uuid);
        calcEngine.recalculate(data, buffs);
        data.itemLevel = resolveItemLevel(data);
        playerData.put(uuid, data);

        healthManager.initPlayer(player, data.maxHp);
        data.currentHp = data.maxHp;
        data.currentMp = data.maxMp;
        healthManager.pinVanillaHealth(player);

        applyMoveSpeed(player, data);
        sendFullStat(player, data);
        logger.info("[StatManager] {} 스탯 로드 완료", name);
    }

    public void onPlayerQuit(UUID uuid) {
        PlayerStatData data = playerData.remove(uuid);
        if (data != null) storage.save(data);
        healthManager.removePlayer(uuid);
        buffSystem.clearPlayer(uuid);
    }

    private void onBuffChanged(UUID playerId) {
        PlayerStatData data = playerData.get(playerId);
        if (data == null) return;
        int oldMaxHp = data.maxHp;
        BuffSystem.BuffSnapshot buffs = buffSystem.getSnapshot(playerId);
        calcEngine.recalculate(data, buffs);
        if (data.maxHp != oldMaxHp) {
            healthManager.setMaxHp(playerId, data.maxHp);
            data.currentHp = healthManager.getCurrentHp(playerId);
        }
    }

    public PlayerStatData getData(UUID uuid) {
        return playerData.get(uuid);
    }

    public void setEquipStat(ServerPlayer player, String field, double value) {
        PlayerStatData data = playerData.get(player.getUUID());
        if (data == null) return;

        switch (field.toLowerCase()) {
            case "strength" -> data.equipStrength = (int) value;
            case "agility" -> data.equipAgility = (int) value;
            case "intelligence" -> data.equipIntelligence = (int) value;
            case "luck" -> data.equipLuck = (int) value;
            case "hp" -> data.equipHp = (int) value;
            case "defense" -> data.equipDefense = (int) value;
            case "attack" -> data.equipAttack = (int) value;
            case "magicattack" -> data.equipMagicAttack = (int) value;
            case "critrate" -> data.equipCritRate = value;
            case "critdamage" -> data.equipCritDamage = value;
            case "armorpenetration" -> data.equipArmorPenetration = value;
            case "bonusdamage" -> data.equipBonusDamage = (int) value;
            case "elementalmultiplier" -> data.equipElementalMultiplier = value;
            case "lifesteal" -> data.equipLifeSteal = value;
            case "attackmultiplier" -> data.equipAttackMultiplier = value;
            case "magicattackmultiplier" -> data.equipMagicAttackMultiplier = value;
            case "movespeed" -> data.equipMoveSpeed = value;
            case "buffduration" -> data.equipBuffDuration = value;
            case "cooldownreduction" -> data.equipCooldownReduction = value;
            case "cleargoldbonus" -> data.equipClearGoldBonus = value;
            case "itemlevel" -> data.itemLevel = value;
            case "itemlevelslot1", "itemlevelweapon", "itemlevelmainhand" -> data.itemLevelSlot1 = value;
            case "itemlevelslot2", "itemlevelhead", "itemlevelhelmet" -> data.itemLevelSlot2 = value;
            case "itemlevelslot3", "itemlevelchest", "itemlevelbody" -> data.itemLevelSlot3 = value;
            case "itemlevelslot4", "itemlevellegs" -> data.itemLevelSlot4 = value;
            default -> {
                logger.warn("[StatManager] 알 수 없는 필드: {}", field);
                return;
            }
        }

        recalculateAndSync(player, data);
        storage.save(data);
    }

    private void recalculateAndSync(ServerPlayer player, PlayerStatData data) {
        int oldMaxHp = data.maxHp;
        BuffSystem.BuffSnapshot buffs = buffSystem.getSnapshot(player.getUUID());
        calcEngine.recalculate(data, buffs);
        data.itemLevel = resolveItemLevel(data);

        if (data.maxHp != oldMaxHp) {
            healthManager.setMaxHp(player.getUUID(), data.maxHp);
            data.currentHp = healthManager.getCurrentHp(player.getUUID());
        }
        applyMoveSpeed(player, data);
        sendFullStat(player, data);
    }

    public void sendFullStat(ServerPlayer player, PlayerStatData data) {
        if (data == null) return;
        data.currentHp = healthManager.getCurrentHp(player.getUUID());

        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        resp.addProperty("action", "stat_data");

        JsonObject stat = new JsonObject();
        stat.addProperty("name", data.name);
        stat.addProperty("itemLevel", data.itemLevel);
        stat.addProperty("combatPower", data.combatPower);
        stat.addProperty("currentHp", data.currentHp);
        stat.addProperty("maxHp", data.maxHp);
        stat.addProperty("currentMp", data.currentMp);
        stat.addProperty("maxMp", data.maxMp);
        stat.addProperty("attack", data.attack);
        stat.addProperty("magicAttack", data.magicAttack);
        stat.addProperty("defense", data.defense);
        stat.addProperty("critRate", data.critRate);
        stat.addProperty("critDamage", data.critDamage);
        stat.addProperty("armorPenetration", data.armorPenetration);
        stat.addProperty("bonusDamage", data.bonusDamage);
        stat.addProperty("elementalMultiplier", data.elementalMultiplier);
        stat.addProperty("lifeSteal", data.lifeSteal);
        stat.addProperty("attackMultiplier", data.attackMultiplier);
        stat.addProperty("magicAttackMultiplier", data.magicAttackMultiplier);
        stat.addProperty("moveSpeed", data.moveSpeed);
        stat.addProperty("buffDuration", data.buffDuration);
        stat.addProperty("cooldownReduction", data.cooldownReduction);
        stat.addProperty("clearGoldBonus", data.clearGoldBonus);
        stat.addProperty("strength", data.totalStrength);
        stat.addProperty("agility", data.totalAgility);
        stat.addProperty("intelligence", data.totalIntelligence);
        stat.addProperty("luck", data.totalLuck);
        stat.addProperty("equipStrength", data.equipStrength);
        stat.addProperty("equipAgility", data.equipAgility);
        stat.addProperty("equipIntelligence", data.equipIntelligence);
        stat.addProperty("equipLuck", data.equipLuck);
        resp.add("stat", stat);

        ServerNetworkHandler.sendStat(player, resp.toString());
    }

    public void syncHpToClient(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerStatData data = playerData.get(uuid);
        if (data == null) return;

        data.currentHp = healthManager.getCurrentHp(uuid);
        JsonObject resp = new JsonObject();
        resp.addProperty("action", "hp_update");
        resp.addProperty("currentHp", data.currentHp);
        resp.addProperty("maxHp", data.maxHp);
        resp.addProperty("currentMp", data.currentMp);
        resp.addProperty("maxMp", data.maxMp);
        ServerNetworkHandler.sendStat(player, resp.toString());
    }

    public void sendBuffUpdate(ServerPlayer player) {
        UUID uuid = player.getUUID();
        var activeBuffs = buffSystem.getActiveBuffs(uuid);
        JsonObject resp = new JsonObject();
        resp.addProperty("action", "buff_update");

        JsonArray buffsArr = new JsonArray();
        for (BuffSystem.ActiveBuff ab : activeBuffs) {
            JsonObject b = new JsonObject();
            b.addProperty("id", ab.buff().id());
            b.addProperty("name", ab.buff().displayName());
            b.addProperty("remainingSeconds", ab.remainingSeconds());
            buffsArr.add(b);
        }
        resp.add("buffs", buffsArr);
        ServerNetworkHandler.sendStat(player, resp.toString());
    }

    private void applyMoveSpeed(ServerPlayer player, PlayerStatData data) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            double vanillaSpeed = 0.1 * (data.moveSpeed / 100.0);
            vanillaSpeed = Math.max(0.01, Math.min(1.0, vanillaSpeed));
            attr.setBaseValue(vanillaSpeed);
        }
    }

    private double resolveItemLevel(PlayerStatData data) {
        double s1 = Math.max(0.0, data.itemLevelSlot1);
        double s2 = Math.max(0.0, data.itemLevelSlot2);
        double s3 = Math.max(0.0, data.itemLevelSlot3);
        double s4 = Math.max(0.0, data.itemLevelSlot4);
        double sum = s1 + s2 + s3 + s4;

        // Lost Ark-style behavior (average of equipped piece levels).
        // If slot levels are not provided, keep manually set itemLevel for compatibility.
        if (sum <= 0.0) return Math.max(0.0, data.itemLevel);
        return Math.max(0.0, sum / 4.0);
    }

    public void saveAll() {
        for (PlayerStatData data : playerData.values()) {
            storage.save(data);
        }
        logger.info("[StatManager] 전체 스탯 저장 완료 ({}명)", playerData.size());
    }
}
