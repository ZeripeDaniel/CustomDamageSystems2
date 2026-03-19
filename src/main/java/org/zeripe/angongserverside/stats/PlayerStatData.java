package org.zeripe.angongserverside.stats;

public class PlayerStatData {
    public String uuid = "";
    public String name = "";
    public double itemLevel = 0.0;
    public double itemLevelSlot1 = 0.0;
    public double itemLevelSlot2 = 0.0;
    public double itemLevelSlot3 = 0.0;
    public double itemLevelSlot4 = 0.0;

    public int equipStrength = 0;
    public int equipAgility = 0;
    public int equipIntelligence = 0;
    public int equipLuck = 0;

    public int equipHp = 0;
    public int equipDefense = 0;
    public int equipAttack = 0;
    public int equipMagicAttack = 0;
    public double equipCritRate = 0.0;
    public double equipCritDamage = 0.0;
    public double equipArmorPenetration = 0.0;
    public int equipBonusDamage = 0;
    public double equipElementalMultiplier = 0.0;
    public double equipLifeSteal = 0.0;
    public double equipAttackMultiplier = 100.0;
    public double equipMagicAttackMultiplier = 100.0;
    public double equipMoveSpeed = 0.0;
    public double equipBuffDuration = 0.0;
    public double equipCooldownReduction = 0.0;
    public double equipClearGoldBonus = 0.0;

    public long gold = 0;

    public transient int maxHp = 10;
    public transient int maxMp = 0;
    public transient int attack = 0;
    public transient int magicAttack = 0;
    public transient int defense = 0;
    public transient double critRate = 0.0;
    public transient double critDamage = 150.0;
    public transient double armorPenetration = 0.0;
    public transient int bonusDamage = 0;
    public transient double elementalMultiplier = 100.0;
    public transient double lifeSteal = 0.0;
    public transient double attackMultiplier = 100.0;
    public transient double magicAttackMultiplier = 100.0;
    public transient double moveSpeed = 100.0;
    public transient double buffDuration = 100.0;
    public transient double cooldownReduction = 0.0;
    public transient double clearGoldBonus = 0.0;
    public transient int combatPower = 0;

    public transient int totalStrength = 0;
    public transient int totalAgility = 0;
    public transient int totalIntelligence = 0;
    public transient int totalLuck = 0;
    public transient double equipAttackSpeed = 0.0;
    public transient boolean overrideMainhandVanillaAttributes = false;
    public transient boolean overrideVanillaArmor = false;

    public transient int currentHp = 10;
    public transient int currentMp = 0;

    public static PlayerStatData defaultFor(String uuid, String name) {
        PlayerStatData d = new PlayerStatData();
        d.uuid = uuid;
        d.name = name;
        d.maxHp = 10;
        d.maxMp = 0;
        d.currentHp = 10;
        d.currentMp = 0;
        d.moveSpeed = 100.0;
        d.critDamage = 150.0;
        d.elementalMultiplier = 100.0;
        d.attackMultiplier = 100.0;
        d.magicAttackMultiplier = 100.0;
        d.buffDuration = 100.0;
        return d;
    }
}
