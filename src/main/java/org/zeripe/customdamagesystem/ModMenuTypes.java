package org.zeripe.customdamagesystem;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.zeripe.customdamagesystem.item.AccessoryMenu;

public final class ModMenuTypes {
    public static final MenuType<AccessoryMenu> ACCESSORY = Registry.register(
            BuiltInRegistries.MENU,
            ResourceLocation.fromNamespaceAndPath(Customdamagesystem.MOD_ID, "accessory"),
            new MenuType<>(AccessoryMenu::new, FeatureFlags.VANILLA_SET)
    );

    private ModMenuTypes() {}

    public static void register() {
        // forces static init
    }
}
