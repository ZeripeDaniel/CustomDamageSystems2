package org.zeripe.angongui.client;

import java.util.ArrayList;
import java.util.List;

public final class ClientState {
    private static final ClientState INSTANCE = new ClientState();

    private final List<BuffEntry> activeBuffs = new ArrayList<>();
    private PlayerStats playerStats = PlayerStats.empty();

    // 서버에서 받은 시스템 토글 설정 — 기본 false (바닐라 우선, 서버가 켜라고 해야 커스텀)
    private boolean statSystemEnabled = false;
    private boolean damageSystemEnabled = false;
    private boolean customHealthEnabled = false;
    private boolean customHudEnabled = false;
    private boolean systemConfigReceived = false;
    private boolean waitingForConfig = false;  // CDS 서버에 접속했지만 아직 config 미수신

    // AngongGui 설정 (서버에서 수신) — 기본 false
    private boolean guiOverlayEnabled = false;
    private boolean guiMenuBarEnabled = false;
    private boolean guiMoneyDisplayEnabled = false;
    private boolean guiPartyWindowEnabled = false;
    private boolean guiQuestWindowEnabled = false;
    private boolean guiCustomPauseScreenEnabled = false;

    private ClientState() {}

    public static ClientState get() {
        return INSTANCE;
    }

    // ── 시스템 토글 ──

    public boolean isStatSystemEnabled() { return statSystemEnabled; }
    public boolean isDamageSystemEnabled() { return damageSystemEnabled; }
    public boolean isCustomHealthEnabled() { return customHealthEnabled; }
    public boolean isCustomHudEnabled() { return customHudEnabled; }
    public boolean isSystemConfigReceived() { return systemConfigReceived; }

    /** CDS 서버 접속 후 config 수신 대기 중인지 (모든 HUD 숨김) */
    public boolean isWaitingForConfig() { return waitingForConfig && !systemConfigReceived; }
    public void setWaitingForConfig(boolean waiting) { this.waitingForConfig = waiting; }

    public void setSystemConfig(boolean stat, boolean damage, boolean health, boolean hud) {
        this.statSystemEnabled = stat;
        this.damageSystemEnabled = damage;
        this.customHealthEnabled = health;
        this.customHudEnabled = hud;
        this.systemConfigReceived = true;
        this.waitingForConfig = false;
    }

    // ── AngongGui 설정 getter/setter ──

    public boolean isGuiOverlayEnabled() { return guiOverlayEnabled; }
    public boolean isGuiMenuBarEnabled() { return guiMenuBarEnabled; }
    public boolean isGuiMoneyDisplayEnabled() { return guiMoneyDisplayEnabled; }
    public boolean isGuiPartyWindowEnabled() { return guiPartyWindowEnabled; }
    public boolean isGuiQuestWindowEnabled() { return guiQuestWindowEnabled; }
    public boolean isGuiCustomPauseScreenEnabled() { return guiCustomPauseScreenEnabled; }

    public void setAngongGuiConfig(boolean overlay, boolean menu, boolean money,
                                    boolean party, boolean quest, boolean pauseScreen) {
        this.guiOverlayEnabled = overlay;
        this.guiMenuBarEnabled = menu;
        this.guiMoneyDisplayEnabled = money;
        this.guiPartyWindowEnabled = party;
        this.guiQuestWindowEnabled = quest;
        this.guiCustomPauseScreenEnabled = pauseScreen;
    }

    public PlayerStats getPlayerStats() {
        return playerStats;
    }

    public void setPlayerStats(PlayerStats s) {
        this.playerStats = s;
    }

    public void updateHpMp(int currentHp, int maxHp, int absorptionHp, int currentMp, int maxMp) {
        PlayerStats old = this.playerStats;
        this.playerStats = new PlayerStats(
                old.name(), old.itemLevel(), old.combatPower(),
                currentHp, maxHp, absorptionHp, currentMp, maxMp,
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
        statSystemEnabled = false;
        damageSystemEnabled = false;
        customHealthEnabled = false;
        customHudEnabled = false;
        systemConfigReceived = false;
        waitingForConfig = false;
        guiOverlayEnabled = false;
        guiMenuBarEnabled = false;
        guiMoneyDisplayEnabled = false;
        guiPartyWindowEnabled = false;
        guiQuestWindowEnabled = false;
        guiCustomPauseScreenEnabled = false;
    }

    public record BuffEntry(String id, String name, int remainingSeconds) {}

    public record PlayerStats(
            String name,
            double itemLevel,
            int combatPower,
            int currentHp, int maxHp, int absorptionHp,
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
                    10, 10, 0, 0, 0,
                    0, 0, 0,
                    0.0, 150.0, 0.0, 0, 100.0, 0.0, 100.0, 100.0, 100.0, 100.0, 0.0, 0.0,
                    0, 0, 0, 0,
                    0, 0, 0, 0
            );
        }
    }
}
