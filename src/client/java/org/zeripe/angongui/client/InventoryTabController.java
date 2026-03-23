package org.zeripe.angongui.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.zeripe.angongcommon.network.StatPayload;

public final class InventoryTabController {
    private static final int INV_W = 176;
    private static final int INV_H = 166;
    private static final int TAB_SZ = 22;
    private static final int TAB_GAP = 2;

    private InventoryTabController() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen == null || screen.getClass() != InventoryScreen.class) return;
            // 스탯 시스템 비활성화 시 악세서리 탭 숨김
            if (!ClientState.get().isStatSystemEnabled()) return;

            int left = (screen.width - INV_W) / 2;
            int top = (screen.height - INV_H) / 2;
            int tx = left - TAB_SZ - 1;

            Screens.getButtons(screen).add(new IconTabButton(
                    tx, top + 4, TAB_SZ,
                    new ItemStack(Items.CHEST),
                    Component.translatable("ui.customdamagesystem.tab.inventory"),
                    true,
                    () -> {}
            ));

            Screens.getButtons(screen).add(new IconTabButton(
                    tx, top + 4 + TAB_SZ + TAB_GAP, TAB_SZ,
                    new ItemStack(Items.TOTEM_OF_UNDYING),
                    Component.translatable("ui.customdamagesystem.tab.accessory"),
                    false,
                    () -> ClientPlayNetworking.send(StatPayload.of("{\"action\":\"open_accessory\"}"))
            ));
        });
    }
}
