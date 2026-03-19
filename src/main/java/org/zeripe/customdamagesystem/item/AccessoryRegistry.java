package org.zeripe.customdamagesystem.item;

import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AccessoryRegistry {
    private static final Map<Item, AccessoryDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private AccessoryRegistry() {}

    public static void register(Item item, AccessoryDefinition definition) {
        if (item == null || definition == null) return;
        DEFINITIONS.put(item, definition);
    }

    public static Optional<AccessoryDefinition> find(Item item) {
        if (item == null) return Optional.empty();
        return Optional.ofNullable(DEFINITIONS.get(item));
    }

    public static boolean isAccessory(Item item) {
        return find(item).isPresent();
    }
}
