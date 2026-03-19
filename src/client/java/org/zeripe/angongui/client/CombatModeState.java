package org.zeripe.angongui.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.zeripe.angongcommon.network.StatPayload;

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
            if (ClientPlayNetworking.canSend(StatPayload.TYPE)) {
                ClientPlayNetworking.send(StatPayload.of(
                        "{\"action\":\"combat_mode\",\"enabled\":" + isCombatMode() + "}"));
            }
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

    /** 서버 접속 해제 시 생활모드로 초기화 */
    public static void reset() {
        mode = Mode.LIFE;
        transition = 0.0f;
        lockedLifeHotbarSlot = 0;
    }
}
