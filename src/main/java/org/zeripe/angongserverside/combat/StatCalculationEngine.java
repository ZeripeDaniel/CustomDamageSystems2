package org.zeripe.angongserverside.combat;

import org.zeripe.angongserverside.config.StatFormulaConfig;
import org.zeripe.angongserverside.stats.PlayerStatData;

import java.util.List;

public class StatCalculationEngine {
    private static final double BASE_CRIT_DAMAGE = 150.0;
    private static final double BASE_MOVE_SPEED = 100.0;
    private static final double BASE_BUFF_DURATION = 100.0;

    private final double defenseConstant;
    private final double environmentalDamageScale;
    private final boolean externalDamageUsesCustomDefense;
    private final int baseHp;
    private final int baseMp;
    private final boolean enableBaseStatScaling;
    private final double strToAttack;
    private final double strToHp;
    private final double agiToMoveSpeed;
    private final double agiToCritRate;
    private final double intToMagicAttack;
    private final double intToMp;
    private final double intToCooldownReduction;
    private final double lukToCritRate;
    private final double lukToCritDamage;
    private final double lukToGoldBonus;
    private final List<StatFormulaConfig.Rule> customRules;

    public StatCalculationEngine(double defenseConstant,
                                 double environmentalDamageScale,
                                 boolean externalDamageUsesCustomDefense,
                                 StatFormulaConfig formulaConfig) {
        StatFormulaConfig cfg = formulaConfig != null ? formulaConfig : new StatFormulaConfig();
        this.defenseConstant = defenseConstant > 0 ? defenseConstant : 500.0;
        this.environmentalDamageScale = environmentalDamageScale > 0 ? environmentalDamageScale : 1.0;
        this.externalDamageUsesCustomDefense = externalDamageUsesCustomDefense;
        this.baseHp = Math.max(1, cfg.baseHp);
        this.baseMp = Math.max(0, cfg.baseMp);
        this.enableBaseStatScaling = cfg.enableBaseStatScaling;
        this.strToAttack = cfg.strToAttack;
        this.strToHp = cfg.strToHp;
        this.agiToMoveSpeed = cfg.agiToMoveSpeed;
        this.agiToCritRate = cfg.agiToCritRate;
        this.intToMagicAttack = cfg.intToMagicAttack;
        this.intToMp = cfg.intToMp;
        this.intToCooldownReduction = cfg.intToCooldownReduction;
        this.lukToCritRate = cfg.lukToCritRate;
        this.lukToCritDamage = cfg.lukToCritDamage;
        this.lukToGoldBonus = cfg.lukToGoldBonus;
        this.customRules = cfg.customRules != null ? List.copyOf(cfg.customRules) : List.of();
    }

    public void recalculate(PlayerStatData data, BuffSystem.BuffSnapshot buffs) {
        data.totalStrength = data.equipStrength + buffs.strength();
        data.totalAgility = data.equipAgility + buffs.agility();
        data.totalIntelligence = data.equipIntelligence + buffs.intelligence();
        data.totalLuck = data.equipLuck + buffs.luck();

        int effectiveStrength = enableBaseStatScaling ? data.totalStrength : 0;
        int effectiveAgility = enableBaseStatScaling ? data.totalAgility : 0;
        int effectiveIntelligence = enableBaseStatScaling ? data.totalIntelligence : 0;
        int effectiveLuck = enableBaseStatScaling ? data.totalLuck : 0;

        data.maxHp = baseHp + (int) (effectiveStrength * strToHp) + data.equipHp + buffs.flatHp();
        data.maxMp = baseMp + (int) (effectiveIntelligence * intToMp);

        data.attack = (int) (effectiveStrength * strToAttack) + data.equipAttack + buffs.flatAttack();
        data.magicAttack = (int) (effectiveIntelligence * intToMagicAttack) + data.equipMagicAttack + buffs.flatMagicAttack();
        data.defense = data.equipDefense + buffs.flatDefense();

        data.critRate = data.equipCritRate
                + effectiveAgility * agiToCritRate
                + effectiveLuck * lukToCritRate
                + buffs.critRateBonus();
        data.critDamage = BASE_CRIT_DAMAGE + effectiveLuck * lukToCritDamage + data.equipCritDamage;
        data.armorPenetration = Math.max(0.0, data.equipArmorPenetration);
        data.bonusDamage = Math.max(0, data.equipBonusDamage);
        data.elementalMultiplier = Math.max(0.0, 100.0 + data.equipElementalMultiplier);
        data.lifeSteal = Math.max(0.0, data.equipLifeSteal);
        data.attackMultiplier = Math.max(1.0, data.equipAttackMultiplier);
        data.magicAttackMultiplier = Math.max(1.0, data.equipMagicAttackMultiplier);

        data.moveSpeed = BASE_MOVE_SPEED + data.equipMoveSpeed + effectiveAgility * agiToMoveSpeed + buffs.moveSpeedBonus();
        data.cooldownReduction = data.equipCooldownReduction + effectiveIntelligence * intToCooldownReduction + buffs.cdrBonus();
        data.buffDuration = BASE_BUFF_DURATION + data.equipBuffDuration + buffs.buffDurationBonus();
        data.clearGoldBonus = data.equipClearGoldBonus + effectiveLuck * lukToGoldBonus;
        applyCustomRules(data, effectiveStrength, effectiveAgility, effectiveIntelligence, effectiveLuck);
        data.maxHp = Math.max(1, data.maxHp);
        data.maxMp = Math.max(0, data.maxMp);
        data.combatPower = calculateCombatPower(data);
    }

