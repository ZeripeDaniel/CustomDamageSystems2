package org.zeripe.angongserverside.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AngongGui 모드의 서버측 설정.
 * CDS config 디렉터리에 angonggui-config.json으로 저장된다.
 * 웹 패널의 Additional 탭에서 수정 가능.
 */
public final class AngongGuiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "angonggui-config.json";

    // === 오버레이 설정 ===
    public boolean overlayEnabled = true;          // 상단 바 (시간/돈/메뉴) 표시
    public boolean menuBarEnabled = true;           // ALT 메뉴 표시
    public boolean moneyDisplayEnabled = true;      // 골드 표시
    public boolean partyWindowEnabled = true;       // 파티 창 활성화
    public boolean questWindowEnabled = true;       // 퀘스트 창 활성화
    public boolean customPauseScreenEnabled = true; // 커스텀 일시정지 화면

    private transient Path filePath;

    public static AngongGuiConfig load(Path configDir, Logger logger) {
        Path path = configDir.resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                AngongGuiConfig loaded = GSON.fromJson(Files.readString(path), AngongGuiConfig.class);
                if (loaded != null) {
                    loaded.filePath = path;
                    Files.writeString(path, GSON.toJson(loaded));
                    return loaded;
                }
            } catch (Exception e) {
                logger.warn("[AngongGuiConfig] 설정 파일 로드 실패: {}", e.getMessage());
            }
        }

        AngongGuiConfig defaults = new AngongGuiConfig();
        defaults.filePath = path;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(defaults));
            logger.info("[AngongGuiConfig] 기본 설정 파일 생성: {}", path);
        } catch (IOException e) {
            logger.warn("[AngongGuiConfig] 설정 파일 생성 실패: {}", e.getMessage());
        }
        return defaults;
    }

    public void save(Logger logger) {
        if (filePath == null) return;
        try {
            Files.writeString(filePath, GSON.toJson(this));
        } catch (IOException e) {
            logger.warn("[AngongGuiConfig] 설정 파일 저장 실패: {}", e.getMessage());
        }
    }

    /** API 응답용 JSON 변환 */
    public JsonObject toJson() {
        return JsonParser.parseString(GSON.toJson(this)).getAsJsonObject();
    }

    /** 웹 패널에서 받은 JSON으로 업데이트 */
    public void applyFromJson(JsonObject json) {
        if (json.has("overlayEnabled")) overlayEnabled = json.get("overlayEnabled").getAsBoolean();
        if (json.has("menuBarEnabled")) menuBarEnabled = json.get("menuBarEnabled").getAsBoolean();
        if (json.has("moneyDisplayEnabled")) moneyDisplayEnabled = json.get("moneyDisplayEnabled").getAsBoolean();
        if (json.has("partyWindowEnabled")) partyWindowEnabled = json.get("partyWindowEnabled").getAsBoolean();
        if (json.has("questWindowEnabled")) questWindowEnabled = json.get("questWindowEnabled").getAsBoolean();
        if (json.has("customPauseScreenEnabled")) customPauseScreenEnabled = json.get("customPauseScreenEnabled").getAsBoolean();
    }
}
