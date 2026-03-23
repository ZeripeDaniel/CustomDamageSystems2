package org.zeripe.angongui.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.zeripe.angongui.client.config.ClientUiConfig;
import org.zeripe.angongui.client.config.KeyCodeResolver;
import org.zeripe.angongui.client.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;

/**
 * 스탯창 토글 입력 처리.
 */
public final class InputHandler {
    private static boolean wasStatKeyDown = false;
    private static boolean wasModeToggleDown = false;
    private static boolean wasSkinKeyDown = false;

    private InputHandler() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            ClientUiConfig cfg = ClientUiConfig.get();
            if (!cfg.enableStatScreen) {
                if (client.screen instanceof StatScreen) {
                    Minecraft.getInstance().setScreen(null);
                }
                wasStatKeyDown = false;
                return;
            }

            boolean isStatScreen = client.screen instanceof StatScreen;
            if (client.screen != null && !isStatScreen) {
                wasStatKeyDown = false;
                return;
            }

            long window = client.getWindow().getWindow();
            boolean ctrlDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);

            // 전투/생활 모드 전환: damageSystem이 비활성화면 전투모드 차단
            boolean graveDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_GRAVE_ACCENT);
            boolean modeToggle = ctrlDown && graveDown;
            if (modeToggle && !wasModeToggleDown && ClientState.get().isDamageSystemEnabled()) {
                CombatModeState.toggle();
            }
            // damageSystem 비활성화 시 전투모드에 있었다면 생활모드로 강제 복귀
            if (!ClientState.get().isDamageSystemEnabled() && CombatModeState.isCombatMode()) {
                CombatModeState.reset();
            }
            wasModeToggleDown = modeToggle;

            int statKeyCode = KeyCodeResolver.resolveOrDefault(cfg.statScreenKey, GLFW.GLFW_KEY_J);
            boolean keyDown = InputConstants.isKeyDown(window, statKeyCode);

            if (keyDown && !wasStatKeyDown) {
                if (isStatScreen) {
                    Minecraft.getInstance().setScreen(null);
                } else {
                    Minecraft.getInstance().setScreen(new StatScreen());
                }
            }
            wasStatKeyDown = keyDown;

            // Ctrl + K = 데미지 스킨 전환
            boolean skinToggle = ctrlDown && InputConstants.isKeyDown(window, GLFW.GLFW_KEY_K);
            if (skinToggle && !wasSkinKeyDown) {
                if (DamageSkin.isServerMode()) {
                    // 서버 모드: 서버에 스킨 순환 요청
                    NetworkHandler.requestSkinCycle();
                } else if (DamageSkin.isEnabled()) {
                    // 로컬 모드 (폴백)
                    DamageSkin.nextPack();
                    DamageSkin.showSwitchMessage();
                }
            }
            wasSkinKeyDown = skinToggle;

            CombatModeState.tick(client);
            CombatSkillInputRouter.tick(client);
        });
    }
}
