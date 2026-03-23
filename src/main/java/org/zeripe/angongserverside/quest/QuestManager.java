package org.zeripe.angongserverside.quest;

import com.google.gson.*;
import org.slf4j.Logger;
import org.zeripe.angongserverside.storage.PlayerDataStorage;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 퀘스트 시스템 핵심 로직.
 * 퀘스트 제안/수락/거절/진행/완료/보상 처리.
 */
public final class QuestManager {

    private final Logger logger;
    private final QuestConfig config;
    private final PlayerDataStorage storage;
    private final Map<UUID, List<PlayerQuestData>> playerQuests = new ConcurrentHashMap<>();
    private int dailyResetHour = 9;
    private ZoneId dailyResetZone = ZoneId.of("Asia/Seoul");

    /** 퀘스트 상태 변경 시 호출되는 콜백 (네트워크 전송용) */
    @FunctionalInterface
    public interface QuestUpdateCallback {
        void onQuestUpdate(UUID playerId, PlayerQuestData questData, QuestDefinition definition);
    }

    private QuestUpdateCallback updateCallback;
    private QuestRewardGranter rewardGranter;

    public QuestManager(Logger logger, QuestConfig config, PlayerDataStorage storage) {
        this.logger = logger;
        this.config = config;
        this.storage = storage;
    }

    public void setDailyResetTime(int hour, String timezone) {
        this.dailyResetHour = Math.max(0, Math.min(23, hour));
        try {
            this.dailyResetZone = ZoneId.of(timezone);
        } catch (Exception e) {
            logger.warn("[Quest] 잘못된 timezone: {}, Asia/Seoul 사용", timezone);
            this.dailyResetZone = ZoneId.of("Asia/Seoul");
        }
    }

    public void setUpdateCallback(QuestUpdateCallback callback) {
        this.updateCallback = callback;
    }

    public void setRewardGranter(QuestRewardGranter rewardGranter) {
        this.rewardGranter = rewardGranter;
    }

    public QuestConfig getConfig() {
        return config;
    }

    // ── 플레이어 라이프사이클 ──

    public void onPlayerJoin(UUID uuid) {
        List<PlayerQuestData> quests = loadPlayerQuests(uuid);
        playerQuests.put(uuid, quests);
        checkDailyResets(uuid);
        autoOfferQuests(uuid);
    }

    public void onPlayerQuit(UUID uuid) {
        savePlayerQuests(uuid);
        playerQuests.remove(uuid);
    }

    public void saveAll() {
        for (UUID uuid : playerQuests.keySet()) {
            savePlayerQuests(uuid);
        }
    }

    // ── 퀘스트 흐름 ──

    /** 퀘스트를 플레이어에게 제안 (AVAILABLE 상태) */
    public boolean offerQuest(UUID playerId, String questId) {
        QuestDefinition def = config.getDefinition(questId);
        if (def == null) return false;

        List<PlayerQuestData> quests = getOrCreate(playerId);

        // 이미 진행 중/완료된 퀘스트는 제안 불가
        PlayerQuestData existing = findQuest(quests, questId);
        if (existing != null && existing.state != PlayerQuestState.REWARDED) return false;
        if (existing != null && existing.state == PlayerQuestState.REWARDED && !def.repeatable && !def.dailyReset) return false;

        // 선행 조건 확인
        if (!checkPrerequisites(playerId, def)) return false;

        // 기존 REWARDED 제거 (반복 퀘스트)
        if (existing != null) {
            quests.remove(existing);
        }

        PlayerQuestData data = new PlayerQuestData(questId, def.objectives.size());

        if (def.autoAccept) {
            data.state = PlayerQuestState.ACTIVE;
            data.acceptedAt = System.currentTimeMillis();
        }

        quests.add(data);
        notifyUpdate(playerId, data, def);
        return true;
    }

    /** 퀘스트 제안 + 자동 수락 (바로 ACTIVE) */
    public boolean assignQuest(UUID playerId, String questId) {
        QuestDefinition def = config.getDefinition(questId);
        if (def == null) return false;

        List<PlayerQuestData> quests = getOrCreate(playerId);
        PlayerQuestData existing = findQuest(quests, questId);
        if (existing != null && existing.state != PlayerQuestState.REWARDED) return false;
        if (existing != null && existing.state == PlayerQuestState.REWARDED && !def.repeatable && !def.dailyReset) return false;

        if (!checkPrerequisites(playerId, def)) return false;

        if (existing != null) quests.remove(existing);

        PlayerQuestData data = new PlayerQuestData(questId, def.objectives.size());
        data.state = PlayerQuestState.ACTIVE;
        data.acceptedAt = System.currentTimeMillis();
        quests.add(data);
        notifyUpdate(playerId, data, def);
        return true;
    }

