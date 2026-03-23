package org.zeripe.angongserverside.quest;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * Fabric 이벤트 → 퀘스트 조건 자동 진행.
 * KILL_MOB, KILL_NAMED, GATHER_RESOURCE 등의 이벤트를 감지하여
 * QuestManager.addConditionProgress()를 호출.
 */
public final class QuestEventListener {

    private final QuestManager questManager;
    private final Logger logger;

    public QuestEventListener(QuestManager questManager, Logger logger) {
        this.questManager = questManager;
        this.logger = logger;
    }

    public void register() {
        // 엔티티 사망 이벤트 — KILL_MOB, KILL_NAMED
        ServerLivingEntityEvents.AFTER_DEATH.register(this::onEntityDeath);

        // 블록 파괴 이벤트 — GATHER_RESOURCE
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer sp) {
                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                questManager.addConditionProgress(sp.getUUID(), QuestObjective.GATHER_RESOURCE, blockId, 1);
            }
        });

        logger.info("[Quest] 이벤트 리스너 등록 완료");
    }

    private void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            // KILL_MOB — 엔티티 타입 ID로 매칭
            String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            questManager.addConditionProgress(player.getUUID(), QuestObjective.KILL_MOB, entityTypeId, 1);

            // KILL_NAMED — 커스텀 이름으로 매칭
            if (entity.hasCustomName()) {
                String customName = entity.getCustomName().getString();
                questManager.addConditionProgress(player.getUUID(), QuestObjective.KILL_NAMED, customName, 1);
            }

            // COLLECT_MOB_DROP — 몹 사망 시 드롭 체크 (dropChance 기반)
            handleMobDropQuests(player, entityTypeId);
        }
    }

    private void handleMobDropQuests(ServerPlayer player, String entityTypeId) {
        var quests = questManager.getActiveQuests(player.getUUID());
        for (var data : quests) {
            var def = questManager.getConfig().getDefinition(data.questId);
            if (def == null) continue;

            for (int i = 0; i < def.objectives.size(); i++) {
                QuestObjective obj = def.objectives.get(i);
                if (!QuestObjective.COLLECT_MOB_DROP.equals(obj.type)) continue;
                if (!entityTypeId.equals(obj.entityType)) continue;
                if (data.objectiveProgress[i] >= obj.amount) continue;

                // dropChance 확률 체크
                double chance = obj.dropChance > 0 ? obj.dropChance : 1.0;
                if (Math.random() < chance) {
                    questManager.addConditionProgress(player.getUUID(), QuestObjective.COLLECT_MOB_DROP, entityTypeId, 1);
                }
                break; // 같은 몹 타입은 한 번만 체크
            }
        }
    }
}
