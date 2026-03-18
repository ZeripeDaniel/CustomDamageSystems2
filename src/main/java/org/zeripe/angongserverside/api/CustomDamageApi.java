package org.zeripe.angongserverside.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.zeripe.angongserverside.combat.HitCooldownRegistry;
import org.zeripe.angongserverside.combat.StatManager;

/**
 * 다른 모드에서 접근 가능한 최소 API.
 */
public final class CustomDamageApi {
    private static volatile StatManager statManager;

    private CustomDamageApi() {}

    public static void bind(StatManager manager) {
        statManager = manager;
    }

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

    public static void registerWeaponHitCooldown(Item weapon, int ticks) {
        HitCooldownRegistry.get().register(weapon, ticks);
    }

    public static void unregisterWeaponHitCooldown(Item weapon) {
        HitCooldownRegistry.get().clear(weapon);
    }
}
