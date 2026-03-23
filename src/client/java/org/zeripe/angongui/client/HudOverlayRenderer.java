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
    // 흡수 HP 색상 (노란색 계열)
    private static final int ABS_TOP = 0xFFFFD54F;
    private static final int ABS_BOT = 0xFFF9A825;
    private static final int BAR_SHINE = 0x33FFFFFF;
    private static final int BAR_SHADOW = 0x44000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SHADOW = 0x88000000;
    private static final int ABS_TEXT_COLOR = 0xFFFFD54F;

    private static final int BOX_W = 30;
    private static final int BOX_H = 40;
    private static final int BOTTOM_OFFSET = 14;
    /** 기존 0.62 → 절반 수준으로 축소 (maxHp + absorptionHp 표시 공간 확보) */
    private static final float TINY_TEXT_SCALE = 0.45f;

    private HudOverlayRenderer() {}

    public static void register() {
        HudRenderCallback.EVENT.register((g, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 서버가 커스텀 HUD 또는 커스텀 체력을 비활성화했으면 렌더링하지 않음 → 바닐라 하트가 보임
            if (!ClientState.get().isCustomHudEnabled()) return;
            if (!ClientState.get().isCustomHealthEnabled()) return;

            renderHpMpBars(g, mc.font, mc);
        });
    }

    private static void renderHpMpBars(GuiGraphics g, Font font, Minecraft mc) {
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        ClientState.PlayerStats stats = ClientState.get().getPlayerStats();

        int baseX = (screenW - BOX_W) / 2;
        int baseY = screenH - BOX_H - BOTTOM_OFFSET;

        // HP 비율: 실제 HP는 maxHp 기준, 흡수는 별도 계산
        int effectiveMax = stats.maxHp() + stats.absorptionHp();
        float hpRatio = effectiveMax > 0 ? Mth.clamp((float) stats.currentHp() / effectiveMax, 0f, 1f) : 0f;
        float absRatio = effectiveMax > 0 ? Mth.clamp((float) stats.absorptionHp() / effectiveMax, 0f, 1f) : 0f;
        float mpRatio = stats.maxMp() > 0 ? Mth.clamp((float) stats.currentMp() / stats.maxMp(), 0f, 1f) : 0f;

        drawSplitVerticalBox(g, font, baseX, baseY, hpRatio, absRatio, mpRatio, stats);
    }

    private static void drawSplitVerticalBox(GuiGraphics g, Font font, int x, int y,
                                              float hpRatio, float absRatio, float mpRatio,
                                              ClientState.PlayerStats stats) {
        int halfW = BOX_W / 2;
        int rightX = x + halfW;
        int hpW = halfW - 1;

        // 배경 + 테두리
        g.fill(x - 1, y - 1, x + BOX_W + 1, y + BOX_H + 1, BAR_BORDER);
        g.fill(x, y, x + BOX_W, y + BOX_H, BAR_BG);
        g.fill(rightX - 1, y, rightX, y + BOX_H, BAR_BORDER);

        // HP 바 (빨강) — 바닥부터 (hpRatio + absRatio)만큼 빨강으로 깔고
        boolean lowHp = stats.maxHp() > 0 && (float) stats.currentHp() / stats.maxHp() < 0.25f;
        float totalHpFill = Mth.clamp(hpRatio + absRatio, 0f, 1f);
        drawVerticalFill(g, x, y, hpW, BOX_H, totalHpFill,
                lowHp ? HP_LOW_TOP : HP_TOP, lowHp ? HP_LOW_BOT : HP_BOT);

        // 흡수 바 (노란색) — 빨간 바 상단에 겹침
        if (absRatio > 0) {
            int totalFillH = Math.max(0, (int) (BOX_H * totalHpFill));
            int absFillH = Math.max(1, (int) (BOX_H * absRatio));
            if (totalFillH > 0) {
                int fillTop = y + BOX_H - totalFillH;
                int absBot = Math.min(fillTop + absFillH, y + BOX_H);
                g.fillGradient(x, fillTop, x + hpW, absBot, ABS_TOP, ABS_BOT);
                g.fill(x, fillTop, x + hpW, fillTop + 1, BAR_SHINE);
            }
        }

        // MP 바 (파랑)
        drawVerticalFill(g, rightX, y, BOX_W - halfW, BOX_H, mpRatio, MP_TOP, MP_BOT);

        // ── 텍스트 (크기 축소) ──

        // 라벨 H / M
        drawTinyText(g, font, "H", x + 1, y + 1, TEXT_COLOR);
        drawTinyText(g, font, "M", rightX + 1, y + 1, TEXT_COLOR);

        // HP 텍스트: 현재HP / maxHP  (흡수 있으면 +abs)
        int textBaseY = y + BOX_H - 14;

        // 흡수 HP (노란색, 맨 위)
        if (stats.absorptionHp() > 0) {
            drawTinyText(g, font, "+" + stats.absorptionHp(), x + 1, textBaseY, ABS_TEXT_COLOR);
            textBaseY += 5;
        }

        // 현재 HP
        drawTinyText(g, font, NUMBER_FORMAT.format(stats.currentHp()), x + 1, textBaseY, TEXT_COLOR);
        textBaseY += 5;
        // /maxHP
        drawTinyText(g, font, "/" + NUMBER_FORMAT.format(stats.maxHp()), x + 1, textBaseY, TEXT_COLOR);

        // MP 텍스트: 현재MP / maxMP
        int mpTextY = y + BOX_H - 9;
        drawTinyText(g, font, NUMBER_FORMAT.format(stats.currentMp()), rightX + 1, mpTextY, TEXT_COLOR);
        drawTinyText(g, font, "/" + NUMBER_FORMAT.format(stats.maxMp()), rightX + 1, mpTextY + 5, TEXT_COLOR);
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