    /** 플레이어가 퀘스트 수락 */
    public boolean acceptQuest(UUID playerId, String questId) {
        PlayerQuestData data = getQuestData(playerId, questId);
        if (data == null || data.state != PlayerQuestState.AVAILABLE) return false;

        data.state = PlayerQuestState.ACTIVE;
        data.acceptedAt = System.currentTimeMillis();

        QuestDefinition def = config.getDefinition(questId);
        notifyUpdate(playerId, data, def);
        logger.info("[Quest] {} 퀘스트 수락: {}", playerId, questId);
        return true;
    }

    /** 플레이어가 퀘스트 거절 */
    public boolean rejectQuest(UUID playerId, String questId) {
        List<PlayerQuestData> quests = playerQuests.get(playerId);
        if (quests == null) return false;

        PlayerQuestData data = findQuest(quests, questId);
        if (data == null || data.state != PlayerQuestState.AVAILABLE) return false;

        quests.remove(data);
        logger.info("[Quest] {} 퀘스트 거절: {}", playerId, questId);
        return true;
    }

    /** 보상 수령 */
    public boolean claimReward(UUID playerId, String questId) {
        PlayerQuestData data = getQuestData(playerId, questId);
        if (data == null || data.state != PlayerQuestState.COMPLETED) return false;

        QuestDefinition def = config.getDefinition(questId);

        // 보상 지급
        if (rewardGranter != null && def != null && !def.rewards.isEmpty()) {
            rewardGranter.grantRewards(playerId, def);
        }

        data.state = PlayerQuestState.REWARDED;
        data.lastResetAt = System.currentTimeMillis();
        notifyUpdate(playerId, data, def);
        logger.info("[Quest] {} 퀘스트 보상 수령: {}", playerId, questId);

        // nextQuest 자동 제안
        if (def != null && def.nextQuest != null && !def.nextQuest.isEmpty()) {
            offerQuest(playerId, def.nextQuest);
        }

        return true;
    }

    // ── 진행도 ──

    /** 특정 퀘스트의 진행도 직접 추가 */
    public boolean addProgress(UUID playerId, String questId, int amount) {
        PlayerQuestData data = getQuestData(playerId, questId);
        if (data == null || data.state != PlayerQuestState.ACTIVE) return false;

        QuestDefinition def = config.getDefinition(questId);
        if (def == null) return false;

        boolean changed = false;
        for (int i = 0; i < data.objectiveProgress.length && i < def.objectives.size(); i++) {
            int max = def.objectives.get(i).amount;
            if (data.objectiveProgress[i] < max) {
                data.objectiveProgress[i] = Math.min(data.objectiveProgress[i] + amount, max);
                changed = true;
                break; // 첫 번째 미완료 조건에 적용
            }
        }

        if (changed) {
            checkCompletion(playerId, data, def);
            notifyUpdate(playerId, data, def);
        }
        return changed;
    }

    /** 조건 타입+키로 매칭되는 모든 퀘스트 진행 */
    public void addConditionProgress(UUID playerId, String conditionType, String key, int amount) {
        List<PlayerQuestData> quests = playerQuests.get(playerId);
        if (quests == null) return;

        for (PlayerQuestData data : quests) {
            if (data.state != PlayerQuestState.ACTIVE) continue;

            QuestDefinition def = config.getDefinition(data.questId);
            if (def == null) continue;

            boolean changed = false;
            for (int i = 0; i < def.objectives.size(); i++) {
                QuestObjective obj = def.objectives.get(i);
                if (!obj.type.equals(conditionType)) continue;
                if (!matchesConditionKey(obj, key)) continue;

                int max = obj.amount;
                if (data.objectiveProgress[i] < max) {
                    data.objectiveProgress[i] = Math.min(data.objectiveProgress[i] + amount, max);
                    changed = true;
                }
            }

            if (changed) {
                checkCompletion(playerId, data, def);
                notifyUpdate(playerId, data, def);
            }
        }
    }

    /** NPC 상호작용 처리 — Bridge에서 호출 */
    public void handleNpcInteract(UUID playerId, String npcId) {
        addConditionProgress(playerId, QuestObjective.TALK_NPC, npcId, 1);
        // DELIVER_ITEM 은 별도 처리 필요 (인벤토리 체크)
    }

    // ── 조회 ──

    public PlayerQuestData getQuestData(UUID playerId, String questId) {
        List<PlayerQuestData> quests = playerQuests.get(playerId);
        return quests != null ? findQuest(quests, questId) : null;
    }

