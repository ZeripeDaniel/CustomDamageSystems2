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

    private static final int BAR_W = 160;
    private static final int BAR_H = 10;
    private static final int BAR_GAP = 3;
    private static final int BOTTOM_OFFSET = 40;

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

        int baseX = (screenW - BAR_W) / 2;
        int mpY = screenH - BOTTOM_OFFSET;
        int hpY = mpY - BAR_H - BAR_GAP;

        float hpRatio = stats.maxHp() > 0 ? Mth.clamp((float) stats.currentHp() / stats.maxHp(), 0f, 1f) : 0f;
        boolean lowHp = hpRatio < 0.25f;
        drawStatusBar(g, baseX, hpY, BAR_W, BAR_H, hpRatio, lowHp ? HP_LOW_TOP : HP_TOP, lowHp ? HP_LOW_BOT : HP_BOT);

        String hpText = NUMBER_FORMAT.format(stats.currentHp()) + " / " + NUMBER_FORMAT.format(stats.maxHp());
        int hpTextW = FontUtil.width(font, hpText);
        int hpTextX = baseX + (BAR_W - hpTextW) / 2;
        int hpTextY = hpY + (BAR_H - 8) / 2;
        FontUtil.draw(g, font, hpText, hpTextX + 1, hpTextY + 1, TEXT_SHADOW, false);
        FontUtil.draw(g, font, hpText, hpTextX, hpTextY, TEXT_COLOR, false);

        float mpRatio = stats.maxMp() > 0 ? Mth.clamp((float) stats.currentMp() / stats.maxMp(), 0f, 1f) : 0f;
        drawStatusBar(g, baseX, mpY, BAR_W, BAR_H, mpRatio, MP_TOP, MP_BOT);

        String mpText = NUMBER_FORMAT.format(stats.currentMp()) + " / " + NUMBER_FORMAT.format(stats.maxMp());
        int mpTextW = FontUtil.width(font, mpText);
        int mpTextX = baseX + (BAR_W - mpTextW) / 2;
        int mpTextY = mpY + (BAR_H - 8) / 2;
        FontUtil.draw(g, font, mpText, mpTextX + 1, mpTextY + 1, TEXT_SHADOW, false);
        FontUtil.draw(g, font, mpText, mpTextX, mpTextY, TEXT_COLOR, false);
    }

    private static void drawStatusBar(GuiGraphics g, int x, int y, int w, int h, float ratio, int fillTop, int fillBot) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BAR_BORDER);
        g.fill(x, y, x + w, y + h, BAR_BG);

        int fillW = (int) (w * ratio);
        if (fillW <= 0) return;

        g.fillGradient(x, y, x + fillW, y + h, fillTop, fillBot);
        g.fill(x, y, x + fillW, y + 1, BAR_SHINE);
        g.fill(x, y + h - 1, x + fillW, y + h, BAR_SHADOW);
    }
}