    private void applyCustomRules(PlayerStatData data,
                                  int effectiveStrength,
                                  int effectiveAgility,
                                  int effectiveIntelligence,
                                  int effectiveLuck) {
        for (StatFormulaConfig.Rule rule : customRules) {
            if (rule == null) continue;
            double perPoints = rule.perPoints();
            if (perPoints <= 0.0) continue;
            double source = sourceValue(rule.sourceStat(), effectiveStrength, effectiveAgility, effectiveIntelligence, effectiveLuck);
            if (source <= 0.0) continue;
            double delta = (source / perPoints) * rule.gain();
            applyDelta(data, rule.targetAttribute(), delta);
        }
    }

    private double sourceValue(String sourceStat,
                               int effectiveStrength,
                               int effectiveAgility,
                               int effectiveIntelligence,
                               int effectiveLuck) {
        if (sourceStat == null) return 0.0;
        return switch (sourceStat.toLowerCase()) {
            case "strength", "str" -> effectiveStrength;
            case "agility", "agi" -> effectiveAgility;
            case "intelligence", "int" -> effectiveIntelligence;
            case "luck", "luk" -> effectiveLuck;
            default -> 0.0;
        };
    }

    private void applyDelta(PlayerStatData data, String targetAttribute, double delta) {
        if (targetAttribute == null || delta == 0.0) return;
        switch (targetAttribute.toLowerCase()) {
            case "maxhp", "hp" -> data.maxHp += (int) Math.round(delta);
            case "maxmp", "mp" -> data.maxMp += (int) Math.round(delta);
            case "attack" -> data.attack += (int) Math.round(delta);
            case "magicattack" -> data.magicAttack += (int) Math.round(delta);
            case "defense" -> data.defense += (int) Math.round(delta);
            case "critrate" -> data.critRate += delta;
            case "critdamage" -> data.critDamage += delta;
            case "armorpenetration" -> data.armorPenetration += delta;
            case "bonusdamage" -> data.bonusDamage += (int) Math.round(delta);
            case "elementalmultiplier" -> data.elementalMultiplier += delta;
            case "lifesteal" -> data.lifeSteal += delta;
            case "attackmultiplier" -> data.attackMultiplier += delta;
            case "magicattackmultiplier" -> data.magicAttackMultiplier += delta;
            case "movespeed" -> data.moveSpeed += delta;
            case "buffduration" -> data.buffDuration += delta;
            case "cooldownreduction" -> data.cooldownReduction += delta;
            case "cleargoldbonus" -> data.clearGoldBonus += delta;
            default -> {
            }
        }
    }

    public int calculateCombatPower(PlayerStatData data) {
        double cp = 0;
        cp += data.maxHp * 0.5;
        cp += data.attack * 3.0;
        cp += data.magicAttack * 3.0;
        cp += data.defense * 2.0;
        cp += data.critRate * 10.0;
        cp += data.critDamage * 2.0;
        cp += data.armorPenetration * 1.5;
        cp += data.bonusDamage * 1.2;
        cp += data.lifeSteal * 8.0;
        cp += data.moveSpeed;
        return (int) cp;
    }

    public DamageResult calculateDamage(PlayerStatData attacker, PlayerStatData defender, boolean isMagic) {
        return calculateDamage(attacker, defender.defense, isMagic);
    }

    public DamageResult calculateDamage(PlayerStatData attacker, int defenderDefense, boolean isMagic) {
        return calculateDamage(attacker, defenderDefense, isMagic, 0.0, 1.0);
    }

    public DamageResult calculateDamage(PlayerStatData attacker, int defenderDefense, boolean isMagic, double extraFlatDamage) {
        return calculateDamage(attacker, defenderDefense, isMagic, extraFlatDamage, 1.0);
    }

    public DamageResult calculateDamage(PlayerStatData attacker,
                                        int defenderDefense,
                                        boolean isMagic,
                                        double extraFlatDamage,
                                        double attackChargeMultiplier) {
        double baseAttack = isMagic
                ? attacker.magicAttack * (attacker.magicAttackMultiplier / 100.0)
                : attacker.attack * (attacker.attackMultiplier / 100.0);
        double rawDamage = Math.max(1.0, baseAttack + attacker.bonusDamage + Math.max(0.0, extraFlatDamage));
        rawDamage *= Math.max(0.0, attackChargeMultiplier);
        int effectiveDefense = (int) Math.max(0.0, defenderDefense - attacker.armorPenetration);
        double defenseReduction = effectiveDefense / (effectiveDefense + defenseConstant);
        double mitigated = rawDamage * (1.0 - defenseReduction);
        mitigated *= (attacker.elementalMultiplier / 100.0);

        boolean isCrit = Math.random() * 100.0 < attacker.critRate;
        if (isCrit) mitigated *= attacker.critDamage / 100.0;

        int finalDamage = Math.max(1, (int) mitigated);
        DamageType type = isMagic ? DamageType.MAGICAL : DamageType.PHYSICAL;
        int lifeStealHeal = attacker.lifeSteal > 0.0
                ? (int) Math.floor(finalDamage * (attacker.lifeSteal / 100.0))
                : 0;
        return new DamageResult(finalDamage, isCrit, type, lifeStealHeal);
    }

    public int convertEnvironmentalDamage(double vanillaDamage, PlayerStatData victim) {
        double rawDamage = vanillaDamage * environmentalDamageScale;
        if (!externalDamageUsesCustomDefense) {
            return Math.max(1, (int) rawDamage);
        }
        double defenseReduction = victim.defense / (victim.defense + defenseConstant);
        return Math.max(1, (int) (rawDamage * (1.0 - defenseReduction)));
    }

    public record DamageResult(int damage, boolean isCrit, DamageType type, int lifeStealHeal) {}

    public enum DamageType {
        PHYSICAL, MAGICAL, TRUE
    }
}
