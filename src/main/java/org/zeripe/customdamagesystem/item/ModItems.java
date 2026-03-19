package org.zeripe.customdamagesystem.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.zeripe.customdamagesystem.Customdamagesystem;

public final class ModItems {
    public static final AccessoryItem WOODEN_RING = registerAccessory(
            "wooden_ring", AccessoryType.RING, 2, 0, 0, 1);
    public static final AccessoryItem WOODEN_NECKLACE = registerAccessory(
            "wooden_necklace", AccessoryType.NECKLACE, 0, 1, 2, 0);
    public static final AccessoryItem WOODEN_EARRING = registerAccessory(
            "wooden_earring", AccessoryType.EARRING, 0, 2, 0, 1);

    public static final CreativeModeTab ITEMS_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            id("items"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(WOODEN_RING))
                    .title(Component.translatable("itemGroup.customdamagesystem.items"))
                    .displayItems((parameters, output) -> {
                        output.accept(WOODEN_RING);
                        output.accept(WOODEN_NECKLACE);
                        output.accept(WOODEN_EARRING);
                    })
                    .build()
    );

    private ModItems() {}

    public static void registerAll() {
        AccessoryRegistry.register(WOODEN_RING, WOODEN_RING.toDefinition());
        AccessoryRegistry.register(WOODEN_NECKLACE, WOODEN_NECKLACE.toDefinition());
        AccessoryRegistry.register(WOODEN_EARRING, WOODEN_EARRING.toDefinition());
    }

    private static AccessoryItem registerAccessory(String path, AccessoryType type, int str, int agi, int intel, int luk) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
        Item.Properties props = new Item.Properties().setId(key).stacksTo(1);
        return Registry.register(BuiltInRegistries.ITEM, id(path), new AccessoryItem(props, type, str, agi, intel, luk));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Customdamagesystem.MOD_ID, path);
    }
}
