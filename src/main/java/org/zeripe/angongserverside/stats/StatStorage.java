package org.zeripe.angongserverside.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 월드 단위 JSON 저장소.
 */
public class StatStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataDir;
    private final Logger logger;

    public StatStorage(MinecraftServer server, Logger logger) {
        this.logger = logger;
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        this.dataDir = worldRoot.resolve("customdamagesystem").resolve("statdata");
        initFileDir();
    }

    private void initFileDir() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            logger.error("[StatStorage] 디렉토리 생성 실패: {}", e.getMessage());
        }
    }

    public PlayerStatData load(String uuid, String name) {
        Path p = filePath(uuid);
        if (Files.exists(p)) {
            try {
                PlayerStatData d = GSON.fromJson(Files.readString(p), PlayerStatData.class);
                if (d != null) return d;
            } catch (Exception e) {
                logger.warn("[StatStorage] 로드 실패 {}: {}", uuid, e.getMessage());
            }
        }
        PlayerStatData fresh = PlayerStatData.defaultFor(uuid, name);
        save(fresh);
        return fresh;
    }

    public void save(PlayerStatData d) {
        try {
            Files.createDirectories(dataDir);
            Files.writeString(filePath(d.uuid), GSON.toJson(d));
        } catch (IOException e) {
            logger.warn("[StatStorage] 저장 실패 {}: {}", d.uuid, e.getMessage());
        }
    }

    public void close() {
        // no-op
    }

    private Path filePath(String uuid) {
        return dataDir.resolve(uuid + ".json");
    }
}
