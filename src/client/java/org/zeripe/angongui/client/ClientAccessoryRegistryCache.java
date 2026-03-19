package org.zeripe.angongui.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 플러그인 서버에서 받은 악세서리 레지스트리 캐시.
 * accessory_registry_sync 패킷에서 수신한 registryId를 itemId+cmd 기준으로 캐싱.
 */
public final class ClientAccessoryRegistryCache {
    /** key: "itemId:cmd" → value: registryId */
    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    private ClientAccessoryRegistryCache() {}

    public static void put(String itemId, int cmd, String registryId) {
        cache.put(makeKey(itemId, cmd), registryId);
    }

    public static String findRegistryId(String itemId, int cmd) {
        // 정확한 매칭 시도
        String exact = cache.get(makeKey(itemId, cmd));
        if (exact != null) return exact;
        // CMD 0으로 fallback
        if (cmd != 0) return cache.get(makeKey(itemId, 0));
        return null;
    }

    public static void clear() {
        cache.clear();
    }

    private static String makeKey(String itemId, int cmd) {
        return itemId + ":" + cmd;
    }
}
