package org.zeripe.angongserverside.party;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import org.zeripe.angongserverside.combat.CustomHealthManager;

import java.util.UUID;

public class PartyMember {

    private final UUID uuid;
    private final String name;
    private int hp;
    private int maxHp;
    private boolean leader;

    public PartyMember(ServerPlayer player, boolean leader) {
        this.uuid = player.getUUID();
        this.name = player.getGameProfile().getName();
        this.leader = leader;
        sync(player, null);
    }

    /** 최신 HP 동기화 (CustomHealthManager가 있으면 커스텀 HP, 없으면 바닐라) */
    public void sync(ServerPlayer player, CustomHealthManager healthManager) {
        if (healthManager != null) {
            this.hp = healthManager.getCurrentHp(player.getUUID());
            this.maxHp = healthManager.getMaxHp(player.getUUID());
        } else {
            this.hp = (int) player.getHealth();
            this.maxHp = (int) player.getMaxHealth();
        }
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", uuid.toString());
        obj.addProperty("name", name);
        obj.addProperty("hp", hp);
        obj.addProperty("maxHp", maxHp);
        obj.addProperty("leader", leader);
        return obj;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public boolean isLeader() { return leader; }
    public void setLeader(boolean leader) { this.leader = leader; }
}
