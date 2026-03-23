package org.zeripe.angongserverside.quest;

/**
 * 퀘스트 개별 조건 (quests.json의 objectives 배열 원소).
 * 불변 — QuestDefinition과 함께 로드 후 변경 없음.
 */
public final class QuestObjective {

    // ── 조건 타입 상수 ──
    public static final String KILL_MOB         = "KILL_MOB";
    public static final String KILL_NAMED       = "KILL_NAMED";
    public static final String COLLECT_ITEM     = "COLLECT_ITEM";
    public static final String COLLECT_MOB_DROP = "COLLECT_MOB_DROP";
    public static final String DELIVER_ITEM     = "DELIVER_ITEM";
    public static final String TALK_NPC         = "TALK_NPC";
    public static final String REACH_LOCATION   = "REACH_LOCATION";
    public static final String CRAFT_ITEM       = "CRAFT_ITEM";
    public static final String GATHER_RESOURCE  = "GATHER_RESOURCE";
    public static final String INTERACT_OBJECT  = "INTERACT_OBJECT";
    public static final String LEVEL_REACH      = "LEVEL_REACH";
    public static final String FISHING          = "FISHING";
    public static final String CUSTOM           = "CUSTOM";

    public String type;           // 조건 타입 (위 상수)
    public String description;    // 클라이언트에 표시할 설명

    // ── 타입별 파라미터 (해당하지 않으면 null/0) ──
    public String entityType;     // KILL_MOB, COLLECT_MOB_DROP
    public String entityName;     // KILL_NAMED
    public String item;           // COLLECT_ITEM, COLLECT_MOB_DROP, DELIVER_ITEM, CRAFT_ITEM, FISHING
    public String block;          // GATHER_RESOURCE, INTERACT_OBJECT
    public String npcId;          // TALK_NPC, DELIVER_ITEM (toNpc)
    public String fromNpc;        // DELIVER_ITEM
    public String customId;       // CUSTOM
    public double dropChance;     // COLLECT_MOB_DROP (0.0~1.0)
    public double x, y, z;        // REACH_LOCATION
    public double radius;         // REACH_LOCATION
    public int level;             // LEVEL_REACH
    public int amount;            // 목표 수량 (기본 1)

    public QuestObjective() {
        this.amount = 1;
    }
}
