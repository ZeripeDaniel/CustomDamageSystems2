package org.zeripe.angongui.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.Locale;

public final class CombatQuickslotRenderer {
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final int SLOT_COUNT = 10;
    private static final int LEFT_SLOT_COUNT = 5;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 2;
    private static final int BAR_PADDING = 4;
    private static final int BAR_H = SLOT_SIZE + BAR_PADDING * 2;
    private static final int BOTTOM = 11;
    private static final int CENTER_GAP = 36;

    private static final int CENTER_BOX_W = 28;
    private static final int CENTER_BOX_H = 34;
    private static final int CENTER_EXP_H = 4;
    private static final int CENTER_EXP_GAP = 4;
    private static final int CENTER_TEXT = 0xFFFFFFFF;
    private static final int CENTER_TEXT_SHADOW = 0x99000000;
    private static final float CENTER_TEXT_SCALE = 0.58f;
    private static final int HP_TOP = 0xFFE53935;
    private static final int HP_BOT = 0xFF8E0000;
    private static final int HP_LOW_TOP = 0xFFFF6F00;
    private static final int HP_LOW_BOT = 0xFF8E3A00;
    private static final int MP_TOP = 0xFF42A5F5;
    private static final int MP_BOT = 0xFF0D47A1;

    private static final String[] COMBAT_SLOT_LABELS = {"R", "1", "2", "3", "4", "LMB", "RMB", "F", "G", ""};

    private CombatQuickslotRenderer() {}

    public static void register() {
        HudRenderCallback.EVENT.register((g, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            render(g, mc);
        });
    }

    private static void render(GuiGraphics g, Minecraft mc) {
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int halfBarW = LEFT_SLOT_COUNT * SLOT_SIZE + (LEFT_SLOT_COUNT - 1) * SLOT_GAP + BAR_PADDING * 2;
        int totalW = halfBarW * 2 + CENTER_GAP;
        int x = (sw - totalW) / 2;
        int y = sh - BAR_H - BOTTOM;
        int leftBarX = x;
        int rightBarX = x + halfBarW + CENTER_GAP;
        int centerX = leftBarX + halfBarW + (CENTER_GAP - CENTER_BOX_W) / 2;
        int centerY = y + (BAR_H - CENTER_BOX_H) / 2;
        float t = CombatModeState.transitionProgress();

        // Life bar moves slightly backward as combat bar comes to front.
        int lifeOffsetY = Math.round(t * 6.0f);
        int combatOffsetY = Math.round((1.0f - t) * 6.0f);
        int lifeAlpha = (int) (255 * (1.0f - t * 0.45f));
        int combatAlpha = (int) (255 * (0.55f + t * 0.45f));
        boolean drawLifeItems = !CombatModeState.isCombatMode() || t < 0.5f;
        if (CombatModeState.isCombatMode()) {
            // Combat active: life bar in back, combat bar in front.
            g.pose().pushPose();
            g.pose().translate(0.0f, 0.0f, -20.0f);
            drawBarBackground(g, leftBarX - 2, y - 2 + lifeOffsetY, halfBarW, BAR_H, withAlpha(0x151515, lifeAlpha / 2), withAlpha(0x252525, lifeAlpha / 2));
            drawBarBackground(g, rightBarX - 2, y - 2 + lifeOffsetY, halfBarW, BAR_H, withAlpha(0x151515, lifeAlpha / 2), withAlpha(0x252525, lifeAlpha / 2));
            drawLifeSlots(g, mc, font, leftBarX + BAR_PADDING - 2, rightBarX + BAR_PADDING - 2, y + BAR_PADDING - 2 + lifeOffsetY, lifeAlpha, drawLifeItems);
            g.pose().popPose();

            g.pose().pushPose();
            g.pose().translate(0.0f, 0.0f, 20.0f);
            drawBarBackground(g, leftBarX, y + combatOffsetY, halfBarW, BAR_H, withAlpha(0x151515, combatAlpha), withAlpha(0x252525, combatAlpha));
            drawBarBackground(g, rightBarX, y + combatOffsetY, halfBarW, BAR_H, withAlpha(0x151515, combatAlpha), withAlpha(0x252525, combatAlpha));
            drawCombatSlots(g, font, leftBarX + BAR_PADDING, rightBarX + BAR_PADDING, y + BAR_PADDING + combatOffsetY, combatAlpha);
            drawCenterStatus(g, mc, font, centerX, centerY + combatOffsetY, combatAlpha);
            g.pose().popPose();
        } else {
            // Life active: combat bar in back, life bar in front.
            g.pose().pushPose();
            g.pose().translate(0.0f, 0.0f, -20.0f);
            drawBarBackground(g, leftBarX, y + combatOffsetY, halfBarW, BAR_H, withAlpha(0x151515, Math.max(35, combatAlpha / 3)), withAlpha(0x252525, Math.max(35, combatAlpha / 3)));
            drawBarBackground(g, rightBarX, y + combatOffsetY, halfBarW, BAR_H, withAlpha(0x151515, Math.max(35, combatAlpha / 3)), withAlpha(0x252525, Math.max(35, combatAlpha / 3)));
            drawCombatSlots(g, font, leftBarX + BAR_PADDING, rightBarX + BAR_PADDING, y + BAR_PADDING + combatOffsetY, Math.max(50, combatAlpha / 2));
            g.pose().popPose();

            g.pose().pushPose();
            g.pose().translate(0.0f, 0.0f, 20.0f);
            drawBarBackground(g, leftBarX - 2, y - 2 + lifeOffsetY, halfBarW, BAR_H, withAlpha(0x151515, lifeAlpha), withAlpha(0x252525, lifeAlpha));
            drawBarBackground(g, rightBarX - 2, y - 2 + lifeOffsetY, halfBarW, BAR_H, withAlpha(0x151515, lifeAlpha), withAlpha(0x252525, lifeAlpha));
            drawLifeSlots(g, mc, font, leftBarX + BAR_PADDING - 2, rightBarX + BAR_PADDING - 2, y + BAR_PADDING - 2 + lifeOffsetY, lifeAlpha, drawLifeItems);
            drawCenterStatus(g, mc, font, centerX, centerY + lifeOffsetY, lifeAlpha);
            g.pose().popPose();
        }

        String modeText = I18n.get(CombatModeState.isCombatMode()
                ? "ui.customdamagesystem.mode.combat_bracket"
                : "ui.customdamagesystem.mode.life_bracket");
        int tw = FontUtil.widthHeirof(font, modeText);
        FontUtil.drawHeirof(g, font, modeText, x + (totalW - tw) / 2, y - 12, 0xFFFFFFFF, true);
    }

