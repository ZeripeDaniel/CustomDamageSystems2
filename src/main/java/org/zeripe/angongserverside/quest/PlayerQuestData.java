package org.zeripe.angongserverside.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 플레이어 한 명의 특정 퀘스트 상태.
 * QuestManager가 Map<UUID, List<PlayerQuestData>>로 관리.
 */
public final class PlayerQuestData {

    public String questId;
    public PlayerQuestState state;
    public int[] objectiveProgress;  // 조건별 진행도 (QuestDefinition.objectives 인덱스 매칭)
    public long acceptedAt;          // epoch millis (수락 시각)
    public long completedAt;         // epoch millis (완료 시각)
    public long lastResetAt;         // epoch millis (마지막 리셋)

    public PlayerQuestData(String questId, int objectiveCount) {
        this.questId = questId;
        this.state = PlayerQuestState.AVAILABLE;
        this.objectiveProgress = new int[objectiveCount];
    }

    /** 조건 인덱스의 진행도를 더함. 최대값은 외부에서 cap. */
    public void addProgress(int objectiveIndex, int amount) {
        if (objectiveIndex >= 0 && objectiveIndex < objectiveProgress.length) {
            objectiveProgress[objectiveIndex] += amount;
        }
    }

    /** 전체 진행도 합계 */
    public int getTotalProgress() {
        int total = 0;
        for (int p : objectiveProgress) {
            total += p;
        }
        return total;
    }

    /** 모든 조건이 충족됐는지 (definition 필요) */
    public boolean isAllObjectivesMet(QuestDefinition def) {
        if (def.objectives.size() != objectiveProgress.length) return false;
        for (int i = 0; i < objectiveProgress.length; i++) {
            if (objectiveProgress[i] < def.objectives.get(i).amount) return false;
        }
        return true;
    }

    // ── JSON 직렬화 ──

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("questId", questId);
        obj.addProperty("state", state.name());
        JsonArray prog = new JsonArray();
        for (int p : objectiveProgress) prog.add(p);
        obj.add("objectiveProgress", prog);
        obj.addProperty("acceptedAt", acceptedAt);
        obj.addProperty("completedAt", completedAt);
        obj.addProperty("lastResetAt", lastResetAt);
        return obj;
    }

    public static PlayerQuestData fromJson(JsonObject obj) {
        String qid = obj.get("questId").getAsString();
        JsonArray prog = obj.getAsJsonArray("objectiveProgress");
        PlayerQuestData data = new PlayerQuestData(qid, prog.size());
        data.state = PlayerQuestState.valueOf(obj.get("state").getAsString());
        for (int i = 0; i < prog.size(); i++) {
            data.objectiveProgress[i] = prog.get(i).getAsInt();
        }
        data.acceptedAt = obj.has("acceptedAt") ? obj.get("acceptedAt").getAsLong() : 0;
        data.completedAt = obj.has("completedAt") ? obj.get("completedAt").getAsLong() : 0;
        data.lastResetAt = obj.has("lastResetAt") ? obj.get("lastResetAt").getAsLong() : 0;
        return data;
    }
}
