package org.zeripe.angongserverside.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.zeripe.angongserverside.config.MonsterAttackGroupConfig;
import org.zeripe.angongserverside.stats.PlayerStatData;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerDamageHandler {
    private final CustomHealthManager healthManager;
    private final StatCalculationEngine calcEngine;
    private final StatManager statManager;
    private final DamageNumberSender dmgSender;
    private final Logger logger;
    private final boolean includeVanillaArmorForPlayerDefense;
    private final double vanillaArmorDefenseMultiplier;
    private final MonsterAttackGroupConfig monsterAttackGroupConfig;
    private final HitCooldownRegistry cooldownRegistry = HitCooldownRegistry.get();
    private final Map<Integer, Map<String, Long>> cooldownByVictimAndKey = new ConcurrentHashMap<>();
    private final Set<Integer> passthroughDamage = ConcurrentHashMap.newKeySet();

    public ServerDamageHandler(CustomHealthManager healthManager,
                               StatCalculationEngine calcEngine,
                               StatManager statManager,
                               DamageNumberSender dmgSender,
                               Logger logger,
                               boolean includeVanillaArmorForPlayerDefense,
                               double vanillaArmorDefenseMultiplier,
                               MonsterAttackGroupConfig monsterAttackGroupConfig) {
        this.healthManager = healthManager;
        this.calcEngine = calcEngine;
        this.statManager = statManager;
        this.dmgSender = dmgSender;
        this.logger = logger;
        this.includeVanillaArmorForPlayerDefense = includeVanillaArmorForPlayerDefense;
        this.vanillaArmorDefenseMultiplier = vanillaArmorDefenseMultiplier > 0 ? vanillaArmorDefenseMultiplier : 10.0;
        this.monsterAttackGroupConfig = monsterAttackGroupConfig;
    }

    private long lastCleanupTick = 0;
    private static final long CLEANUP_INTERVAL_TICKS = 1200L; // 60초마다

    public void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(this::onAfterDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(this::onAfterRespawn);
    }

    /** 만료된 쿨다운 엔트리 정리 (서버 틱에서 주기적 호출) */
    public void cleanupExpiredCooldowns(long currentGameTime) {
        if (currentGameTime - lastCleanupTick < CLEANUP_INTERVAL_TICKS) return;
        lastCleanupTick = currentGameTime;
        cooldownByVictimAndKey.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(e -> currentGameTime >= e.getValue());
            return entry.getValue().isEmpty();
        });
    }

    private boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (passthroughDamage.contains(entity.getId())) return true;
        if (source.is(DamageTypes.GENERIC_KILL)) return true;

        ServerPlayer attacker = resolveAttacker(source);
        PlayerStatData attackerData = attacker != null ? statManager.getData(attacker.getUUID()) : null;
        int cooldownTicks = cooldownRegistry.resolve(
                source,
                attacker,
                attackerData != null ? attackerData.equipAttackSpeed : 0.0,
                attackerData != null && attackerData.overrideMainhandVanillaAttributes
        );
        String cooldownKey = resolveCooldownKey(source, attacker);
        if (source.getEntity() != null && isHitOnCooldown(entity, cooldownKey)) return false;
        if (entity instanceof ServerPlayer && source.is(DamageTypes.STARVE)) return false;

        if (entity instanceof ServerPlayer victim) {
            return handlePlayerVictim(victim, source, amount, attacker, attackerData, cooldownTicks, cooldownKey);
        }
        return handleNonPlayerVictim(entity, source, amount, attacker, attackerData, cooldownTicks, cooldownKey);
    }

    private boolean handlePlayerVictim(ServerPlayer victim, DamageSource source, float amount, ServerPlayer attacker, PlayerStatData attackerData, int cooldownTicks, String cooldownKey) {
        PlayerStatData victimData = statManager.getData(victim.getUUID());
        if (victimData == null) return true;

        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            healthManager.applyDamage(victim, healthManager.getMaxHp(victim.getUUID()));
            triggerEntityHurtFeedback(victim, source, cooldownTicks);
            statManager.syncHpToClient(victim);
            return false;
        }

        boolean isProjectileHit = source.getDirectEntity() instanceof Projectile;

        StatCalculationEngine.DamageResult result;
        if (attacker != null) {
            if (attackerData == null) return false;
            int defenderDefense = victimData.defense;
            if (includeVanillaArmorForPlayerDefense && !victimData.overrideVanillaArmor) {
                defenderDefense += (int) Math.round(victim.getArmorValue() * vanillaArmorDefenseMultiplier);
            }
            double extraFlat;
            double chargeMultiplier;
            if (isProjectileHit) {
                // 원거리: 바닐라 투사체 데미지를 보너스로 추가
                extraFlat = amount;
                chargeMultiplier = 1.0;
            } else {
                extraFlat = resolveVanillaAttackDamageBonus(attacker, attackerData);
                chargeMultiplier = resolveMeleeAttackChargeMultiplier(source, attacker, amount);
            }
            result = calcEngine.calculateDamage(attackerData, defenderDefense, false, extraFlat, chargeMultiplier);
        } else if (source.getEntity() != null) {
            int envDmg = calcEngine.convertEnvironmentalDamage(amount, victimData);
            result = new StatCalculationEngine.DamageResult(envDmg, false, StatCalculationEngine.DamageType.PHYSICAL, 0);
        } else {
            int envDmg = calcEngine.convertEnvironmentalDamage(amount, victimData);
            result = new StatCalculationEngine.DamageResult(envDmg, false, StatCalculationEngine.DamageType.TRUE, 0);
        }

        healthManager.applyDamage(victim, result.damage());
        if (attacker != null && result.lifeStealHeal() > 0) {
            healthManager.heal(attacker, result.lifeStealHeal());
            statManager.syncHpToClient(attacker);
        }
        Vec3 pos = victim.position().add(0, 1.5, 0);
        dmgSender.sendDamageNumber(pos, result, attacker != null ? attacker.getUUID() : null);
        triggerEntityHurtFeedback(victim, source, cooldownTicks);
        applyCooldown(victim, source, cooldownKey, cooldownTicks);
        statManager.syncHpToClient(victim);
        return false;
    }

    private boolean handleNonPlayerVictim(LivingEntity victim, DamageSource source, float amount, ServerPlayer attacker, PlayerStatData attackerData, int cooldownTicks, String cooldownKey) {
        int damage;
        boolean crit = false;
        StatCalculationEngine.DamageType type = source.getEntity() != null
                ? StatCalculationEngine.DamageType.PHYSICAL
                : StatCalculationEngine.DamageType.TRUE;

        boolean isProjectileHit = source.getDirectEntity() instanceof Projectile;

        if (attacker != null) {
            if (attackerData != null) {
                int armor = Math.max(0, victim.getArmorValue());
                double rawDamage = attackerData.attack + attackerData.bonusDamage;
                if (isProjectileHit) {
                    // 원거리: 바닐라 투사체 데미지를 보너스로 추가
                    rawDamage += amount;
                } else {
                    rawDamage += resolveVanillaAttackDamageBonus(attacker, attackerData);
                    rawDamage *= resolveMeleeAttackChargeMultiplier(source, attacker, amount);
                }
                double effectiveArmor = Math.max(0.0, armor - attackerData.armorPenetration);
                double defenseReduction = effectiveArmor / (effectiveArmor + 500.0);
                double mitigated = rawDamage * (1.0 - defenseReduction);
                mitigated *= attackerData.elementalMultiplier / 100.0;
                crit = Math.random() * 100.0 < attackerData.critRate;
                if (crit) mitigated *= attackerData.critDamage / 100.0;
                damage = Math.max(1, (int) mitigated);
                if (attackerData.lifeSteal > 0.0) {
                    int heal = (int) Math.floor(damage * (attackerData.lifeSteal / 100.0));
                    if (heal > 0) {
                        healthManager.heal(attacker, heal);
                        statManager.syncHpToClient(attacker);
                    }
                }
            } else {
                damage = Math.max(1, Math.round(amount));
            }
        } else {
            damage = Math.max(1, Math.round(amount));
        }

        Vec3 pos = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        dmgSender.sendDamageNumber(pos, new StatCalculationEngine.DamageResult(damage, crit, type, 0),
                attacker != null ? attacker.getUUID() : null);

        passthroughDamage.add(victim.getId());
        try {
            victim.hurt(source, damage);
        } finally {
            passthroughDamage.remove(victim.getId());
        }
        applyCooldown(victim, source, cooldownKey, cooldownTicks);
        return false;
    }

    private void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) return;
        logger.debug("[ServerDamageHandler] {} 사망", player.getName().getString());
    }

    private void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        healthManager.respawn(newPlayer);
        statManager.syncHpToClient(newPlayer);
    }

    private ServerPlayer resolveAttacker(DamageSource source) {
        Entity directEntity = source.getDirectEntity();
        Entity causingEntity = source.getEntity();

        if (directEntity instanceof ServerPlayer player) return player;
        if (directEntity instanceof Projectile && causingEntity instanceof ServerPlayer shooter) return shooter;
        if (causingEntity instanceof ServerPlayer player) return player;
        return null;
    }

    private void triggerEntityHurtFeedback(LivingEntity victim, DamageSource source, int cooldownTicks) {
        victim.invulnerableTime = Math.max(victim.invulnerableTime, cooldownTicks);
        victim.hurtTime = 10;
        victim.hurtDuration = 10;
        victim.hurtMarked = true;
        victim.level().broadcastEntityEvent(victim, (byte) 2);
        if (victim instanceof ServerPlayer player) {
            player.connection.send(new ClientboundDamageEventPacket(player, source));
            player.connection.send(new ClientboundHurtAnimationPacket(player));
        }
    }

    private void applyCooldown(LivingEntity victim, DamageSource source, String cooldownKey, int cooldownTicks) {
        if (source.getEntity() == null || cooldownTicks <= 0) return;
        long now = victim.level().getGameTime();
        cooldownByVictimAndKey
                .computeIfAbsent(victim.getId(), ignored -> new ConcurrentHashMap<>())
                .put(cooldownKey, now + cooldownTicks);
    }

    private boolean isHitOnCooldown(LivingEntity victim, String cooldownKey) {
        if (cooldownKey == null || cooldownKey.isEmpty()) return false;
        Map<String, Long> perKey = cooldownByVictimAndKey.get(victim.getId());
        if (perKey == null) return false;
        Long until = perKey.get(cooldownKey);
        if (until == null) return false;
        long now = victim.level().getGameTime();
        if (now < until) return true;
        perKey.remove(cooldownKey);
        return false;
    }

    private String resolveCooldownKey(DamageSource source, ServerPlayer playerAttacker) {
        DamageChannel channel = resolveDamageChannel(source);
        Entity sourceEntity = source.getEntity();

        if (sourceEntity == null) {
            return channel.name() + ":WORLD";
        }
        if (playerAttacker != null) {
            return channel.name() + ":P" + playerAttacker.getUUID();
        }

        boolean independent = monsterAttackGroupConfig != null && monsterAttackGroupConfig.isIndependentAttacker(sourceEntity);
        if (independent) {
            return channel.name() + ":E" + sourceEntity.getId();
        }
        return channel.name() + ":SHARED";
    }

    private DamageChannel resolveDamageChannel(DamageSource source) {
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return DamageChannel.MAGIC;
        }
        if (source.getDirectEntity() instanceof Projectile) {
            return DamageChannel.RANGED;
        }
        if (source.getEntity() != null) {
            return DamageChannel.MELEE;
        }
        return DamageChannel.ENVIRONMENT;
    }

    private double resolveVanillaAttackDamageBonus(ServerPlayer attacker, PlayerStatData attackerData) {
        if (attackerData != null && attackerData.overrideMainhandVanillaAttributes) {
            return 0.0;
        }
        AttributeInstance attackDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) return 0.0;
        // Robustly resolve weapon/NBT bonus:
        // - Prefer modifier-based delta (value - baseValue)
        // - Fallback to vanilla fist baseline delta (value - 1.0) for environments where baseValue is modified
        double byModifiers = attackDamage.getValue() - attackDamage.getBaseValue();
        double byFistBaseline = attackDamage.getValue() - 1.0;
        return Math.max(0.0, Math.max(byModifiers, byFistBaseline));
    }

    private boolean isDirectMeleeAttack(DamageSource source, ServerPlayer attacker) {
        return attacker != null && source.getDirectEntity() == attacker;
    }

    private double resolveMeleeAttackChargeMultiplier(DamageSource source, ServerPlayer attacker, float vanillaAmount) {
        if (!isDirectMeleeAttack(source, attacker)) {
            return 1.0;
        }
        AttributeInstance attackDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null || attackDamage.getValue() <= 0.0) {
            return 1.0;
        }
        // In ALLOW_DAMAGE timing, attack ticker is often already reset.
        // Derive current charge effect from vanilla-calculated damage amount instead.
        double ratio = vanillaAmount / attackDamage.getValue();
        return Math.max(0.2, Math.min(1.0, ratio));
    }

    private enum DamageChannel {
        MELEE,
        RANGED,
        MAGIC,
        ENVIRONMENT
    }
}
