package org.zeripe.angongui.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.zeripe.angongui.client.config.ClientUiConfig;
import org.zeripe.angongui.client.config.KeyCodeResolver;
import org.lwjgl.glfw.GLFW;

/**
 * 스탯창 토글 입력 처리.
 */
public final class InputHandler {
    private static boolean wasStatKeyDown = false;

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
        });
    }
}
