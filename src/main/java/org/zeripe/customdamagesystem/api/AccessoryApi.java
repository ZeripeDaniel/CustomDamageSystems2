package org.zeripe.customdamagesystem.api;

import net.minecraft.world.item.Item;
import org.zeripe.customdamagesystem.item.AccessoryDefinition;
import org.zeripe.customdamagesystem.item.AccessoryRegistry;
import org.zeripe.customdamagesystem.item.AccessoryType;

import java.util.Optional;

/**
 * API for external mods to mark custom items as accessories and define core stat bonuses.
 */
public final class AccessoryApi {
    private AccessoryApi() {}

    public static void registerAccessory(Item item,
                                         AccessoryType type,
                                         int strength,
                                         int agility,
                                         int intelligence,
                                         int luck) {
        AccessoryRegistry.register(item, new AccessoryDefinition(type, strength, agility, intelligence, luck));
    }

    public static Optional<AccessoryDefinition> getDefinition(Item item) {
        return AccessoryRegistry.find(item);
    }
}
