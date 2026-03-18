package org.zeripe.angongserverside.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomHealthManager {
    private final Map<UUID, Integer> currentHp = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> maxHp = new ConcurrentHashMap<>();
    private final Logger logger;

    public CustomHealthManager(Logger logger) {
        this.logger = logger;
    }

    public void initPlayer(ServerPlayer player, int max) {
        UUID uuid = player.getUUID();
        maxHp.put(uuid, max);
        currentHp.put(uuid, max);
        pinVanillaHealth(player);
    }

    public void setMaxHp(UUID uuid, int newMax) {
        int oldMax = maxHp.getOrDefault(uuid, 10);
        maxHp.put(uuid, newMax);

        int current = currentHp.getOrDefault(uuid, newMax);
        if (current > newMax) {
            currentHp.put(uuid, newMax);
        } else if (newMax > oldMax && oldMax > 0) {
            double ratio = (double) current / oldMax;
            currentHp.put(uuid, (int) (ratio * newMax));
        }
    }

    public int getCurrentHp(UUID uuid) {
        return currentHp.getOrDefault(uuid, 0);
    }

    public int getMaxHp(UUID uuid) {
        return maxHp.getOrDefault(uuid, 10);
    }

    public int applyDamage(ServerPlayer player, int amount) {
        UUID uuid = player.getUUID();
        int current = currentHp.getOrDefault(uuid, 0);
        int actual = Math.min(current, amount);
        int newHp = current - actual;
        currentHp.put(uuid, newHp);

        if (newHp <= 0) {
            currentHp.put(uuid, 0);
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            logger.debug("[CustomHealth] {} 사망 ({} 데미지)", player.getName().getString(), amount);
        } else {
            pinVanillaHealth(player);
        }
        return actual;
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
    }
}
