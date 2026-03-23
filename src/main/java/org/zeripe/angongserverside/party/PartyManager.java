package org.zeripe.angongserverside.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.zeripe.angongserverside.combat.CustomHealthManager;
import org.zeripe.angongcommon.network.PartyPayload;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {

    private static final int MAX_PARTY_SIZE = 4;
    private static final long INVITE_TIMEOUT_MS = 30_000; // 30초

    private final Logger logger;
    private CustomHealthManager healthManager;

    // 플레이어UUID → 파티ID(리더UUID)
    private final Map<UUID, UUID> playerPartyMap = new ConcurrentHashMap<>();
    // 파티ID → 멤버 리스트
    private final Map<UUID, List<PartyMember>> parties = new ConcurrentHashMap<>();
    // 초대받은 플레이어UUID → 초대 정보
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();

    private record PendingInvite(UUID fromUuid, String fromName, long timestamp) {}

    public PartyManager(Logger logger) {
        this.logger = logger;
    }

    public void setHealthManager(CustomHealthManager healthManager) {
        this.healthManager = healthManager;
    }

    // ── 파티 생성 ──────────────────────────────────────────

    public JsonObject createParty(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (playerPartyMap.containsKey(uuid)) {
            return errorJson("이미 파티에 소속되어 있습니다.");
        }
        List<PartyMember> members = new ArrayList<>();
        members.add(new PartyMember(player, true));
        parties.put(uuid, members);
        playerPartyMap.put(uuid, uuid);
        logger.info("[Party] {} 파티 생성", player.getGameProfile().getName());
        broadcastPartyUpdate(uuid, player.server);
        return successJson("파티가 생성되었습니다.");
    }

    // ── 초대 ──────────────────────────────────────────────

    public JsonObject invitePlayer(ServerPlayer leader, String targetName, MinecraftServer server) {
        UUID leaderUuid = leader.getUUID();
        UUID partyId = playerPartyMap.get(leaderUuid);

        // 파티가 없으면 자동 생성
        if (partyId == null) {
            createParty(leader);
            partyId = leaderUuid;
        }

        if (!partyId.equals(leaderUuid)) {
            return errorJson("파티장만 초대할 수 있습니다.");
        }

        List<PartyMember> members = parties.get(partyId);
        if (members != null && members.size() >= MAX_PARTY_SIZE) {
            return errorJson("파티가 가득 찼습니다. (최대 " + MAX_PARTY_SIZE + "인)");
        }

        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return errorJson("플레이어 '" + targetName + "'를 찾을 수 없습니다.");
        }
        if (target.getUUID().equals(leaderUuid)) {
            return errorJson("자기 자신은 초대할 수 없습니다.");
        }
        if (playerPartyMap.containsKey(target.getUUID())) {
            return errorJson("해당 플레이어는 이미 파티에 소속되어 있습니다.");
        }
        if (pendingInvites.containsKey(target.getUUID())) {
            return errorJson("해당 플레이어에게 이미 초대가 전송되었습니다.");
        }

        // 초대 저장
        pendingInvites.put(target.getUUID(),
                new PendingInvite(leaderUuid, leader.getGameProfile().getName(), System.currentTimeMillis()));

        // 타겟에게 S2C 초대 패킷
        JsonObject invite = new JsonObject();
        invite.addProperty("success", true);
        invite.addProperty("action", "party_invite");
        invite.addProperty("from", leaderUuid.toString());
        invite.addProperty("fromName", leader.getGameProfile().getName());
        sendPartyPayload(target, invite.toString());

        // 타겟에게 채팅 메시지
        target.sendSystemMessage(Component.literal(
                "§e[파티] §f" + leader.getGameProfile().getName() + "§e님이 파티에 초대했습니다! ALT를 눌러 수락/거절하세요."));

        logger.info("[Party] {} → {} 초대", leader.getGameProfile().getName(), targetName);
        return successJson(targetName + "에게 초대를 보냈습니다.");
    }

    // ── 초대 수락 ─────────────────────────────────────────

    public JsonObject acceptInvite(ServerPlayer player, String fromUuidStr, MinecraftServer server) {
        UUID playerUuid = player.getUUID();
        PendingInvite invite = pendingInvites.remove(playerUuid);
        if (invite == null) {
            return errorJson("받은 초대가 없습니다.");
        }

        UUID inviterUuid = invite.fromUuid();
        if (fromUuidStr != null && !fromUuidStr.isEmpty()) {
            try {
                inviterUuid = UUID.fromString(fromUuidStr);
            } catch (IllegalArgumentException ignored) {}
        }

        UUID partyId = playerPartyMap.get(inviterUuid);
        if (partyId == null) {
            return errorJson("초대한 파티가 더 이상 존재하지 않습니다.");
        }

        List<PartyMember> members = parties.get(partyId);
        if (members == null) {
            return errorJson("파티가 존재하지 않습니다.");
        }
        if (members.size() >= MAX_PARTY_SIZE) {
            return errorJson("파티가 가득 찼습니다.");
        }
        if (playerPartyMap.containsKey(playerUuid)) {
            return errorJson("이미 파티에 소속되어 있습니다.");
        }

        members.add(new PartyMember(player, false));
        playerPartyMap.put(playerUuid, partyId);
        logger.info("[Party] {} 파티 수락 (리더: {})", player.getGameProfile().getName(), invite.fromName());
        broadcastPartyUpdate(partyId, server);
        return successJson("파티에 참가했습니다.");
    }

    // ── 초대 거절 ─────────────────────────────────────────

    public JsonObject rejectInvite(ServerPlayer player, String fromUuidStr, MinecraftServer server) {
        UUID playerUuid = player.getUUID();
        PendingInvite invite = pendingInvites.remove(playerUuid);
        if (invite == null) {
            return errorJson("받은 초대가 없습니다.");
        }

        // 초대자에게 알림
        ServerPlayer inviter = server.getPlayerList().getPlayer(invite.fromUuid());
        if (inviter != null) {
            inviter.sendSystemMessage(Component.literal(
                    "§e[파티] §f" + player.getGameProfile().getName() + "§e님이 초대를 거절했습니다."));
        }

        logger.info("[Party] {} 초대 거절", player.getGameProfile().getName());
        return successJson("초대를 거절했습니다.");
    }

    // ── 파티 탈퇴 ─────────────────────────────────────────

    public JsonObject leaveParty(ServerPlayer player, MinecraftServer server) {
        UUID uuid = player.getUUID();
        UUID partyId = playerPartyMap.remove(uuid);
        if (partyId == null) {
            return errorJson("파티에 소속되어 있지 않습니다.");
        }

        List<PartyMember> members = parties.get(partyId);
        if (members == null) return successJson("파티를 떠났습니다.");

        members.removeIf(m -> m.getUuid().equals(uuid));

        if (members.isEmpty()) {
            parties.remove(partyId);
        } else if (partyId.equals(uuid)) {
            // 리더가 나갔으면 다음 사람이 리더 승계
            PartyMember newLeader = members.get(0);
            newLeader.setLeader(true);
            UUID newPartyId = newLeader.getUuid();
            parties.put(newPartyId, parties.remove(partyId));
            for (PartyMember m : members) {
                playerPartyMap.put(m.getUuid(), newPartyId);
            }
            broadcastPartyUpdate(newPartyId, server);
        } else {
            broadcastPartyUpdate(partyId, server);
        }

        // 탈퇴한 플레이어에게 빈 파티 전송
        sendEmptyPartyUpdate(player);
        logger.info("[Party] {} 파티 탈퇴", player.getGameProfile().getName());
        return successJson("파티를 떠났습니다.");
    }

    // ── 파티 해산 ─────────────────────────────────────────

    public JsonObject disbandParty(ServerPlayer leader, MinecraftServer server) {
        UUID leaderUuid = leader.getUUID();
        UUID partyId = playerPartyMap.get(leaderUuid);
        if (partyId == null) {
            return errorJson("파티에 소속되어 있지 않습니다.");
        }
        if (!partyId.equals(leaderUuid)) {
            return errorJson("파티장만 해산할 수 있습니다.");
        }

        List<PartyMember> members = parties.remove(partyId);
        if (members == null) return successJson("파티가 해산되었습니다.");

        for (PartyMember m : members) {
            playerPartyMap.remove(m.getUuid());
            ServerPlayer mp = server.getPlayerList().getPlayer(m.getUuid());
            if (mp != null) {
                // 해산 알림 전송
                JsonObject disbanded = new JsonObject();
                disbanded.addProperty("success", true);
                disbanded.addProperty("action", "party_disbanded");
                sendPartyPayload(mp, disbanded.toString());
                sendEmptyPartyUpdate(mp);
            }
        }

        logger.info("[Party] {} 파티 해산", leader.getGameProfile().getName());
        return successJson("파티가 해산되었습니다.");
    }

    // ── 추방 ──────────────────────────────────────────────

    public JsonObject kickMember(ServerPlayer leader, String targetUuidStr, MinecraftServer server) {
        UUID leaderUuid = leader.getUUID();
        UUID partyId = playerPartyMap.get(leaderUuid);
        if (partyId == null || !partyId.equals(leaderUuid)) {
            return errorJson("파티장만 추방할 수 있습니다.");
        }

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(targetUuidStr);
        } catch (IllegalArgumentException e) {
            return errorJson("잘못된 플레이어 UUID입니다.");
        }

        if (targetUuid.equals(leaderUuid)) {
            return errorJson("자기 자신은 추방할 수 없습니다.");
        }

        List<PartyMember> members = parties.get(partyId);
        if (members == null) return errorJson("파티가 존재하지 않습니다.");

        boolean removed = members.removeIf(m -> m.getUuid().equals(targetUuid));
        if (!removed) return errorJson("해당 플레이어는 파티에 없습니다.");

        playerPartyMap.remove(targetUuid);
        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target != null) {
            target.sendSystemMessage(Component.literal("§e[파티] §c파티에서 추방되었습니다."));
            sendEmptyPartyUpdate(target);
        }

        broadcastPartyUpdate(partyId, server);
        logger.info("[Party] {} 추방 by {}", targetUuidStr, leader.getGameProfile().getName());
        return successJson("추방했습니다.");
    }

    /** 두 플레이어가 같은 파티에 속해있는지 확인 */
    public boolean areInSameParty(UUID a, UUID b) {
        UUID partyA = playerPartyMap.get(a);
        UUID partyB = playerPartyMap.get(b);
        return partyA != null && partyA.equals(partyB);
    }

    // ── 파티 정보 조회 ────────────────────────────────────

    public JsonObject getPartyData(ServerPlayer player, MinecraftServer server) {
        UUID uuid = player.getUUID();
        UUID partyId = playerPartyMap.get(uuid);

        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.addProperty("action", "party_data");

        if (partyId == null) {
            res.add("party", new JsonArray());
            return res;
        }

        List<PartyMember> members = parties.get(partyId);
        if (members == null) {
            res.add("party", new JsonArray());
            return res;
        }

        syncHp(partyId, server);
        JsonArray arr = new JsonArray();
        members.forEach(m -> arr.add(m.toJson()));
        res.add("party", arr);
        return res;
    }

    // ── HP 동기화 & 브로드캐스트 ──────────────────────────

    public void tickHpSync(MinecraftServer server) {
        // 만료된 초대 정리
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(e -> now - e.getValue().timestamp() > INVITE_TIMEOUT_MS);

        // 모든 파티의 HP 동기화 후 브로드캐스트
        for (Map.Entry<UUID, List<PartyMember>> entry : parties.entrySet()) {
            UUID partyId = entry.getKey();
            List<PartyMember> members = entry.getValue();

            boolean changed = false;
            for (PartyMember m : members) {
                ServerPlayer sp = server.getPlayerList().getPlayer(m.getUuid());
                if (sp != null) {
                    int oldHp = m.getHp();
                    int oldMaxHp = m.getMaxHp();
                    m.sync(sp, healthManager);
                    if (m.getHp() != oldHp || m.getMaxHp() != oldMaxHp) {
                        changed = true;
                    }
                }
            }

            if (changed) {
                broadcastPartyUpdate(partyId, server);
            }
        }
    }

    // ── 플레이어 퇴장 처리 ────────────────────────────────

    public void onPlayerQuit(UUID uuid, MinecraftServer server) {
        pendingInvites.remove(uuid);

        UUID partyId = playerPartyMap.remove(uuid);
        if (partyId == null) return;

        List<PartyMember> members = parties.get(partyId);
        if (members == null) return;

        members.removeIf(m -> m.getUuid().equals(uuid));

        if (members.isEmpty()) {
            parties.remove(partyId);
        } else if (partyId.equals(uuid)) {
            // 리더가 나감 → 승계
            PartyMember newLeader = members.get(0);
            newLeader.setLeader(true);
            UUID newPartyId = newLeader.getUuid();
            parties.put(newPartyId, parties.remove(partyId));
            for (PartyMember m : members) {
                playerPartyMap.put(m.getUuid(), newPartyId);
            }
            broadcastPartyUpdate(newPartyId, server);
        } else {
            broadcastPartyUpdate(partyId, server);
        }
    }

    // ── 유틸리티 ──────────────────────────────────────────

    private void syncHp(UUID partyId, MinecraftServer server) {
        List<PartyMember> members = parties.get(partyId);
        if (members == null) return;
        for (PartyMember m : members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(m.getUuid());
            if (sp != null) m.sync(sp, healthManager);
        }
    }

    private void broadcastPartyUpdate(UUID partyId, MinecraftServer server) {
        List<PartyMember> members = parties.get(partyId);
        if (members == null) return;

        syncHp(partyId, server);

        JsonObject update = new JsonObject();
        update.addProperty("success", true);
        update.addProperty("action", "party_update");
        JsonArray arr = new JsonArray();
        members.forEach(m -> arr.add(m.toJson()));
        update.add("party", arr);

        String json = update.toString();
        for (PartyMember m : members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(m.getUuid());
            if (sp != null) sendPartyPayload(sp, json);
        }
    }

    private void sendEmptyPartyUpdate(ServerPlayer player) {
        JsonObject update = new JsonObject();
        update.addProperty("success", true);
        update.addProperty("action", "party_update");
        update.add("party", new JsonArray());
        sendPartyPayload(player, update.toString());
    }

    private void sendPartyPayload(ServerPlayer player, String json) {
        ServerPlayNetworking.send(player, PartyPayload.of(json));
    }

    private JsonObject successJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.addProperty("action", "party_response");
        obj.addProperty("message", message);
        return obj;
    }

    private JsonObject errorJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", false);
        obj.addProperty("action", "party_error");
        obj.addProperty("message", message);
        return obj;
    }
}
