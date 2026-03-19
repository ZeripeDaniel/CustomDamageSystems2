package org.zeripe.angongserverside.combat;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.zeripe.angongserverside.config.ServerConfig;
import org.zeripe.angongserverside.network.ServerNetworkHandler;

import java.util.UUID;

public class DamageNumberSender {
    private final MinecraftServer server;
    private DamageSkinManager skinManager;
    private double rangeXZ = 25.0;
    private double rangeY = 10.0;

    public DamageNumberSender(MinecraftServer server) {
        this.server = server;
    }

    public void setSkinManager(DamageSkinManager skinManager) {
        this.skinManager = skinManager;
    }

    public void applyConfig(ServerConfig config) {
        this.rangeXZ = config.damageNumberRangeXZ;
        this.rangeY = config.damageNumberRangeY;
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

        double rangeXZSq = rangeXZ * rangeXZ;
        String json = packet.toString();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            double dx = player.getX() - pos.x;
            double dy = player.getY() - pos.y;
            double dz = player.getZ() - pos.z;
            if (Math.abs(dy) <= rangeY && dx * dx + dz * dz <= rangeXZSq) {
                ServerNetworkHandler.sendStat(player, json);
            }
        }
    }

    /** 기존 호환: attackerUuid 없이 호출 */
    public void sendDamageNumber(Vec3 pos, StatCalculationEngine.DamageResult result) {
        sendDamageNumber(pos, result, null);
    }
}
