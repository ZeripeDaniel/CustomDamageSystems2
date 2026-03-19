package org.zeripe.customdamagesystem;

import net.fabricmc.api.ModInitializer;
import org.zeripe.customdamagesystem.item.ModItems;

public class Customdamagesystem implements ModInitializer {
    public static final String MOD_ID = "customdamagesystem";

    @Override
    public void onInitialize() {
        ModMenuTypes.register();
        ModItems.registerAll();
    }
}
