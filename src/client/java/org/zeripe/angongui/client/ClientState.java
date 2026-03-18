package org.zeripe.angongui.client;

import java.util.ArrayList;
import java.util.List;

public final class ClientState {
    private static final ClientState INSTANCE = new ClientState();

    private final List<BuffEntry> activeBuffs = new ArrayList<>();
    private PlayerStats playerStats = PlayerStats.empty();

    private ClientState() {}

    public static ClientState get() {
        return INSTANCE;
    }

    public PlayerStats getPlayerStats() {
        return playerStats;
    }

    public void setPlayerStats(PlayerStats s) {
        this.playerStats = s;
    }

    public void updateHpMp(int currentHp, int maxHp, int currentMp, int maxMp) {
        PlayerStats old = this.playerStats;
        this.playerStats = new PlayerStats(
                old.name(), old.itemLevel(), old.combatPower(),
                currentHp, maxHp, currentMp, maxMp,
                old.attack(), old.magicAttack(), old.defense(),
                old.critRate(), old.critDamage(), old.armorPenetration(),
                old.bonusDamage(), old.elementalMultiplier(), old.lifeSteal(),
                old.attackMultiplier(), old.magicAttackMultiplier(),
                old.moveSpeed(), old.buffDuration(), old.cooldownReduction(),
                old.clearGoldBonus(),
                old.strength(), old.agility(), old.intelligence(), old.luck(),
                old.equipStrength(), old.equipAgility(), old.equipIntelligence(), old.equipLuck()
        );
    }

    public List<BuffEntry> getActiveBuffs() {
        return activeBuffs;
    }

    public void setActiveBuffs(List<BuffEntry> buffs) {
        activeBuffs.clear();
        activeBuffs.addAll(buffs);
    }

    public void clear() {
        activeBuffs.clear();
        playerStats = PlayerStats.empty();
    }

    public record BuffEntry(String id, String name, int remainingSeconds) {}

    public record PlayerStats(
            String name,
            double itemLevel,
            int combatPower,
            int currentHp, int maxHp,
            int currentMp, int maxMp,
            int attack,
            int magicAttack,
            int defense,
            double critRate,
            double critDamage,
            double armorPenetration,
            int bonusDamage,
            double elementalMultiplier,
            double lifeSteal,
            double attackMultiplier,
            double magicAttackMultiplier,
            double moveSpeed,
            double buffDuration,
            double cooldownReduction,
            double clearGoldBonus,
            int strength,
            int agility,
            int intelligence,
            int luck,
            int equipStrength,
            int equipAgility,
            int equipIntelligence,
            int equipLuck
    ) {
        public static PlayerStats empty() {
            return new PlayerStats(
                    "", 0, 0,
                    10, 10, 0, 0,
                    0, 0, 0,
                    0.0, 150.0, 0.0, 0, 100.0, 0.0, 100.0, 100.0, 100.0, 100.0, 0.0, 0.0,
                    0, 0, 0, 0,
                    0, 0, 0, 0
            );
        }
    }
}
