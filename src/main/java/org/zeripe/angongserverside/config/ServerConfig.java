package org.zeripe.angongserverside.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 서버/싱글 공용 설정 파일.
 * 없으면 기본값으로 생성된다.
 */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "customdamagesystem-server.json";

    public String _comment = "Server-side combat tuning. If this file exists on a modded server, server values are authoritative.";
    public String _usage = "Change numbers, save the file, then restart server/world.";

    // === 시스템 토글 ===
    public boolean statSystemEnabled = true;          // 스탯 계산 + API 노출
    public boolean damageSystemEnabled = true;        // 바닐라 데미지 가로채기 → 커스텀 공식 적용
    public boolean customHealthEnabled = true;        // 커스텀 HP 시스템 (damageSystem=true 일 때만 유효)
    public boolean customHudEnabled = true;           // 클라이언트 커스텀 HUD 표시
    public boolean questSystemEnabled = true;         // 퀘스트 시스템 활성화
    public int dailyResetHour = 9;                     // 일일 퀘스트 초기화 시각 (0~23)
    public String dailyResetTimezone = "Asia/Seoul";   // 초기화 기준 시간대
    public boolean partyFriendlyFire = false;          // 파티원 간 PvP 허용 여부

    public int defaultHitCooldownTicks = 7;
    public int nonWeaponHitCooldownTicks = 14;
    public int equipmentSyncIntervalTicks = 20;
    public boolean useVanillaAttackSpeedForHitCooldown = true;
    public int minAttackSpeedCooldownTicks = 1;
    public int maxAttackSpeedCooldownTicks = 40;
    public double defenseConstant = 500.0;
    public double environmentalDamageScale = 1.0;
    public boolean externalDamageUsesCustomDefense = false;
    public boolean includeVanillaArmorForPlayerDefense = true;
    public double vanillaArmorDefenseMultiplier = 10.0;

    public double damageNumberRangeXZ = 25.0;
    public double damageNumberRangeY = 10.0;

    public boolean economyEnabled = true;
    public String economyProvider = "internal"; // "internal", "scoreboard"
    public String scoreboardObjective = "gold";

    // === 웹 패널 설정 ===
    public boolean webPanelEnabled = false;
    public int webPanelPort = 8080;
    public String webPanelPassword = "changeme";
    public String restartBatPath = "";

    // === MySQL 설정 ===
    public boolean useMySQL = false;
    public String mysqlHost = "localhost";
    public int mysqlPort = 3306;
    public String mysqlDatabase = "customdamagesystem";
    public String mysqlUsername = "root";
    public String mysqlPassword = "";
    public int mysqlPoolSize = 10;
    public String mysqlTablePrefix = "cds_";

    public static ServerConfig load(Logger logger) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                ServerConfig loaded = GSON.fromJson(Files.readString(path), ServerConfig.class);
                if (loaded != null) {
                    loaded.ensureDocs();
                    Files.writeString(path, GSON.toJson(loaded));
                    return loaded;
                }
            } catch (Exception e) {
                logger.warn("[ServerConfig] 설정 파일 로드 실패: {}", e.getMessage());
            }
        }

        ServerConfig defaults = new ServerConfig();
        defaults.ensureDocs();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(defaults));
            logger.info("[ServerConfig] 기본 설정 파일 생성: {}", path);
        } catch (IOException e) {
            logger.warn("[ServerConfig] 설정 파일 생성 실패: {}", e.getMessage());
        }
        return defaults;
    }

    private void ensureDocs() {
        if (_comment == null || _comment.isBlank()) {
            _comment = "Server-side combat tuning. If this file exists on a modded server, server values are authoritative.";
        }
        if (_usage == null || _usage.isBlank()) {
            _usage = "Change numbers, save the file, then restart server/world.";
        }
    }
}
