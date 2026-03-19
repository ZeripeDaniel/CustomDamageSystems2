package org.zeripe.angongui.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.zeripe.angongui.client.network.NetworkHandler;
import org.zeripe.customdamagesystem.ModMenuTypes;

public class CustomDamageSystemClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModMenuTypes.ACCESSORY, AccessoryScreen::new);
        NetworkHandler.register();
        InputHandler.register();
        InventoryTabController.register();
        CombatQuickslotRenderer.register();
        DamageNumberRenderer.register();
        // ── 데미지 스킨 패키지 등록 ──
        // 각 패키지 = 4장의 스프라이트 시트 (physical, magical, critical, heal)
        // 위치: assets/customdamagesystem/textures/gui/damage_skins/{이름}/
        // 셀 크기: 16x24 (한 칸당), 가로 13칸 = 0123456789,!+
        DamageSkin.registerPack("default");
        // 추가 스킨 예시 (폴더 추가 후 주석 해제):
        // DamageSkin.registerPack("fire");
        // DamageSkin.registerPack("ice");
        // DamageSkin.registerPack("pixel");
        DamageSkin.enable(16, 24);
        // 인게임 Ctrl+K 로 스킨 전환
        ClientTickEvents.END_CLIENT_TICK.register(LocalStatManager::tick);
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (ClientItemLevelCache.has(itemId)) {
                double level = ClientItemLevelCache.get(itemId);
                String formatted = level == Math.floor(level)
                        ? String.valueOf((int) level)
                        : String.valueOf(level);
                lines.add(1, Component.translatable("ui.customdamagesystem.item.level", formatted)
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }
}
