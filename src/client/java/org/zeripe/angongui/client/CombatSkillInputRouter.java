package org.zeripe.angongui.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class CombatSkillInputRouter {
    private static final boolean[] keyState = new boolean[512];
    private static boolean leftMouseDown = false;
    private static boolean rightMouseDown = false;

    private CombatSkillInputRouter() {}

    public static void tick(Minecraft client) {
        if (client.player == null) return;
        if (!CombatModeState.isCombatMode()) {
            reset();
            return;
        }

        long window = client.getWindow().getWindow();
        handleKey(window, GLFW.GLFW_KEY_R, "R");
        handleKey(window, GLFW.GLFW_KEY_1, "1");
        handleKey(window, GLFW.GLFW_KEY_2, "2");
        handleKey(window, GLFW.GLFW_KEY_3, "3");
        handleKey(window, GLFW.GLFW_KEY_4, "4");
        handleKey(window, GLFW.GLFW_KEY_F, "F");
        handleKey(window, GLFW.GLFW_KEY_G, "G");

        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (leftDown && !leftMouseDown) trigger(client, "LMB");
        leftMouseDown = leftDown;

        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (rightDown && !rightMouseDown) trigger(client, "RMB");
        rightMouseDown = rightDown;
    }

    private static void handleKey(long window, int glfwKey, String slotKey) {
        boolean down = InputConstants.isKeyDown(window, glfwKey);
        if (down && !keyState[glfwKey]) {
            Minecraft mc = Minecraft.getInstance();
            trigger(mc, slotKey);
        }
        keyState[glfwKey] = down;
    }

    private static void trigger(Minecraft client, String slotKey) {
        if (client.player == null) return;
        // Placeholder structure: actual skill execution/binding will be connected later.
        client.player.displayClientMessage(
                Component.literal(I18n.get("ui.customdamagesystem.mode.skill_used", slotKey)),
                true
        );
    }

    private static void reset() {
        for (int i = 0; i < keyState.length; i++) keyState[i] = false;
        leftMouseDown = false;
        rightMouseDown = false;
    }
}
