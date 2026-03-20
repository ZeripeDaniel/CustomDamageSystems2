package org.zeripe.angongserverside.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomHealthManager {
    /** 흡수 최대치 (황금사과 총 혜택) */
    public static final int MAX_ABSORPTION = 6;

    private final Map<UUID, Integer> currentHp = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> maxHp = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> absorptionHp = new ConcurrentHashMap<>();
    private final Logger logger;

    public CustomHealthManager(Logger logger) {
        this.logger = logger;
    }

    public void initPlayer(ServerPlayer player, int max) {
        UUID uuid = player.getUUID();
        maxHp.put(uuid, max);
        currentHp.put(uuid, max);
        absorptionHp.put(uuid, 0);
        pinVanillaHealth(player);
    }

    public void initPlayer(ServerPlayer player, int max, int savedHp) {
        UUID uuid = player.getUUID();
        maxHp.put(uuid, max);
        int hp = Math.max(1, Math.min(savedHp, max));
        currentHp.put(uuid, hp);
        absorptionHp.put(uuid, 0);
        pinVanillaHealth(player);
    }

    public void setMaxHp(UUID uuid, int newMax) {
        int oldMax = maxHp.getOrDefault(uuid, 10);
        newMax = Math.max(1, newMax);
        maxHp.put(uuid, newMax);

        int current = currentHp.getOrDefault(uuid, newMax);
        if (current > newMax) {
            currentHp.put(uuid, newMax);
        } else if (newMax > oldMax && oldMax > 0) {
            double ratio = (double) current / oldMax;
            currentHp.put(uuid, Math.max(1, (int) (ratio * newMax)));
        }
    }

    public int getCurrentHp(UUID uuid) {
        return currentHp.getOrDefault(uuid, 0);
    }

    public int getMaxHp(UUID uuid) {
        return maxHp.getOrDefault(uuid, 10);
    }

    public int getAbsorptionHp(UUID uuid) {
        return absorptionHp.getOrDefault(uuid, 0);
    }

    /**
     * 흡수 효과 적용 (황금사과 등).
     * totalEffect(기본 MAX_ABSORPTION=6)만큼의 혜택을 부여:
     *   1) 부족한 HP를 먼저 회복
     *   2) 나머지를 absorptionHp로 추가 (MAX_ABSORPTION 캡)
     * maxHp는 절대 변경하지 않음.
     */
    public void applyAbsorption(ServerPlayer player, int totalEffect) {
        UUID uuid = player.getUUID();
        int current = currentHp.getOrDefault(uuid, 0);
        int max = maxHp.getOrDefault(uuid, 10);

        // 1) 부족한 HP 회복
        int missing = Math.max(0, max - current);
        int toHeal = Math.min(totalEffect, missing);
        if (toHeal > 0) {
            currentHp.put(uuid, current + toHeal);
        }

        // 2) 나머지를 흡수 HP로 적용 (캡)
        int remainder = totalEffect - toHeal;
        if (remainder > 0) {
            int existingAbs = absorptionHp.getOrDefault(uuid, 0);
            absorptionHp.put(uuid, Math.min(MAX_ABSORPTION, existingAbs + remainder));
        }
        pinVanillaHealth(player);
    }

    /** 흡수 효과 해제 (포션 만료 등) */
    public void clearAbsorption(UUID uuid) {
        absorptionHp.put(uuid, 0);
    }

    /**
     * 데미지 적용. 흡수 HP를 먼저 소모 → 실제 HP 감소.
     */
    public int applyDamage(ServerPlayer player, int amount) {
        UUID uuid = player.getUUID();
        int totalDamage = amount;

        // 1) 흡수 HP 소모
        int abs = absorptionHp.getOrDefault(uuid, 0);
        if (abs > 0) {
            int absorbedByShield = Math.min(abs, amount);
            absorptionHp.put(uuid, abs - absorbedByShield);
            amount -= absorbedByShield;
            if (amount <= 0) {
                pinVanillaHealth(player);
                return totalDamage;
            }
        }

        // 2) 실제 HP 소모
        int current = currentHp.getOrDefault(uuid, 0);
        int actual = Math.min(current, amount);
        int newHp = current - actual;
        currentHp.put(uuid, newHp);

        if (newHp <= 0) {
            currentHp.put(uuid, 0);
            absorptionHp.put(uuid, 0);
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            logger.debug("[CustomHealth] {} 사망 ({} 데미지)", player.getName().getString(), totalDamage);
        } else {
            pinVanillaHealth(player);
        }
        return totalDamage;
    }

    public int heal(ServerPlayer player, int amount) {
        if (amount <= 0) return 0;
        UUID uuid = player.getUUID();
        int current = currentHp.getOrDefault(uuid, 0);
        int max = maxHp.getOrDefault(uuid, 1000);
        int actual = Math.min(max - current, amount);
        if (actual <= 0) return 0;
        currentHp.put(uuid, current + actual);
        pinVanillaHealth(player);
        return actual;
    }

    public void respawn(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int max = maxHp.getOrDefault(uuid, 10);
        currentHp.put(uuid, max);
        absorptionHp.put(uuid, 0);
        pinVanillaHealth(player);
    }

    public void pinVanillaHealth(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            double vanillaMax = attr.getValue();
            if (player.getHealth() < vanillaMax) {
                player.setHealth((float) vanillaMax);
            }
        }
    }

    public void removePlayer(UUID uuid) {
        currentHp.remove(uuid);
        maxHp.remove(uuid);
        absorptionHp.remove(uuid);
    }
}
