package org.zeripe.angongserverside.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.zeripe.angongserverside.combat.HitCooldownRegistry;
import org.zeripe.angongserverside.combat.StatManager;
import org.zeripe.angongserverside.stats.PlayerStatData;

import java.util.UUID;

/**
 * 다른 모드/플러그인에서 접근 가능한 공개 API.
 * <p>
 * get 계열 — 버프·장비·공식이 모두 반영된 <b>최종 계산 스탯</b>을 반환합니다.
 * getEquip 계열 — 장비에서 오는 <b>원본 수치</b>만 반환합니다.
 * set 계열 — 장비 수치를 변경한 뒤 재계산 + 클라이언트 동기화를 수행합니다.
 */
public final class CustomDamageApi {
    private static volatile StatManager statManager;

    private CustomDamageApi() {}

    public static void bind(StatManager manager) {
        statManager = manager;
    }

    // ══════════════════════════════════════════════════════════
    //  Getter — 최종 계산 스탯 (transient)
    // ══════════════════════════════════════════════════════════

    /** 최종 계산된 스탯 전체를 반환합니다. null이면 해당 플레이어 데이터가 없음. */
    public static PlayerStatData getPlayerData(UUID uuid) {
        if (statManager == null) return null;
        return statManager.getData(uuid);
    }

    public static PlayerStatData getPlayerData(ServerPlayer player) {
        return player == null ? null : getPlayerData(player.getUUID());
    }

    // ── 전투 스탯 ──

    public static int getAttack(ServerPlayer player) { return getAttack(player.getUUID()); }
    public static int getAttack(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.attack : 0;
    }

    public static int getMagicAttack(ServerPlayer player) { return getMagicAttack(player.getUUID()); }
    public static int getMagicAttack(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.magicAttack : 0;
    }

    public static int getDefense(ServerPlayer player) { return getDefense(player.getUUID()); }
    public static int getDefense(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.defense : 0;
    }

    public static double getCritRate(ServerPlayer player) { return getCritRate(player.getUUID()); }
    public static double getCritRate(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.critRate : 0.0;
    }

    public static double getCritDamage(ServerPlayer player) { return getCritDamage(player.getUUID()); }
    public static double getCritDamage(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.critDamage : 150.0;
    }

    public static double getArmorPenetration(ServerPlayer player) { return getArmorPenetration(player.getUUID()); }
    public static double getArmorPenetration(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.armorPenetration : 0.0;
    }

    public static int getBonusDamage(ServerPlayer player) { return getBonusDamage(player.getUUID()); }
    public static int getBonusDamage(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.bonusDamage : 0;
    }

    public static double getElementalMultiplier(ServerPlayer player) { return getElementalMultiplier(player.getUUID()); }
    public static double getElementalMultiplier(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.elementalMultiplier : 100.0;
    }

    public static double getLifeSteal(ServerPlayer player) { return getLifeSteal(player.getUUID()); }
    public static double getLifeSteal(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.lifeSteal : 0.0;
    }

    public static double getAttackMultiplier(ServerPlayer player) { return getAttackMultiplier(player.getUUID()); }
    public static double getAttackMultiplier(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.attackMultiplier : 100.0;
    }

    public static double getMagicAttackMultiplier(ServerPlayer player) { return getMagicAttackMultiplier(player.getUUID()); }
    public static double getMagicAttackMultiplier(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.magicAttackMultiplier : 100.0;
    }

    // ── 기본 능력치 ──

    public static int getStrength(ServerPlayer player) { return getStrength(player.getUUID()); }
    public static int getStrength(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.totalStrength : 0;
    }

    public static int getAgility(ServerPlayer player) { return getAgility(player.getUUID()); }
    public static int getAgility(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.totalAgility : 0;
    }

    public static int getIntelligence(ServerPlayer player) { return getIntelligence(player.getUUID()); }
    public static int getIntelligence(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.totalIntelligence : 0;
    }

    public static int getLuck(ServerPlayer player) { return getLuck(player.getUUID()); }
    public static int getLuck(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.totalLuck : 0;
    }

    // ── HP / MP ──

    public static int getCurrentHp(ServerPlayer player) { return getCurrentHp(player.getUUID()); }
    public static int getCurrentHp(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.currentHp : 0;
    }

    public static int getMaxHp(ServerPlayer player) { return getMaxHp(player.getUUID()); }
    public static int getMaxHp(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.maxHp : 0;
    }

    public static int getCurrentMp(ServerPlayer player) { return getCurrentMp(player.getUUID()); }
    public static int getCurrentMp(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.currentMp : 0;
    }

    public static int getMaxMp(ServerPlayer player) { return getMaxMp(player.getUUID()); }
    public static int getMaxMp(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.maxMp : 0;
    }

    // ── 이동속도 / 유틸리티 ──

    public static double getMoveSpeed(ServerPlayer player) { return getMoveSpeed(player.getUUID()); }
    public static double getMoveSpeed(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.moveSpeed : 100.0;
    }

    public static double getBuffDuration(ServerPlayer player) { return getBuffDuration(player.getUUID()); }
    public static double getBuffDuration(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.buffDuration : 100.0;
    }

    public static double getCooldownReduction(ServerPlayer player) { return getCooldownReduction(player.getUUID()); }
    public static double getCooldownReduction(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.cooldownReduction : 0.0;
    }

    public static double getClearGoldBonus(ServerPlayer player) { return getClearGoldBonus(player.getUUID()); }
    public static double getClearGoldBonus(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.clearGoldBonus : 0.0;
    }

    // ── 아이템 레벨 / 전투력 ──

    public static double getItemLevel(ServerPlayer player) { return getItemLevel(player.getUUID()); }
    public static double getItemLevel(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.itemLevel : 0.0;
    }

