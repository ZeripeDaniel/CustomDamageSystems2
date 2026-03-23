package org.zeripe.angongserverside.api;

import org.zeripe.angongserverside.quest.PlayerQuestData;
import org.zeripe.angongserverside.quest.QuestManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 퀘스트 시스템 공개 API.
 * 외부 모드에서 compileOnly 의존성으로 사용.
 * 패턴: CustomDamageApi와 동일 (static + bind).
 */
public final class QuestApi {

    private static volatile QuestManager questManager;

    private QuestApi() {}

    public static void bind(QuestManager manager) {
        questManager = manager;
    }

    // ── 퀘스트 할당 ──

    /** 퀘스트를 플레이어에게 제안 (AVAILABLE 상태). 선행 조건 확인됨. */
    public static boolean offerQuest(UUID playerId, String questId) {
        return questManager != null && questManager.offerQuest(playerId, questId);
    }

    /** 퀘스트를 바로 수락 상태로 할당. */
    public static boolean assignQuest(UUID playerId, String questId) {
        return questManager != null && questManager.assignQuest(playerId, questId);
    }

    // ── 진행도 ──

    /** 특정 퀘스트의 진행도를 직접 추가. */
    public static boolean addProgress(UUID playerId, String questId, int amount) {
        return questManager != null && questManager.addProgress(playerId, questId, amount);
    }

    /** 조건 타입+키로 매칭되는 모든 활성 퀘스트 진행. */
    public static void addConditionProgress(UUID playerId, String conditionType, String key, int amount) {
        if (questManager != null) questManager.addConditionProgress(playerId, conditionType, key, amount);
    }

    /** NPC 상호작용 처리 — Bridge에서 호출. TALK_NPC + DELIVER_ITEM 매칭. */
    public static void handleNpcInteract(UUID playerId, String npcId) {
        if (questManager != null) questManager.handleNpcInteract(playerId, npcId);
    }

    // ── 조회 ──

    public static boolean isQuestActive(UUID playerId, String questId) {
        if (questManager == null) return false;
        PlayerQuestData data = questManager.getQuestData(playerId, questId);
        return data != null && data.state == org.zeripe.angongserverside.quest.PlayerQuestState.ACTIVE;
    }

    public static boolean isQuestCompleted(UUID playerId, String questId) {
        if (questManager == null) return false;
        PlayerQuestData data = questManager.getQuestData(playerId, questId);
        return data != null && (data.state == org.zeripe.angongserverside.quest.PlayerQuestState.COMPLETED
                || data.state == org.zeripe.angongserverside.quest.PlayerQuestState.REWARDED);
    }

    public static int getQuestProgress(UUID playerId, String questId) {
        if (questManager == null) return 0;
        PlayerQuestData data = questManager.getQuestData(playerId, questId);
        return data != null ? data.getTotalProgress() : 0;
    }

    public static int getQuestMaxProgress(UUID playerId, String questId) {
        if (questManager == null) return 0;
        var def = questManager.getConfig().getDefinition(questId);
        return def != null ? def.getTotalMaxProgress() : 0;
    }

    public static List<PlayerQuestData> getPlayerQuests(UUID playerId) {
        if (questManager == null) return List.of();
        return questManager.getPlayerQuests(playerId);
    }

    // ── 관리 (어드민/API) ──

    public static boolean completeQuest(UUID playerId, String questId) {
        return questManager != null && questManager.completeQuest(playerId, questId);
    }

    public static boolean resetQuest(UUID playerId, String questId) {
        return questManager != null && questManager.resetQuest(playerId, questId);
    }

    public static boolean removeQuest(UUID playerId, String questId) {
        return questManager != null && questManager.removeQuest(playerId, questId);
    }

    // ── 정의 ──

    public static Set<String> getQuestIds() {
        return questManager != null ? questManager.getConfig().getQuestIds() : Set.of();
    }

    public static void reloadDefinitions() {
        if (questManager != null) questManager.getConfig().reload();
    }
}
