package org.zeripe.angongserverside.quest;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * quests.json 로더.
 * 서버 시작 시 로드, 리로드 지원.
 */
public final class QuestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "quests.json";

    private final Logger logger;
    private final Map<String, QuestDefinition> definitions = new LinkedHashMap<>();

    public QuestConfig(Logger logger) {
        this.logger = logger;
    }

    /** 퀘스트 정의 로드. 파일이 없으면 샘플 생성. */
    public void load() {
        definitions.clear();
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (!Files.exists(path)) {
            createSample(path);
        }

        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray quests = root.getAsJsonArray("quests");
            if (quests == null) {
                logger.warn("[QuestConfig] quests.json에 'quests' 배열이 없습니다.");
                return;
            }

            for (JsonElement el : quests) {
                try {
                    QuestDefinition def = parseDefinition(el.getAsJsonObject());
                    if (def.id != null && !def.id.isEmpty()) {
                        definitions.put(def.id, def);
                    }
                } catch (Exception e) {
                    logger.warn("[QuestConfig] 퀘스트 파싱 실패: {}", e.getMessage());
                }
            }

            logger.info("[QuestConfig] {} 개 퀘스트 로드 완료", definitions.size());
        } catch (Exception e) {
            logger.error("[QuestConfig] quests.json 로드 실패: {}", e.getMessage());
        }
    }

    /** 리로드 */
    public void reload() {
        load();
    }

    public Map<String, QuestDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    public QuestDefinition getDefinition(String questId) {
        return definitions.get(questId);
    }

    public Set<String> getQuestIds() {
        return definitions.keySet();
    }

    // ── CRUD ──

    /** 퀘스트 정의 추가/업데이트 */
    public void putDefinition(QuestDefinition def) {
        definitions.put(def.id, def);
    }

    /** 퀘스트 정의 삭제 */
    public boolean removeDefinition(String questId) {
        return definitions.remove(questId) != null;
    }

    /** quests.json에 현재 정의 저장 */
    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (QuestDefinition def : definitions.values()) {
            arr.add(definitionToJson(def));
        }
        root.add("quests", arr);
        try {
            Files.writeString(path, GSON.toJson(root));
            logger.info("[QuestConfig] quests.json 저장 완료 ({} 개)", definitions.size());
        } catch (IOException e) {
            logger.error("[QuestConfig] quests.json 저장 실패: {}", e.getMessage());
        }
    }

    private JsonObject definitionToJson(QuestDefinition def) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", def.id);
        obj.addProperty("title", def.title);
        obj.addProperty("description", def.description);
        obj.addProperty("category", def.category);
        if (def.chainId != null) obj.addProperty("chainId", def.chainId);
        obj.addProperty("chainOrder", def.chainOrder);
        obj.addProperty("repeatable", def.repeatable);
        obj.addProperty("dailyReset", def.dailyReset);
        obj.addProperty("autoAccept", def.autoAccept);
        obj.addProperty("sortOrder", def.sortOrder);
        if (def.nextQuest != null) obj.addProperty("nextQuest", def.nextQuest);

        JsonObject prereq = new JsonObject();
        JsonArray pqArr = new JsonArray();
        def.prerequisiteQuests.forEach(pqArr::add);
        prereq.add("quests", pqArr);
        prereq.addProperty("minLevel", def.minLevel);
        obj.add("prerequisites", prereq);

        JsonArray objArr = new JsonArray();
        for (QuestObjective o : def.objectives) {
            objArr.add(objectiveToJson(o));
        }
        obj.add("objectives", objArr);

        JsonArray rwdArr = new JsonArray();
        for (QuestReward r : def.rewards) {
            rwdArr.add(rewardToJson(r));
        }
        obj.add("rewards", rwdArr);

        return obj;
    }

    private JsonObject objectiveToJson(QuestObjective o) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", o.type);
        obj.addProperty("description", o.description);
        obj.addProperty("amount", o.amount);
        if (o.entityType != null) obj.addProperty("entityType", o.entityType);
        if (o.entityName != null) obj.addProperty("entityName", o.entityName);
        if (o.item != null) obj.addProperty("item", o.item);
        if (o.block != null) obj.addProperty("block", o.block);
        if (o.npcId != null) obj.addProperty("npcId", o.npcId);
        if (o.fromNpc != null) obj.addProperty("fromNpc", o.fromNpc);
        if (o.customId != null) obj.addProperty("customId", o.customId);
        if (o.dropChance != 1.0) obj.addProperty("dropChance", o.dropChance);
        if (o.x != 0 || o.y != 0 || o.z != 0) {
            obj.addProperty("x", o.x);
            obj.addProperty("y", o.y);
            obj.addProperty("z", o.z);
            obj.addProperty("radius", o.radius);
        }
        if (o.level > 0) obj.addProperty("level", o.level);
        return obj;
    }

    private JsonObject rewardToJson(QuestReward r) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", r.type);
        if (r.amount != 0) obj.addProperty("amount", r.amount);
        if (r.item != null) obj.addProperty("item", r.item);
        if (r.command != null) obj.addProperty("command", r.command);
        if (r.stat != null) obj.addProperty("stat", r.stat);
        if (r.value != 0) obj.addProperty("value", r.value);
        return obj;
    }

    // ── 파싱 ──

    private QuestDefinition parseDefinition(JsonObject obj) {
        QuestDefinition def = new QuestDefinition();
        def.id = getString(obj, "id", "");
        def.title = getString(obj, "title", "");
        def.description = getString(obj, "description", "");
        def.category = getString(obj, "category", QuestDefinition.CAT_SUB);
        def.chainId = getString(obj, "chainId", null);
        def.chainOrder = getInt(obj, "chainOrder", 0);
        def.repeatable = getBool(obj, "repeatable", false);
        def.dailyReset = getBool(obj, "dailyReset", false);
        def.autoAccept = getBool(obj, "autoAccept", false);
        def.sortOrder = getInt(obj, "sortOrder", 0);
        def.nextQuest = getString(obj, "nextQuest", null);

        // prerequisites
        if (obj.has("prerequisites")) {
            JsonObject prereq = obj.getAsJsonObject("prerequisites");
            if (prereq.has("quests")) {
                for (JsonElement e : prereq.getAsJsonArray("quests")) {
                    def.prerequisiteQuests.add(e.getAsString());
                }
            }
            def.minLevel = getInt(prereq, "minLevel", 0);
        }

        // objectives
        if (obj.has("objectives")) {
            for (JsonElement e : obj.getAsJsonArray("objectives")) {
                def.objectives.add(parseObjective(e.getAsJsonObject()));
            }
        }

        // rewards
        if (obj.has("rewards")) {
            for (JsonElement e : obj.getAsJsonArray("rewards")) {
                def.rewards.add(parseReward(e.getAsJsonObject()));
            }
        }

        return def;
    }

    private QuestObjective parseObjective(JsonObject obj) {
        QuestObjective o = new QuestObjective();
        o.type = getString(obj, "type", QuestObjective.CUSTOM);
        o.description = getString(obj, "description", "");
        o.entityType = getString(obj, "entityType", null);
        o.entityName = getString(obj, "entityName", null);
        o.item = getString(obj, "item", null);
        o.block = getString(obj, "block", null);
        o.npcId = getString(obj, "npcId", null);
        o.fromNpc = getString(obj, "fromNpc", null);
        o.customId = getString(obj, "customId", null);
        o.dropChance = getDouble(obj, "dropChance", 1.0);
        o.x = getDouble(obj, "x", 0);
        o.y = getDouble(obj, "y", 0);
        o.z = getDouble(obj, "z", 0);
        o.radius = getDouble(obj, "radius", 5);
        o.level = getInt(obj, "level", 0);
        o.amount = getInt(obj, "amount", 1);
        return o;
    }

    private QuestReward parseReward(JsonObject obj) {
        QuestReward r = new QuestReward();
        r.type = getString(obj, "type", QuestReward.GOLD);
        r.amount = getInt(obj, "amount", 0);
        r.item = getString(obj, "item", null);
        r.command = getString(obj, "command", null);
        r.stat = getString(obj, "stat", null);
        r.value = getInt(obj, "value", 0);
        return r;
    }

    // ── 유틸 ──

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    // ── 샘플 생성 ──

    private void createSample(Path path) {
        String sample = """
                {
                  "quests": [
                    {
                      "id": "mq_001_village_call",
                      "title": "마을의 부름",
                      "description": "마을 이장과 대화하세요.",
                      "category": "MAIN",
                      "chainId": "main_story_ch1",
                      "chainOrder": 1,
                      "repeatable": false,
                      "dailyReset": false,
                      "autoAccept": false,
                      "sortOrder": 0,
                      "prerequisites": { "quests": [], "minLevel": 1 },
                      "objectives": [
                        { "type": "TALK_NPC", "npcId": "village_chief", "amount": 1, "description": "마을 이장과 대화" }
                      ],
                      "rewards": [
                        { "type": "gold", "amount": 100 },
                        { "type": "exp", "amount": 50 }
                      ],
                      "nextQuest": "mq_002_first_trial"
                    },
                    {
                      "id": "mq_002_first_trial",
                      "title": "첫 번째 시련",
                      "description": "슬라임을 10마리 처치하세요.",
                      "category": "MAIN",
                      "chainId": "main_story_ch1",
                      "chainOrder": 2,
                      "repeatable": false,
                      "dailyReset": false,
                      "autoAccept": false,
                      "sortOrder": 1,
                      "prerequisites": { "quests": ["mq_001_village_call"], "minLevel": 1 },
                      "objectives": [
                        { "type": "KILL_MOB", "entityType": "minecraft:slime", "amount": 10, "description": "슬라임 처치" }
                      ],
                      "rewards": [
                        { "type": "gold", "amount": 500 },
                        { "type": "exp", "amount": 200 }
                      ],
                      "nextQuest": null
                    },
                    {
                      "id": "dq_slime_hunt",
                      "title": "[일일] 슬라임 사냥",
                      "description": "슬라임을 5마리 처치하세요.",
                      "category": "DAILY",
                      "chainId": null,
                      "chainOrder": 0,
                      "repeatable": false,
                      "dailyReset": true,
                      "autoAccept": false,
                      "sortOrder": 100,
                      "prerequisites": { "quests": [], "minLevel": 1 },
                      "objectives": [
                        { "type": "KILL_MOB", "entityType": "minecraft:slime", "amount": 5, "description": "슬라임 처치" }
                      ],
                      "rewards": [
                        { "type": "gold", "amount": 200 }
                      ],
                      "nextQuest": null
                    },
                    {
                      "id": "sq_delivery_letter",
                      "title": "이장의 편지",
                      "description": "이장의 편지를 대장장이에게 전달하세요.",
                      "category": "SUB",
                      "chainId": null,
                      "chainOrder": 0,
                      "repeatable": false,
                      "dailyReset": false,
                      "autoAccept": false,
                      "sortOrder": 50,
                      "prerequisites": { "quests": ["mq_001_village_call"], "minLevel": 1 },
                      "objectives": [
                        { "type": "DELIVER_ITEM", "fromNpc": "village_chief", "npcId": "blacksmith", "item": "minecraft:paper", "amount": 1, "description": "편지를 대장장이에게 전달" }
                      ],
                      "rewards": [
                        { "type": "gold", "amount": 200 },
                        { "type": "item", "item": "minecraft:iron_sword", "amount": 1 }
                      ],
                      "nextQuest": null
                    }
                  ]
                }
                """;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, sample);
            logger.info("[QuestConfig] 샘플 quests.json 생성: {}", path);
        } catch (IOException e) {
            logger.warn("[QuestConfig] 샘플 파일 생성 실패: {}", e.getMessage());
        }
    }
}
