package org.zeripe.angongserverside.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * 퀘스트 템플릿 — quests.json에서 로드되는 불변 정의.
 * 플레이어별 상태는 {@link PlayerQuestData}에 저장.
 */
public final class QuestDefinition {

    // ── 카테고리 상수 ──
    public static final String CAT_MAIN   = "MAIN";
    public static final String CAT_SUB    = "SUB";
    public static final String CAT_DAILY  = "DAILY";
    public static final String CAT_REPEAT = "REPEAT";
    public static final String CAT_WEEKLY = "WEEKLY";
    public static final String CAT_GUILD  = "GUILD";
    public static final String CAT_EVENT  = "EVENT";

    public String id;
    public String title;
    public String description;
    public String category;          // CAT_* 상수

    // 체인
    public String chainId;           // 퀘스트 체인 그룹 ID (null = 독립 퀘스트)
    public int chainOrder;           // 체인 내 순서

    // 반복/리셋
    public boolean repeatable;       // 보상 수령 후 재수행 가능
    public boolean dailyReset;       // 매일 자정 리셋

    // 자동 수락
    public boolean autoAccept;       // true → AVAILABLE 건너뛰고 바로 ACTIVE

    // 정렬
    public int sortOrder;

    // 선행 조건
    public List<String> prerequisiteQuests = new ArrayList<>();
    public int minLevel;             // 최소 레벨 (0 = 제한 없음)

    // 조건 & 보상
    public List<QuestObjective> objectives = new ArrayList<>();
    public List<QuestReward> rewards = new ArrayList<>();

    // 다음 퀘스트 (보상 수령 시 자동 제안)
    public String nextQuest;         // null = 없음

    /** 모든 조건의 목표 합계 (클라이언트 표시용) */
    public int getTotalMaxProgress() {
        int total = 0;
        for (QuestObjective obj : objectives) {
            total += obj.amount;
        }
        return total;
    }
}
