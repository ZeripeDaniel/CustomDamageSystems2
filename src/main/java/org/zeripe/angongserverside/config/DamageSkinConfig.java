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
 * 데미지 스킨 팩 설정.
 * JSON 파일로 서버에서 스킨 팩을 등록/관리한다.
 *
 * 설정 파일: customdamagesystem-damage-skins.json
 */
public final class DamageSkinConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "customdamagesystem-damage-skins.json";

    public String _comment = "Damage skin pack configuration. Each pack requires 4 sprite sheet images (physical, magical, critical, heal).";
    public String _usage = "Add/remove skin packs below. Cell size = one digit's pixel size in the sprite sheet. Each sheet has 13 cells: 0123456789,!+";

    /** 기본 셀 가로 크기 (px) */
    public int cellWidth = 16;

    /** 기본 셀 세로 크기 (px) */
    public int cellHeight = 24;

    /** 등록된 스킨 팩 목록 */
    public List<SkinPackEntry> packs = new ArrayList<>();

    /**
     * 스킨 팩 하나의 설정.
     */
    public static class SkinPackEntry {
        /** 스킨 고유 ID (영문, 소문자 권장) */
        public String id;

        /** 표시 이름 */
        public String displayName;

        /**
         * 텍스처 경로의 네임스페이스 (리소스팩).
         * 기본: "customdamagesystem"
         */
        public String namespace = "customdamagesystem";

        /**
         * 텍스처 기본 경로 (네임스페이스 내).
         * 기본: "textures/gui/damage_skins/{id}/"
         * 이 경로 아래에 physical.png, magical.png, critical.png, heal.png 가 있어야 함.
         */
        public String texturePath;

        /** 이 스킨 전용 셀 크기 (0이면 글로벌 설정 사용) */
        public int cellWidth = 0;
        public int cellHeight = 0;

        /** 기본 제공 여부 (true면 모든 플레이어가 사용 가능) */
        public boolean freeForAll = false;
    }

    public static DamageSkinConfig load(Logger logger) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                DamageSkinConfig loaded = GSON.fromJson(Files.readString(path), DamageSkinConfig.class);
                if (loaded != null) {
                    loaded.ensureDefaults();
                    Files.writeString(path, GSON.toJson(loaded));
                    return loaded;
                }
            } catch (Exception e) {
                logger.warn("[DamageSkinConfig] 설정 파일 로드 실패: {}", e.getMessage());
            }
        }

        DamageSkinConfig defaults = new DamageSkinConfig();
        defaults.ensureDefaults();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(defaults));
            logger.info("[DamageSkinConfig] 기본 설정 파일 생성: {}", path);
        } catch (IOException e) {
            logger.warn("[DamageSkinConfig] 설정 파일 생성 실패: {}", e.getMessage());
        }
        return defaults;
    }

    private void ensureDefaults() {
        if (packs == null) packs = new ArrayList<>();
        if (packs.isEmpty()) {
            // 기본 스킨 팩 추가
            SkinPackEntry defaultPack = new SkinPackEntry();
            defaultPack.id = "default";
            defaultPack.displayName = "기본 스킨";
            defaultPack.namespace = "customdamagesystem";
            defaultPack.texturePath = "textures/gui/damage_skins/default/";
            defaultPack.freeForAll = true;
            packs.add(defaultPack);
        }
        // 텍스처 경로 자동 채우기
        for (SkinPackEntry pack : packs) {
            if (pack.texturePath == null || pack.texturePath.isBlank()) {
                pack.texturePath = "textures/gui/damage_skins/" + pack.id + "/";
            }
            if (pack.namespace == null || pack.namespace.isBlank()) {
                pack.namespace = "customdamagesystem";
            }
            if (pack.displayName == null || pack.displayName.isBlank()) {
                pack.displayName = pack.id;
            }
        }
    }
}
