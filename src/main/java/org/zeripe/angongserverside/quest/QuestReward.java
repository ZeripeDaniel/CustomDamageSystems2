package org.zeripe.angongserverside.quest;

/**
 * 퀘스트 보상 (quests.json의 rewards 배열 원소).
 * 불변 — QuestDefinition과 함께 로드 후 변경 없음.
 */
public final class QuestReward {

    public static final String GOLD    = "gold";
    public static final String EXP     = "exp";
    public static final String ITEM    = "item";
    public static final String COMMAND = "command";
    public static final String STAT    = "stat";

    public String type;       // 보상 타입 (위 상수)
    public int amount;        // gold, exp, item 수량
    public String item;       // item 타입 (minecraft:iron_sword 등)
    public String command;    // command 실행 문자열 (%player% 치환)
    public String stat;       // stat 필드명 (strength 등)
    public int value;         // stat 추가값
}
