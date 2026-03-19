package org.zeripe.angongserverside.combat;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class BuffSystem {
    private final Map<UUID, List<ActiveBuff>> playerBuffs = new ConcurrentHashMap<>();
    private final Logger logger;
    private Consumer<UUID> onBuffChanged;

    public BuffSystem(Logger logger) {
        this.logger = logger;
    }

    public void setOnBuffChanged(Consumer<UUID> callback) {
        this.onBuffChanged = callback;
    }

    public void addBuff(UUID playerId, Buff buff, double buffDurationPercent) {
        List<ActiveBuff> buffs = playerBuffs.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>());
        buffs.removeIf(ab -> ab.buff().id().equals(buff.id()));

        long adjustedDurationMs = (long) (buff.durationMs() * (buffDurationPercent / 100.0));
        long expiresAt = System.currentTimeMillis() + adjustedDurationMs;
        buffs.add(new ActiveBuff(buff, expiresAt));
        logger.debug("[BuffSystem] 버프 추가: {} -> {}", buff.id(), playerId);

        if (onBuffChanged != null) onBuffChanged.accept(playerId);
    }

    public void removeBuff(UUID playerId, String buffId) {
        List<ActiveBuff> buffs = playerBuffs.get(playerId);
        if (buffs != null) {
            boolean removed = buffs.removeIf(ab -> ab.buff().id().equals(buffId));
            if (removed && onBuffChanged != null) onBuffChanged.accept(playerId);
        }
    }

    public void tickAll() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, List<ActiveBuff>> entry : playerBuffs.entrySet()) {
            UUID playerId = entry.getKey();
            List<ActiveBuff> buffs = entry.getValue();
            boolean anyExpired = buffs.removeIf(ab -> now >= ab.expiresAt());
            if (anyExpired && onBuffChanged != null) onBuffChanged.accept(playerId);
        }
    }

    public BuffSnapshot getSnapshot(UUID playerId) {
        List<ActiveBuff> buffs = playerBuffs.get(playerId);
        if (buffs == null || buffs.isEmpty()) return BuffSnapshot.EMPTY;

        int str = 0, agi = 0, intel = 0, luk = 0;
        int flatHp = 0, flatAtk = 0, flatMAtk = 0, flatDef = 0;
        double msBonus = 0, cdrBonus = 0, bdBonus = 0, crBonus = 0;

        for (ActiveBuff ab : buffs) {
            Buff b = ab.buff();
            str += b.strength();
            agi += b.agility();
            intel += b.intelligence();
            luk += b.luck();
            flatHp += b.flatHp();
            flatAtk += b.flatAttack();
            flatMAtk += b.flatMagicAttack();
            flatDef += b.flatDefense();
            msBonus += b.moveSpeedBonus();
            cdrBonus += b.cdrBonus();
            bdBonus += b.buffDurationBonus();
            crBonus += b.critRateBonus();
        }

        return new BuffSnapshot(str, agi, intel, luk, flatHp, flatAtk, flatMAtk, flatDef,
                msBonus, cdrBonus, bdBonus, crBonus);
    }

    public List<ActiveBuff> getActiveBuffs(UUID playerId) {
        return playerBuffs.getOrDefault(playerId, Collections.emptyList());
    }

    public void clearPlayer(UUID playerId) {
        playerBuffs.remove(playerId);
    }

    public record Buff(
            String id,
            String displayName,
            long durationMs,
            int strength,
            int agility,
            int intelligence,
            int luck,
            int flatHp,
            int flatAttack,
            int flatMagicAttack,
            int flatDefense,
            double moveSpeedBonus,
            double cdrBonus,
            double buffDurationBonus,
            double critRateBonus
    ) {}

    public record ActiveBuff(Buff buff, long expiresAt) {
        public long remainingMs() {
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }

        public int remainingSeconds() {
            return (int) (remainingMs() / 1000);
        }
    }

    public record BuffSnapshot(
            int strength, int agility, int intelligence, int luck,
            int flatHp, int flatAttack, int flatMagicAttack, int flatDefense,
            double moveSpeedBonus, double cdrBonus, double buffDurationBonus,
            double critRateBonus
    ) {
        public static final BuffSnapshot EMPTY =
                new BuffSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
