package org.zeripe.angongserverside.storage;

import org.zeripe.angongserverside.stats.PlayerStatData;

import java.util.List;
import java.util.UUID;

public interface PlayerDataStorage {

    // === Stats ===
    PlayerStatData loadStats(String uuid, String name);
    void saveStats(PlayerStatData data);
    List<PlayerStatData> loadAllStats();

    // === Accessories (raw JSON) ===
    String loadAccessoryJson(UUID uuid);
    void saveAccessoryJson(UUID uuid, String json);

    // === Quest Data (raw JSON) ===
    String loadQuestDataJson(UUID uuid);
    void saveQuestDataJson(UUID uuid, String json);

    // === Damage Skins ===
    List<String> loadOwnedSkins(UUID uuid);
    String loadSelectedSkin(UUID uuid);
    void saveSkinData(UUID uuid, List<String> ownedSkins, String selectedSkin);

    // === Lifecycle ===
    void init();
    void shutdown();
}
