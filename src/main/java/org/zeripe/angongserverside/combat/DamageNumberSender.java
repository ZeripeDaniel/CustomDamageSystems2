package org.zeripe.angongserverside.combat;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.zeripe.angongserverside.network.ServerNetworkHandler;

public class DamageNumberSender {
    private static final double RANGE = 48.0;
    private final MinecraftServer server;

    public DamageNumberSender(MinecraftServer server) {
        this.server = server;
    }

    public void sendDamageNumber(Vec3 pos, StatCalculationEngine.DamageResult result) {
        JsonObject packet = new JsonObject();
        packet.addProperty("action", "damage_number");
        packet.addProperty("x", pos.x);
        packet.addProperty("y", pos.y);
        packet.addProperty("z", pos.z);
        packet.addProperty("amount", result.damage());
        packet.addProperty("crit", result.isCrit());
        packet.addProperty("type", result.type().name());
        String json = packet.toString();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.position().distanceTo(pos) <= RANGE) {
                ServerNetworkHandler.sendStat(player, json);
            }
        }
    }
}