    public List<PlayerQuestData> getPlayerQuests(UUID playerId) {
        List<PlayerQuestData> quests = playerQuests.get(playerId);
        return quests != null ? Collections.unmodifiableList(quests) : Collections.emptyList();
    }

    public List<PlayerQuestData> getActiveQuests(UUID playerId) {
        return getPlayerQuests(playerId).stream()
                .filter(q -> q.state == PlayerQuestState.ACTIVE)
                .toList();
    }

    // ── 관리 ──

    public boolean completeQuest(UUID playerId, String questId) {
        PlayerQuestData data = getQuestData(playerId, questId);
        if (data == null || data.state != PlayerQuestState.ACTIVE) return false;

        QuestDefinition def = config.getDefinition(questId);
        if (def == null) return false;

        // 모든 조건 충족 처리
        for (int i = 0; i < data.objectiveProgress.length && i < def.objectives.size(); i++) {
            data.objectiveProgress[i] = def.objectives.get(i).amount;
        }
        data.state = PlayerQuestState.COMPLETED;
        data.completedAt = System.currentTimeMillis();
        notifyUpdate(playerId, data, def);
        return true;
    }

    public boolean resetQuest(UUID playerId, String questId) {
        List<PlayerQuestData> quests = playerQuests.get(playerId);
        if (quests == null) return false;

        PlayerQuestData data = findQuest(quests, questId);
        if (data == null) return false;

        quests.remove(data);
        return true;
    }

    public boolean removeQuest(UUID playerId, String questId) {
        return resetQuest(playerId, questId);
    }

    // ── 일일 리셋 ──

    public void checkDailyResets(UUID playerId) {
        List<PlayerQuestData> quests = playerQuests.get(playerId);
        if (quests == null) return;

        // 설정된 시간대 + 시각 기준으로 리셋 시점 계산
        ZonedDateTime now = ZonedDateTime.now(dailyResetZone);
        ZonedDateTime todayReset = now.toLocalDate().atTime(dailyResetHour, 0).atZone(dailyResetZone);
        if (now.isBefore(todayReset)) {
            todayReset = todayReset.minusDays(1);
        }
        long resetEpoch = todayReset.toInstant().toEpochMilli();

        Iterator<PlayerQuestData> it = quests.iterator();
        while (it.hasNext()) {
            PlayerQuestData data = it.next();
            QuestDefinition def = config.getDefinition(data.questId);
            if (def == null || !def.dailyReset) continue;

            if (data.state == PlayerQuestState.REWARDED && data.lastResetAt < resetEpoch) {
                it.remove(); // 리셋 — 다시 제안 가능
            }
        }
    }

    /** autoAccept 퀘스트 자동 offer (접속 시 호출) */
    private void autoOfferQuests(UUID playerId) {
        List<PlayerQuestData> quests = getOrCreate(playerId);
        for (QuestDefinition def : config.getDefinitions().values()) {
            if (!def.autoAccept) continue;
            PlayerQuestData existing = findQuest(quests, def.id);
            // 이미 진행/완료 중이면 스킵
            if (existing != null && existing.state != PlayerQuestState.REWARDED) continue;
            // REWARDED인데 dailyReset=false & repeatable=false 면 스킵
            if (existing != null && !def.dailyReset && !def.repeatable) continue;
            // offer (autoAccept이므로 바로 ACTIVE로 전환됨)
            offerQuest(playerId, def.id);
        }
    }

    // ── JSON (클라이언트 전송용) ──

    public JsonObject buildQuestListJson(UUID playerId) {
        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        resp.addProperty("action", "quest_list");

        JsonArray arr = new JsonArray();
        for (PlayerQuestData data : getPlayerQuests(playerId)) {
            QuestDefinition def = config.getDefinition(data.questId);
            if (def != null) {
                arr.add(buildQuestEntryJson(data, def));
            }
        }
        resp.add("quests", arr);
        return resp;
    }

    public JsonObject buildQuestUpdateJson(PlayerQuestData data, QuestDefinition def) {
        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        resp.addProperty("action", "quest_update");
        resp.add("quest", buildQuestEntryJson(data, def));
        return resp;
    }

    public JsonObject buildQuestOfferJson(PlayerQuestData data, QuestDefinition def) {
        JsonObject resp = new JsonObject();
        resp.addProperty("success", true);
        resp.addProperty("action", "quest_offer");
        resp.add("quest", buildQuestEntryJson(data, def));
        return resp;
    }

