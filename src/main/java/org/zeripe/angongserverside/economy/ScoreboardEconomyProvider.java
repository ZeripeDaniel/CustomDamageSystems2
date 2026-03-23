package org.zeripe.angongserverside.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * 바닐라 스코어보드 objective를 경제 원본으로 사용하는 프로바이더.
 * 스코어보드가 마스터이며, 우리는 읽고 쓰기만 한다.
 */
public class ScoreboardEconomyProvider implements EconomyProvider {

    private final MinecraftServer server;
    private final String objectiveName;
    private final Logger logger;

    public ScoreboardEconomyProvider(MinecraftServer server, String objectiveName, Logger logger) {
        this.server = server;
        this.objectiveName = objectiveName;
        this.logger = logger;

        // objective가 없으면 생성
        Scoreboard scoreboard = server.getScoreboard();
        if (scoreboard.getObjective(objectiveName) == null) {
            scoreboard.addObjective(
                    objectiveName,
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal(objectiveName),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                    true,
                    null
            );
            logger.info("[ScoreboardEconomy] 스코어보드 objective 생성: {}", objectiveName);
        }
    }

    @Override
    public long getBalance(UUID uuid) {
        var player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return 0;

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return 0;

        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(player, objective);
        return score != null ? score.value() : 0;
    }

    @Override
    public boolean deposit(UUID uuid, long amount) {
        var player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return false;

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return false;

        ScoreAccess access = scoreboard.getOrCreatePlayerScore(player, objective);
        access.set(access.get() + (int) Math.min(amount, Integer.MAX_VALUE));
        return true;
    }

    @Override
    public boolean withdraw(UUID uuid, long amount) {
        var player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return false;

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return false;

        ScoreAccess access = scoreboard.getOrCreatePlayerScore(player, objective);
        int current = access.get();
        if (current < amount) return false;
        access.set(current - (int) Math.min(amount, Integer.MAX_VALUE));
        return true;
    }

    @Override
    public void setBalance(UUID uuid, long amount) {
        var player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return;

        ScoreAccess access = scoreboard.getOrCreatePlayerScore(player, objective);
        access.set((int) Math.max(0, Math.min(amount, Integer.MAX_VALUE)));
    }
}
