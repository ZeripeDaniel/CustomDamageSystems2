package org.zeripe.angongserverside.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 스탯 기준/스케일링 규칙 전용 설정 파일.
 * - baseHp/baseMp: 기본 최대 체력/마나
 * - 기본 계수: 힘/민첩/지능/행운 계열의 기본 영향
 * - customRules: sourceStat -> targetAttribute 커스텀 규칙
 */
public final class StatFormulaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "customdamagesystem-stat.json";

    public String _comment = "Stat scaling rules. Server-side authoritative when running on a modded server.";
    public String _usage = "Edit base values and customRules. Example: strength 5 -> maxHp +10.";

    public int baseHp = 10;
    public int baseMp = 0;
    public boolean enableBaseStatScaling = true;

    public double strToAttack = 2.5;
    public double strToHp = 2.0; // 힘 5당 체력 10
    public double agiToMoveSpeed = 0.05;
    public double agiToCritRate = 0.02;
    public double intToMagicAttack = 3.0;
    public double intToMp = 2.0;
    public double intToCooldownReduction = 0.01;
    public double lukToCritRate = 0.03;
    public double lukToCritDamage = 0.1;
    public double lukToGoldBonus = 0.05;

    /**
     * sourceStat: strength/agility/intelligence/luck
     * targetAttribute: maxHp/maxMp/attack/magicAttack/defense/critRate/critDamage/armorPenetration/
     *                 bonusDamage/elementalMultiplier/lifeSteal/attackMultiplier/magicAttackMultiplier/
     *                 moveSpeed/buffDuration/cooldownReduction/clearGoldBonus
     * perPoints: 몇 포인트당
     * gain: target 증가량
     */
    public List<Rule> customRules = new ArrayList<>(List.of(
            new Rule("strength", "maxHp", 5.0, 10.0)
    ));

    public static StatFormulaConfig load(Logger logger) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                StatFormulaConfig loaded = GSON.fromJson(Files.readString(path), StatFormulaConfig.class);
                if (loaded != null) {
                    if (loaded.customRules == null) loaded.customRules = new ArrayList<>();
                    loaded.ensureDocs();
                    Files.writeString(path, GSON.toJson(loaded));
                    return loaded;
                }
            } catch (Exception e) {
                logger.warn("[StatFormulaConfig] 설정 파일 로드 실패: {}", e.getMessage());
            }
        }

        StatFormulaConfig defaults = new StatFormulaConfig();
        defaults.ensureDocs();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(defaults));
            logger.info("[StatFormulaConfig] 기본 설정 파일 생성: {}", path);
        } catch (IOException e) {
            logger.warn("[StatFormulaConfig] 설정 파일 생성 실패: {}", e.getMessage());
        }
        return defaults;
    }

    private void ensureDocs() {
        if (_comment == null || _comment.isBlank()) {
            _comment = "Stat scaling rules. Server-side authoritative when running on a modded server.";
        }
        if (_usage == null || _usage.isBlank()) {
            _usage = "Edit base values and customRules. Example: strength 5 -> maxHp +10.";
        }
    }

    public record Rule(String sourceStat, String targetAttribute, double perPoints, double gain) {}
}
