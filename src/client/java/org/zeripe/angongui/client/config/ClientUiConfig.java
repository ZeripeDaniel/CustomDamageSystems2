package org.zeripe.angongui.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 클라이언트 UI 설정.
 */
public final class ClientUiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "customdamagesystem-client.json";

    public String _comment = "Client-only UI settings. Gameplay stats are server-authoritative on modded servers.";
    public String _usage = "Set UI toggles and key bindings. Restart game after editing.";

    public boolean enableStatScreen = true;
    public String statScreenKey = "J";

    private static volatile ClientUiConfig cached;

    private ClientUiConfig() {}

    public static ClientUiConfig get() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    private static ClientUiConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                ClientUiConfig cfg = GSON.fromJson(Files.readString(path), ClientUiConfig.class);
                if (cfg != null) {
                    cfg.ensureDocs();
                    Files.writeString(path, GSON.toJson(cfg));
                    return cfg;
                }
            } catch (Exception ignored) {
            }
        }

        ClientUiConfig defaults = new ClientUiConfig();
        defaults.ensureDocs();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(defaults));
        } catch (Exception ignored) {
        }
        return defaults;
    }

    private void ensureDocs() {
        if (_comment == null || _comment.isBlank()) {
            _comment = "Client-only UI settings. Gameplay stats are server-authoritative on modded servers.";
        }
        if (_usage == null || _usage.isBlank()) {
            _usage = "Set UI toggles and key bindings. Restart game after editing.";
        }
    }
}