    private static void drawBarBackground(GuiGraphics g, int x, int y, int w, int h, int topColorNoAlpha, int bottomColorNoAlpha) {
        g.fillGradient(x, y, x + w, y + h, topColorNoAlpha, bottomColorNoAlpha);
        g.fill(x, y, x + w, y + 1, withAlpha(0xFFFFFF, 0x99));
        g.fill(x, y + h - 1, x + w, y + h, withAlpha(0x000000, 0x66));
    }

    private static void drawCombatSlots(GuiGraphics g, Font font, int leftSx, int rightSx, int sy, int alpha) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int x = i < LEFT_SLOT_COUNT
                    ? leftSx + i * (SLOT_SIZE + SLOT_GAP)
                    : rightSx + (i - LEFT_SLOT_COUNT) * (SLOT_SIZE + SLOT_GAP);
            int y = sy;
            g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, withAlpha(0x000000, Math.max(40, alpha - 80)));
            g.fill(x, y, x + SLOT_SIZE, y + 1, withAlpha(0x3A3A3A, alpha));
            g.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, withAlpha(0x222222, alpha));
            g.fill(x, y, x + 1, y + SLOT_SIZE, withAlpha(0x3A3A3A, alpha));
            g.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, withAlpha(0x222222, alpha));

            String key = COMBAT_SLOT_LABELS[i];
            if (!key.isEmpty()) {
                int tw = font.width(key);
                g.drawString(font, key, x + (SLOT_SIZE - tw) / 2, y + SLOT_SIZE + 1, withAlpha(0xDDDDDD, alpha), false);
            }
        }
    }

    private static void drawLifeSlots(GuiGraphics g, Minecraft mc, Font font, int leftSx, int rightSx, int sy, int alpha, boolean drawItems) {
        int selected = mc.player.getInventory().selected;
        for (int i = 0; i < SLOT_COUNT; i++) {
            int x = i < LEFT_SLOT_COUNT
                    ? leftSx + i * (SLOT_SIZE + SLOT_GAP)
                    : rightSx + (i - LEFT_SLOT_COUNT) * (SLOT_SIZE + SLOT_GAP);
            int y = sy;
            g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, withAlpha(0x000000, Math.max(35, alpha - 120)));
            g.fill(x, y, x + SLOT_SIZE, y + 1, withAlpha(0x545454, alpha));
            g.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, withAlpha(0x262626, alpha));
            g.fill(x, y, x + 1, y + SLOT_SIZE, withAlpha(0x545454, alpha));
            g.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, withAlpha(0x262626, alpha));

            // Life mode selected slot indicator (slots 1..9 map to hotbar 0..8).
            if (i == selected + 1) {
                g.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y, withAlpha(0xFFE08A, alpha));
                g.fill(x - 1, y + SLOT_SIZE, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, withAlpha(0xFFE08A, alpha));
                g.fill(x - 1, y - 1, x, y + SLOT_SIZE + 1, withAlpha(0xFFE08A, alpha));
                g.fill(x + SLOT_SIZE, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, withAlpha(0xFFE08A, alpha));
            }

            ItemStack stack = (i == 0) ? mc.player.getOffhandItem() : mc.player.getInventory().items.get(i - 1);
            if (drawItems && !stack.isEmpty()) {
                g.renderItem(stack, x + 2, y + 2);
                g.renderItemDecorations(font, stack, x + 2, y + 2);
            }
        }
    }

    private static void drawCenterStatus(GuiGraphics g, Minecraft mc, Font font, int x, int y, int alpha) {
        ClientState.PlayerStats stats = ClientState.get().getPlayerStats();
        float hpRatio = stats.maxHp() > 0 ? Mth.clamp((float) stats.currentHp() / stats.maxHp(), 0f, 1f) : 0f;
        float mpRatio = stats.maxMp() > 0 ? Mth.clamp((float) stats.currentMp() / stats.maxMp(), 0f, 1f) : 0f;
        float expRatio = Mth.clamp(mc.player.experienceProgress, 0f, 1f);

        int hpTop = hpRatio < 0.25f ? HP_LOW_TOP : HP_TOP;
        int hpBot = hpRatio < 0.25f ? HP_LOW_BOT : HP_BOT;

        int halfW = CENTER_BOX_W / 2;
        int dividerX = x + halfW;

        g.fill(x - 1, y - 1, x + CENTER_BOX_W + 1, y + CENTER_BOX_H + 1, withAlpha(0x1A1A1A, alpha));
        g.fill(x, y, x + CENTER_BOX_W, y + CENTER_BOX_H, withAlpha(0x0D0D0D, Math.max(90, alpha - 60)));
        g.fill(dividerX - 1, y, dividerX, y + CENTER_BOX_H, withAlpha(0x2A2A2A, alpha));

        drawVerticalFill(g, x, y, halfW - 1, CENTER_BOX_H, hpRatio, hpTop, hpBot, alpha);
        drawVerticalFill(g, dividerX, y, CENTER_BOX_W - halfW, CENTER_BOX_H, mpRatio, MP_TOP, MP_BOT, alpha);

        drawTinyTextHeirof(g, font, "H", x + 2, y + 1, withAlpha(CENTER_TEXT, alpha), withAlpha(CENTER_TEXT_SHADOW, alpha));
        drawTinyTextHeirof(g, font, "M", dividerX + 2, y + 1, withAlpha(CENTER_TEXT, alpha), withAlpha(CENTER_TEXT_SHADOW, alpha));
        drawTinyTextHeirof(g, font, NUMBER_FORMAT.format(stats.currentHp()), x + 2, y + CENTER_BOX_H - 8, withAlpha(CENTER_TEXT, alpha), withAlpha(CENTER_TEXT_SHADOW, alpha));
        drawTinyTextHeirof(g, font, NUMBER_FORMAT.format(stats.currentMp()), dividerX + 2, y + CENTER_BOX_H - 8, withAlpha(CENTER_TEXT, alpha), withAlpha(CENTER_TEXT_SHADOW, alpha));

        int expY = y + CENTER_BOX_H + CENTER_EXP_GAP;
        g.fill(x - 1, expY - 1, x + CENTER_BOX_W + 1, expY + CENTER_EXP_H + 1, withAlpha(0x1A1A1A, alpha));
        g.fill(x, expY, x + CENTER_BOX_W, expY + CENTER_EXP_H, withAlpha(0x0C0C0C, Math.max(80, alpha - 80)));
        int fillW = (int) (CENTER_BOX_W * expRatio);
        if (fillW > 0) {
            g.fillGradient(x, expY, x + fillW, expY + CENTER_EXP_H, withAlpha(0x4CAF50, alpha), withAlpha(0x1B5E20, alpha));
        }
        String expLabel = "EXP " + mc.player.experienceLevel;
        drawTinyTextHeirof(g, font, expLabel, x + 1, expY - 7, withAlpha(CENTER_TEXT, alpha), withAlpha(CENTER_TEXT_SHADOW, alpha));
    }

    private static void drawVerticalFill(GuiGraphics g, int x, int y, int w, int h, float ratio, int top, int bottom, int alpha) {
        int fillH = Math.max(0, (int) (h * ratio));
        if (fillH <= 0) return;
        int fy = y + h - fillH;
        g.fillGradient(x, fy, x + w, y + h, withAlpha(top, alpha), withAlpha(bottom, alpha));
        g.fill(x, fy, x + w, fy + 1, withAlpha(0xFFFFFF, Math.max(35, alpha - 120)));
        g.fill(x, y + h - 1, x + w, y + h, withAlpha(0x000000, Math.max(45, alpha - 90)));
    }

    private static void drawTinyTextHeirof(GuiGraphics g, Font font, String text, int x, int y, int color, int shadowColor) {
        float inv = 1.0f / CENTER_TEXT_SCALE;
        g.pose().pushPose();
        g.pose().scale(CENTER_TEXT_SCALE, CENTER_TEXT_SCALE, 1.0f);
        FontUtil.drawHeirof(g, font, text, Math.round((x + 1) * inv), Math.round((y + 1) * inv), shadowColor, false);
        FontUtil.drawHeirof(g, font, text, Math.round(x * inv), Math.round(y * inv), color, false);
        g.pose().popPose();
    }

    private static int withAlpha(int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}
