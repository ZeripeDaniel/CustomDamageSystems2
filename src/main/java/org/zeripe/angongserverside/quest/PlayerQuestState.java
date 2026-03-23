package org.zeripe.angongserverside.quest;

public enum PlayerQuestState {
    AVAILABLE,   // 제안됨 — 수락/거절 대기
    ACTIVE,      // 수락 — 진행 중
    COMPLETED,   // 조건 충족 — 보상 수령 대기
    REWARDED     // 보상 수령 완료
}
