package org.zeripe.angongserverside.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 무기별 피격 쿨타임 정책.
 */
public final class HitCooldownRegistry {
    private static final HitCooldownRegistry INSTANCE = new HitCooldownRegistry();

    private volatile int defaultTicks = 7;
    private volatile boolean useVanillaAttackSpeed = true;
    private volatile int minAttackSpeedTicks = 1;
    private volatile int maxAttackSpeedTicks = 40;
    private final Map<Item, Integer> weaponCooldownTicks = new ConcurrentHashMap<>();

    private HitCooldownRegistry() {}

    public static HitCooldownRegistry get() {
        return INSTANCE;
    }

    public void setDefaultTicks(int ticks) {
        if (ticks > 0) this.defaultTicks = ticks;
    }

    public void setAttackSpeedPolicy(boolean enabled, int minTicks, int maxTicks) {
        this.useVanillaAttackSpeed = enabled;
        this.minAttackSpeedTicks = Math.max(1, minTicks);
        this.maxAttackSpeedTicks = Math.max(this.minAttackSpeedTicks, maxTicks);
    }

    public int defaultTicks() {
        return defaultTicks;
    }

    public void register(Item weapon, int ticks) {
        if (weapon == null || ticks <= 0) return;
        weaponCooldownTicks.put(weapon, ticks);
    }

    public void clear(Item weapon) {
        if (weapon == null) return;
        weaponCooldownTicks.remove(weapon);
    }

    public int resolve(DamageSource source, ServerPlayer attacker) {
        if (source.getEntity() == null || attacker == null) {
            return defaultTicks;
        }
        Item weapon = attacker.getMainHandItem().getItem();
        Integer manualTicks = weaponCooldownTicks.get(weapon);
        if (manualTicks != null) {
            return Math.max(1, manualTicks);
        }
        if (useVanillaAttackSpeed) {
            AttributeInstance attackSpeedAttr = attacker.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeedAttr != null && attackSpeedAttr.getValue() > 0.0) {
                int ticks = (int) Math.ceil(20.0 / attackSpeedAttr.getValue());
                return Math.max(minAttackSpeedTicks, Math.min(maxAttackSpeedTicks, ticks));
            }
        }
        return defaultTicks;
    }
}
