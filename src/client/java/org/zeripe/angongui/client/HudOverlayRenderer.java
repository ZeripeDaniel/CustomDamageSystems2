package org.zeripe.angongui.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.text.NumberFormat;
import java.util.Locale;

public final class HudOverlayRenderer {
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private static final int BAR_BG = 0xCC0D0D0D;
    private static final int BAR_BORDER = 0xFF1A1A1A;
    private static final int HP_TOP = 0xFFE53935;
    private static final int HP_BOT = 0xFF8E0000;
    private static final int HP_LOW_TOP = 0xFFFF6F00;
    private static final int HP_LOW_BOT = 0xFF8E3A00;
    private static final int MP_TOP = 0xFF42A5F5;
    private static final int MP_BOT = 0xFF0D47A1;
    private static final int BAR_SHINE = 0x33FFFFFF;
    private static final int BAR_SHADOW = 0x44000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SHADOW = 0x88000000;

    private static final int BOX_W = 30;
    private static final int BOX_H = 40;
    private static final int BOTTOM_OFFSET = 14;
    private static final float TINY_TEXT_SCALE = 0.62f;

    private HudOverlayRenderer() {}

    public static void register() {
        HudRenderCallback.EVENT.register((g, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            renderHpMpBars(g, mc.font, mc);
        });
    }

    private static void renderHpMpBars(GuiGraphics g, Font font, Minecraft mc) {
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        ClientState.PlayerStats stats = ClientState.get().getPlayerStats();

        int baseX = (screenW - BOX_W) / 2;
        int baseY = screenH - BOX_H - BOTTOM_OFFSET;

        float hpRatio = stats.maxHp() > 0 ? Mth.clamp((float) stats.currentHp() / stats.maxHp(), 0f, 1f) : 0f;
        float mpRatio = stats.maxMp() > 0 ? Mth.clamp((float) stats.currentMp() / stats.maxMp(), 0f, 1f) : 0f;

        drawSplitVerticalBox(g, font, baseX, baseY, hpRatio, mpRatio, stats.currentHp(), stats.currentMp());
    }

    private static void drawSplitVerticalBox(GuiGraphics g, Font font, int x, int y, float hpRatio, float mpRatio, int hp, int mp) {
        int halfW = BOX_W / 2;
        int leftX = x;
        int rightX = x + halfW;

        g.fill(x - 1, y - 1, x + BOX_W + 1, y + BOX_H + 1, BAR_BORDER);
        g.fill(x, y, x + BOX_W, y + BOX_H, BAR_BG);
        g.fill(rightX - 1, y, rightX, y + BOX_H, BAR_BORDER);

        drawVerticalFill(g, leftX, y, halfW - 1, BOX_H, hpRatio, hpRatio < 0.25f ? HP_LOW_TOP : HP_TOP, hpRatio < 0.25f ? HP_LOW_BOT : HP_BOT);
        drawVerticalFill(g, rightX, y, BOX_W - halfW, BOX_H, mpRatio, MP_TOP, MP_BOT);

        drawTinyText(g, font, "H", leftX + 2, y + 1, TEXT_COLOR);
        drawTinyText(g, font, "M", rightX + 2, y + 1, TEXT_COLOR);

        drawTinyText(g, font, NUMBER_FORMAT.format(hp), leftX + 2, y + BOX_H - 8, TEXT_COLOR);
        drawTinyText(g, font, NUMBER_FORMAT.format(mp), rightX + 2, y + BOX_H - 8, TEXT_COLOR);
    }

    private static void drawVerticalFill(GuiGraphics g, int x, int y, int w, int h, float ratio, int fillTop, int fillBot) {
        int fillH = Math.max(0, (int) (h * ratio));
        if (fillH <= 0) return;
        int fy = y + h - fillH;
        g.fillGradient(x, fy, x + w, y + h, fillTop, fillBot);
        g.fill(x, fy, x + w, fy + 1, BAR_SHINE);
        g.fill(x, y + h - 1, x + w, y + h, BAR_SHADOW);
    }

    private static void drawTinyText(GuiGraphics g, Font font, String text, int x, int y, int color) {
        float inv = 1.0f / TINY_TEXT_SCALE;
        g.pose().pushPose();
        g.pose().scale(TINY_TEXT_SCALE, TINY_TEXT_SCALE, 1.0f);
        FontUtil.draw(g, font, text, Math.round((x + 1) * inv), Math.round((y + 1) * inv), TEXT_SHADOW, false);
        FontUtil.draw(g, font, text, Math.round(x * inv), Math.round(y * inv), color, false);
        g.pose().popPose();
    }
}
