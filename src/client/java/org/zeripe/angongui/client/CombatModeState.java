package org.zeripe.angongui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public final class CombatModeState {
    public enum Mode {
        LIFE,
        COMBAT
    }

    private static volatile Mode mode = Mode.LIFE;
    private static float transition = 0.0f; // 0.0 = life front, 1.0 = combat front
    private static int lockedLifeHotbarSlot = 0;

    private CombatModeState() {}

    public static Mode getMode() {
        return mode;
    }

    public static boolean isCombatMode() {
        return mode == Mode.COMBAT;
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mode == Mode.LIFE) {
            lockedLifeHotbarSlot = mc.player.getInventory().selected;
        }
        mode = (mode == Mode.LIFE) ? Mode.COMBAT : Mode.LIFE;
        if (mc.player != null) {
            String key = mode == Mode.COMBAT
                    ? "ui.customdamagesystem.mode.combat"
                    : "ui.customdamagesystem.mode.life";
            mc.player.displayClientMessage(Component.literal(I18n.get(key)), true);
        }
    }

    public static void tick(Minecraft client) {
        float target = (mode == Mode.COMBAT) ? 1.0f : 0.0f;
        if (transition < target) {
            transition = Math.min(target, transition + 0.12f);
        } else if (transition > target) {
            transition = Math.max(target, transition - 0.12f);
        }

        // In combat mode, keep life hotbar items unchanged and not selectable.
        if (client.player != null && mode == Mode.COMBAT) {
            client.player.getInventory().selected = lockedLifeHotbarSlot;
        }
    }

    public static float transitionProgress() {
        return transition;
    }
}