    private JsonObject buildQuestEntryJson(PlayerQuestData data, QuestDefinition def) {
        JsonObject q = new JsonObject();
        q.addProperty("id", def.id);
        q.addProperty("title", def.title);
        q.addProperty("description", def.description);
        q.addProperty("category", def.category);
        q.addProperty("status", data.state.name());
        q.addProperty("completed", data.state == PlayerQuestState.COMPLETED || data.state == PlayerQuestState.REWARDED);

        // 전체 진행도
        int totalProgress = 0;
        int totalMax = 0;
        JsonArray objectives = new JsonArray();
        for (int i = 0; i < def.objectives.size(); i++) {
            QuestObjective obj = def.objectives.get(i);
            int prog = i < data.objectiveProgress.length ? data.objectiveProgress[i] : 0;
            prog = Math.min(prog, obj.amount);
            totalProgress += prog;
            totalMax += obj.amount;

            JsonObject o = new JsonObject();
            o.addProperty("description", obj.description);
            o.addProperty("progress", prog);
            o.addProperty("maxProgress", obj.amount);
            objectives.add(o);
        }
        q.addProperty("progress", totalProgress);
        q.addProperty("maxProgress", totalMax);
        q.add("objectives", objectives);

        return q;
    }

    // ── 내부 ──

    private boolean matchesConditionKey(QuestObjective obj, String key) {
        if (key == null) return false;
        return switch (obj.type) {
            case QuestObjective.KILL_MOB -> key.equals(obj.entityType);
            case QuestObjective.KILL_NAMED -> key.equals(obj.entityName);
            case QuestObjective.COLLECT_ITEM, QuestObjective.CRAFT_ITEM, QuestObjective.FISHING -> key.equals(obj.item);
            case QuestObjective.COLLECT_MOB_DROP -> key.equals(obj.entityType); // 몹 타입으로 매칭
            case QuestObjective.TALK_NPC -> key.equals(obj.npcId);
            case QuestObjective.GATHER_RESOURCE, QuestObjective.INTERACT_OBJECT -> key.equals(obj.block);
            case QuestObjective.REACH_LOCATION -> true; // 위치는 별도 체크
            case QuestObjective.CUSTOM -> key.equals(obj.customId);
            default -> false;
        };
    }

    private void checkCompletion(UUID playerId, PlayerQuestData data, QuestDefinition def) {
        if (data.state == PlayerQuestState.ACTIVE && data.isAllObjectivesMet(def)) {
            data.state = PlayerQuestState.COMPLETED;
            data.completedAt = System.currentTimeMillis();
            logger.info("[Quest] {} 퀘스트 완료: {}", playerId, data.questId);
        }
    }

    private boolean checkPrerequisites(UUID playerId, QuestDefinition def) {
        List<PlayerQuestData> quests = playerQuests.getOrDefault(playerId, Collections.emptyList());
        for (String prereqId : def.prerequisiteQuests) {
            PlayerQuestData prereq = findQuest(quests, prereqId);
            if (prereq == null || prereq.state != PlayerQuestState.REWARDED) return false;
        }
        return true;
    }

    private List<PlayerQuestData> getOrCreate(UUID playerId) {
        return playerQuests.computeIfAbsent(playerId, k -> new ArrayList<>());
    }

    private PlayerQuestData findQuest(List<PlayerQuestData> quests, String questId) {
        for (PlayerQuestData q : quests) {
            if (q.questId.equals(questId)) return q;
        }
        return null;
    }

    private void notifyUpdate(UUID playerId, PlayerQuestData data, QuestDefinition def) {
        if (updateCallback != null && def != null) {
            updateCallback.onQuestUpdate(playerId, data, def);
        }
    }

    // ── 저장/로드 ──

    private List<PlayerQuestData> loadPlayerQuests(UUID uuid) {
        List<PlayerQuestData> list = new ArrayList<>();
        try {
            String json = storage.loadQuestDataJson(uuid);
            if (json != null && !json.isBlank()) {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray arr = root.getAsJsonArray("quests");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        list.add(PlayerQuestData.fromJson(el.getAsJsonObject()));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("[Quest] {} 퀘스트 데이터 로드 실패: {}", uuid, e.getMessage());
        }
        return list;
    }

    private void savePlayerQuests(UUID uuid) {
        List<PlayerQuestData> quests = playerQuests.get(uuid);
        if (quests == null) return;

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (PlayerQuestData data : quests) {
            arr.add(data.toJson());
        }
        root.add("quests", arr);

        try {
            storage.saveQuestDataJson(uuid, root.toString());
        } catch (Exception e) {
            logger.warn("[Quest] {} 퀘스트 데이터 저장 실패: {}", uuid, e.getMessage());
        }
    }
}
