package org.zeripe.angongserverside.combat;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.zeripe.angongserverside.network.ServerNetworkHandler;

import java.util.UUID;

public class DamageNumberSender {
    private static final double RANGE = 48.0;
    private final MinecraftServer server;
    private DamageSkinManager skinManager;

    public DamageNumberSender(MinecraftServer server) {
        this.server = server;
    }

    public void setSkinManager(DamageSkinManager skinManager) {
        this.skinManager = skinManager;
    }

    /**
     * 데미지 넘버 전송. attackerUuid 가 null 이 아니면 해당 플레이어의 선택 스킨 ID 를 포함한다.
     */
    public void sendDamageNumber(Vec3 pos, StatCalculationEngine.DamageResult result, UUID attackerUuid) {
        JsonObject packet = new JsonObject();
        packet.addProperty("action", "damage_number");
        packet.addProperty("x", pos.x);
        packet.addProperty("y", pos.y);
        packet.addProperty("z", pos.z);
        packet.addProperty("amount", result.damage());
        packet.addProperty("crit", result.isCrit());
        packet.addProperty("type", result.type().name());

        if (attackerUuid != null && skinManager != null) {
            packet.addProperty("skinId", skinManager.getSelectedSkin(attackerUuid));
        }

        String json = packet.toString();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.position().distanceTo(pos) <= RANGE) {
                ServerNetworkHandler.sendStat(player, json);
            }
        }
    }

    /** 기존 호환: attackerUuid 없이 호출 */
    public void sendDamageNumber(Vec3 pos, StatCalculationEngine.DamageResult result) {
        sendDamageNumber(pos, result, null);
    }
}
