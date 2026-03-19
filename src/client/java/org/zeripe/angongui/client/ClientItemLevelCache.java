package org.zeripe.angongui.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientItemLevelCache {
    private static final Map<String, Double> LEVELS = new ConcurrentHashMap<>();

    private ClientItemLevelCache() {}

    public static void put(String itemId, double level) {
        LEVELS.put(itemId, level);
    }

    public static double get(String itemId) {
        return LEVELS.getOrDefault(itemId, 0.0);
    }

    public static boolean has(String itemId) {
        return LEVELS.containsKey(itemId);
    }

    public static void clear() {
        LEVELS.clear();
    }
}
