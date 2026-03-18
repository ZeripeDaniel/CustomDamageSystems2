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

    public int defaultHitCooldownTicks = 7;
    public int equipmentSyncIntervalTicks = 20;
    public boolean useVanillaAttackSpeedForHitCooldown = true;
    public int minAttackSpeedCooldownTicks = 1;
    public int maxAttackSpeedCooldownTicks = 40;
    public double defenseConstant = 500.0;
    public double environmentalDamageScale = 1.0;
    public boolean externalDamageUsesCustomDefense = false;
    public boolean includeVanillaArmorForPlayerDefense = true;
    public double vanillaArmorDefenseMultiplier = 10.0;

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