    public static int getCombatPower(ServerPlayer player) { return getCombatPower(player.getUUID()); }
    public static int getCombatPower(UUID uuid) {
        PlayerStatData d = getPlayerData(uuid);
        return d != null ? d.combatPower : 0;
    }

    // ── 골드 ──

    public static long getGold(ServerPlayer player) { return getGold(player.getUUID()); }
    public static long getGold(UUID uuid) {
        if (statManager == null) return 0;
        return statManager.getGold(uuid);
    }

    // ══════════════════════════════════════════════════════════
    //  Getter — 장비 원본 스탯 (equip)
    // ══════════════════════════════════════════════════════════

    /**
     * 장비에서 오는 원본 수치를 필드명으로 조회합니다.
     * 필드명은 set 계열과 동일합니다 (attack, defense, strength 등).
     * 존재하지 않는 필드면 0을 반환합니다.
     */
    public static double getEquipStat(UUID uuid, String field) {
        PlayerStatData d = getPlayerData(uuid);
        if (d == null || field == null) return 0.0;
        return switch (field.toLowerCase()) {
            case "strength" -> d.equipStrength;
            case "agility" -> d.equipAgility;
            case "intelligence" -> d.equipIntelligence;
            case "luck" -> d.equipLuck;
            case "hp" -> d.equipHp;
            case "defense" -> d.equipDefense;
            case "attack" -> d.equipAttack;
            case "magicattack" -> d.equipMagicAttack;
            case "critrate" -> d.equipCritRate;
            case "critdamage" -> d.equipCritDamage;
            case "armorpenetration" -> d.equipArmorPenetration;
            case "bonusdamage" -> d.equipBonusDamage;
            case "elementalmultiplier" -> d.equipElementalMultiplier;
            case "lifesteal" -> d.equipLifeSteal;
            case "attackmultiplier" -> d.equipAttackMultiplier;
            case "magicattackmultiplier" -> d.equipMagicAttackMultiplier;
            case "movespeed" -> d.equipMoveSpeed;
            case "buffduration" -> d.equipBuffDuration;
            case "cooldownreduction" -> d.equipCooldownReduction;
            case "cleargoldbonus" -> d.equipClearGoldBonus;
            default -> 0.0;
        };
    }

    public static double getEquipStat(ServerPlayer player, String field) {
        return player == null ? 0.0 : getEquipStat(player.getUUID(), field);
    }

    // ══════════════════════════════════════════════════════════
    //  Setter — 장비 스탯 변경 (기존)
    // ══════════════════════════════════════════════════════════

    public static void setEquipStat(ServerPlayer player, String field, double value) {
        if (statManager == null || player == null || field == null) return;
        statManager.setEquipStat(player, field, value);
    }

    public static void setAttack(ServerPlayer player, double value) {
        setEquipStat(player, "attack", value);
    }

    public static void setMagicAttack(ServerPlayer player, double value) {
        setEquipStat(player, "magicattack", value);
    }

    public static void setDefense(ServerPlayer player, double value) {
        setEquipStat(player, "defense", value);
    }

    public static void setStrength(ServerPlayer player, double value) {
        setEquipStat(player, "strength", value);
    }

    public static void setAgility(ServerPlayer player, double value) {
        setEquipStat(player, "agility", value);
    }

    public static void setIntelligence(ServerPlayer player, double value) {
        setEquipStat(player, "intelligence", value);
    }

    public static void setLuck(ServerPlayer player, double value) {
        setEquipStat(player, "luck", value);
    }

    public static void setCritRate(ServerPlayer player, double value) {
        setEquipStat(player, "critrate", value);
    }

    public static void setCritDamage(ServerPlayer player, double value) {
        setEquipStat(player, "critdamage", value);
    }

    public static void setArmorPenetration(ServerPlayer player, double value) {
        setEquipStat(player, "armorpenetration", value);
    }

    public static void setBonusDamage(ServerPlayer player, double value) {
        setEquipStat(player, "bonusdamage", value);
    }

    public static void setElementalMultiplier(ServerPlayer player, double value) {
        setEquipStat(player, "elementalmultiplier", value);
    }

    public static void setLifeSteal(ServerPlayer player, double value) {
        setEquipStat(player, "lifesteal", value);
    }

    public static void setAttackMultiplier(ServerPlayer player, double value) {
        setEquipStat(player, "attackmultiplier", value);
    }

    public static void setMagicAttackMultiplier(ServerPlayer player, double value) {
        setEquipStat(player, "magicattackmultiplier", value);
    }

    public static void setItemLevel(ServerPlayer player, double value) {
        setEquipStat(player, "itemlevel", value);
    }

    public static void setItemLevelSlot1(ServerPlayer player, double value) {
        setEquipStat(player, "itemlevelslot1", value);
    }

    public static void setItemLevelSlot2(ServerPlayer player, double value) {
        setEquipStat(player, "itemlevelslot2", value);
    }

    public static void setItemLevelSlot3(ServerPlayer player, double value) {
        setEquipStat(player, "itemlevelslot3", value);
    }

    public static void setItemLevelSlot4(ServerPlayer player, double value) {
        setEquipStat(player, "itemlevelslot4", value);
    }

    // ══════════════════════════════════════════════════════════
    //  무기 쿨다운 등록
    // ══════════════════════════════════════════════════════════

    public static void registerWeaponHitCooldown(Item weapon, int ticks) {
        HitCooldownRegistry.get().register(weapon, ticks);
    }

    public static void unregisterWeaponHitCooldown(Item weapon) {
        HitCooldownRegistry.get().clear(weapon);
    }
}
