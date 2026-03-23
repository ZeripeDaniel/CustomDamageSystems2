package org.zeripe.angongserverside.quest;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.zeripe.angongserverside.combat.StatManager;

import java.util.UUID;

/**
 * 퀘스트 보상 지급 (Fabric 모드 서버용).
 * claimReward 시 QuestDefinition.rewards를 순회하며 보상 지급.
 */
public final class QuestRewardGranter {

    private final Logger logger;
    private StatManager statManager;
    private MinecraftServer server;

    public QuestRewardGranter(Logger logger) {
        this.logger = logger;
    }

    public void setStatManager(StatManager statManager) {
        this.statManager = statManager;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    /**
     * 퀘스트 보상 목록을 플레이어에게 지급.
     * @return 보상 지급 성공 여부
     */
    public boolean grantRewards(UUID playerId, QuestDefinition def) {
        if (server == null) return false;

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            logger.warn("[QuestReward] 플레이어가 오프라인: {}", playerId);
            return false;
        }

        for (QuestReward reward : def.rewards) {
            try {
                grantSingleReward(player, reward);
            } catch (Exception e) {
                logger.warn("[QuestReward] 보상 지급 실패 (quest={}, type={}): {}",
                        def.id, reward.type, e.getMessage());
            }
        }

        logger.info("[QuestReward] {} 퀘스트 보상 지급 완료: {} (보상 {}개)",
                player.getName().getString(), def.id, def.rewards.size());
        return true;
    }

    private void grantSingleReward(ServerPlayer player, QuestReward reward) {
        switch (reward.type) {
            case QuestReward.GOLD -> grantGold(player, reward.amount);
            case QuestReward.EXP -> grantExp(player, reward.amount);
            case QuestReward.ITEM -> grantItem(player, reward.item, reward.amount);
            case QuestReward.COMMAND -> grantCommand(player, reward.command);
            case QuestReward.STAT -> grantStat(player, reward.stat, reward.value);
            default -> logger.warn("[QuestReward] 알 수 없는 보상 타입: {}", reward.type);
        }
    }

    private void grantGold(ServerPlayer player, int amount) {
        if (statManager == null) return;
        var data = statManager.getData(player.getUUID());
        if (data != null) {
            data.gold += amount;
            statManager.sendFullStat(player, data);
            logger.debug("[QuestReward] {} 골드 {} 지급", player.getName().getString(), amount);
        }
    }

    private void grantExp(ServerPlayer player, int amount) {
        player.giveExperiencePoints(amount);
        logger.debug("[QuestReward] {} 경험치 {} 지급", player.getName().getString(), amount);
    }

    private void grantItem(ServerPlayer player, String itemId, int amount) {
        if (itemId == null || itemId.isEmpty()) return;

        BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)).ifPresent(ref -> {
            ItemStack stack = new ItemStack(ref.value(), amount);
            if (!player.getInventory().add(stack)) {
                // 인벤토리가 가득 찬 경우 발밑에 드롭
                player.drop(stack, false);
            }
            logger.debug("[QuestReward] {} 아이템 {} x{} 지급", player.getName().getString(), itemId, amount);
        });
    }

    private void grantCommand(ServerPlayer player, String command) {
        if (command == null || command.isEmpty() || server == null) return;

        String resolved = command.replace("%player%", player.getName().getString());
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, resolved);
        logger.debug("[QuestReward] {} 커맨드 실행: {}", player.getName().getString(), resolved);
    }

    private void grantStat(ServerPlayer player, String stat, int value) {
        if (statManager == null || stat == null) return;
        var data = statManager.getData(player.getUUID());
        if (data == null) return;

        switch (stat.toLowerCase()) {
            case "strength" -> data.equipStrength += value;
            case "agility" -> data.equipAgility += value;
            case "intelligence" -> data.equipIntelligence += value;
            case "luck" -> data.equipLuck += value;
            case "maxhp", "hp" -> data.equipHp += value;
            case "defense" -> data.equipDefense += value;
            case "attack" -> data.equipAttack += value;
            default -> {
                logger.warn("[QuestReward] 알 수 없는 스탯: {}", stat);
                return;
            }
        }
        statManager.sendFullStat(player, data);
        logger.debug("[QuestReward] {} 스탯 {} +{} 지급", player.getName().getString(), stat, value);
    }
}
