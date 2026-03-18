package org.zeripe.angongserverside.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 몬스터 다중 공격(속칭 다구리) 허용 대상을 JSON으로 정의한다.
 * - "*" 포함 시 모든 엔티티가 개별 공격자로 처리된다.
 */
public final class MonsterAttackGroupConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "customdamagesystem-monster-groups.json";

    public String _comment = "Controls which attacker entity types use independent cooldown keys (gank behavior).";
    public String _usage = "Use \"*\" for all entities, or list specific entity ids like \"minecraft:zombie\".";
    public List<String> independentAttackers = new ArrayList<>(List.of("*"));

    public static MonsterAttackGroupConfig load(Path configDir, Logger logger) {
        Path path = configDir.resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                MonsterAttackGroupConfig loaded = GSON.fromJson(Files.readString(path), MonsterAttackGroupConfig.class);
                if (loaded != null && loaded.independentAttackers != null) {
                    loaded.ensureDocs();
                    Files.writeString(path, GSON.toJson(loaded));
                    return loaded;
                }
            } catch (Exception e) {
                logger.warn("[MonsterAttackGroupConfig] 설정 파일 로드 실패: {}", e.getMessage());
            }
        }

        MonsterAttackGroupConfig defaults = new MonsterAttackGroupConfig();
        defaults.ensureDocs();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(defaults));
            logger.info("[MonsterAttackGroupConfig] 기본 설정 파일 생성: {}", path);
        } catch (IOException e) {
            logger.warn("[MonsterAttackGroupConfig] 설정 파일 생성 실패: {}", e.getMessage());
        }
        return defaults;
    }

    public boolean isIndependentAttacker(Entity attacker) {
        if (attacker == null || independentAttackers == null || independentAttackers.isEmpty()) return false;
        if (independentAttackers.contains("*")) return true;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
        return independentAttackers.contains(id);
    }

    private void ensureDocs() {
        if (_comment == null || _comment.isBlank()) {
            _comment = "Controls which attacker entity types use independent cooldown keys (gank behavior).";
        }
        if (_usage == null || _usage.isBlank()) {
            _usage = "Use \"*\" for all entities, or list specific entity ids like \"minecraft:zombie\".";
        }
    }
}
