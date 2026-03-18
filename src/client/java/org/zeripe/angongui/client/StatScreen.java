package org.zeripe.angongui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class StatScreen extends Screen {
    private static final NumberFormat NUM_FMT = NumberFormat.getNumberInstance(Locale.US);
    private static final DecimalFormat ITEM_LEVEL_FMT =
            new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US));

    private static final int BASE_H = 296;
    private static final float SCALE = 0.7f;

    private static final int C_PANEL_TOP = 0xF2121920;
    private static final int C_PANEL_BOT = 0xF01F2636;
    private static final int C_TITLE_BG = 0xFF0D1219;
    private static final int C_EDGE = 0xFF2A4A6B;
    private static final int C_EDGE_GLOW = 0x222A4A6B;
    private static final int C_SECTION_LINE = 0xFF1A2E40;
    private static final int C_SECTION_LINE2 = 0x08FFFFFF;
    private static final int C_LABEL = 0xFF8BAEC8;
    private static final int C_VALUE = 0xFFFFFFFF;
    private static final int C_VALUE_BONUS = 0xFF4A90D9;
    private static final int C_VALUE_DIM = 0xFFAAAAAA;
    private static final int C_COMBAT_ICON = 0xFF4A90D9;
    private static final int C_COMBAT_VAL = 0xFF4ADFFF;
    private static final int C_SECTION_HDR = 0xFF6A9FC0;
    private static final int C_OVERLAY = 0x88000000;
    private static final int C_HP_RED = 0xFFE53935;
    private static final int C_MP_BLUE = 0xFF42A5F5;
    private static final int C_BUFF_BG = 0x33000000;
    private static final int C_BUFF_TIME = 0xFFFFD700;

    private static final int TITLE_H = 22;
    private static final int PAD = 12;
    private static final int ROW_H = 18;
    private static final float STAT_VALUE_SCALE = 0.9f;

    private int fullW;
    private int fullH;
    private int px;
    private int py;
    private int col2X;

    public StatScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        List<ClientState.BuffEntry> buffs = ClientState.get().getActiveBuffs();
        int buffRows = buffs.isEmpty() ? 0 : 1 + ((buffs.size() + 1) / 2);
        fullH = BASE_H + buffRows * ROW_H;

        fullW = (int) (width / SCALE * 0.41f);
        fullW = Math.max(fullW, Math.min(230, (int) ((width - 8) / SCALE)));
        fullW = Math.min(fullW, (int) ((width - 16) / SCALE));

        int panelW = (int) (fullW * SCALE);
        int panelH = (int) (Math.min(fullH, (height - 10) / SCALE) * SCALE);

        px = (width - panelW) / 2;
        py = (height - panelH) / 2;
        col2X = px + fullW / 2;
    }

    private float logicalMX(double screenMX) {
        return (float) (screenMX - px * (1f - SCALE)) / SCALE;
    }

    private float logicalMY(double screenMY) {
        return (float) (screenMY - py * (1f - SCALE)) / SCALE;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, C_OVERLAY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g, mouseX, mouseY, pt);

        g.pose().pushPose();
        g.pose().translate(px * (1f - SCALE), py * (1f - SCALE), 0f);
        g.pose().scale(SCALE, SCALE, 1f);

        drawPanel(g, (int) logicalMX(mouseX), (int) logicalMY(mouseY));

        g.pose().popPose();
        super.render(g, mouseX, mouseY, pt);
    }

    private void drawPanel(GuiGraphics g, int mx, int my) {
        g.fillGradient(px, py, px + fullW, py + fullH, C_PANEL_TOP, C_PANEL_BOT);

        g.fill(px, py, px + fullW, py + 1, C_EDGE);
        g.fill(px, py + fullH - 1, px + fullW, py + fullH, C_EDGE);
        g.fill(px, py, px + 1, py + fullH, C_EDGE);
        g.fill(px + fullW - 1, py, px + fullW, py + fullH, C_EDGE);
        g.fill(px + 1, py + 1, px + fullW - 1, py + 2, C_EDGE_GLOW);

        g.fill(px + 1, py + 1, px + fullW - 1, py + TITLE_H, C_TITLE_BG);
        g.fill(px + 1, py + TITLE_H, px + fullW - 1, py + TITLE_H + 1, C_EDGE);

        String title = tr("ui.customdamagesystem.stat.title");
        int titleW = textWidth(title);
        draw(g, title, px + (fullW - titleW) / 2, py + (TITLE_H - 8) / 2, C_LABEL, false);

        boolean hovX = mx >= px + fullW - 16 && mx < px + fullW - 4 && my >= py + 4 && my < py + TITLE_H - 2;
        draw(g, "X", px + fullW - 14, py + (TITLE_H - 8) / 2, hovX ? C_VALUE : C_VALUE_DIM, false);

        int profTop = py + TITLE_H + 6;
        drawProfile(g, mx, my, profTop);

        int divY = profTop + 78;
        drawDivider(g, divY);

        int combatY = divY + 8;
        drawCombatPower(g, combatY);

        int div2Y = combatY + ROW_H + 4;
        drawDivider(g, div2Y);

        int statsTop = div2Y + 8;
        drawCombatStats(g, statsTop);

        int div3Y = statsTop + ROW_H * 5 + 8;
        drawDivider(g, div3Y);

        int baseTop = div3Y + 6;
        drawBaseStats(g, baseTop);

        List<ClientState.BuffEntry> buffs = ClientState.get().getActiveBuffs();
        if (!buffs.isEmpty()) {
            int div4Y = baseTop + 14 + ROW_H * 2 + 4;
            drawDivider(g, div4Y);
            drawBuffs(g, div4Y + 6, buffs);
        }
    }

    private void drawDivider(GuiGraphics g, int y) {
        g.fill(px + PAD, y, px + fullW - PAD, y + 1, C_SECTION_LINE);
        g.fill(px + PAD, y + 1, px + fullW - PAD, y + 2, C_SECTION_LINE2);
    }

    private void drawProfile(GuiGraphics g, int mx, int my, int topY) {
        ClientState.PlayerStats s = ClientState.get().getPlayerStats();

        int charBoxX = px + PAD;
        int charBoxW = 66;
        int charBoxH = 72;

        g.fill(charBoxX, topY, charBoxX + charBoxW, topY + charBoxH, 0x22000000);
        g.fill(charBoxX, topY, charBoxX + charBoxW, topY + 1, C_SECTION_LINE);
        g.fill(charBoxX, topY, charBoxX + 1, topY + charBoxH, C_SECTION_LINE);
        g.fill(charBoxX + charBoxW - 1, topY, charBoxX + charBoxW, topY + charBoxH, C_SECTION_LINE);
        g.fill(charBoxX, topY + charBoxH - 1, charBoxX + charBoxW, topY + charBoxH, C_SECTION_LINE);

        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g,
                    charBoxX + 1, topY + 1,
                    charBoxX + charBoxW - 1, topY + charBoxH - 1,
                    28, 0f, mx, my,
                    minecraft.player);
        }

        int infoX = charBoxX + charBoxW + 10;
        String name = s.name().isEmpty() && minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : s.name();
        draw(g, name, infoX, topY + 2, C_VALUE, true);

        int ilBoxY = topY + 14;
        g.fill(infoX, ilBoxY, infoX + 36, ilBoxY + 36, 0x33000000);
        g.fill(infoX, ilBoxY, infoX + 36, ilBoxY + 1, C_SECTION_LINE);
        g.fill(infoX, ilBoxY, infoX + 1, ilBoxY + 36, C_SECTION_LINE);
        g.fill(infoX + 35, ilBoxY, infoX + 36, ilBoxY + 36, C_SECTION_LINE);
        g.fill(infoX, ilBoxY + 35, infoX + 36, ilBoxY + 36, C_SECTION_LINE);

        int txtX = infoX + 42;
        draw(g, tr("ui.customdamagesystem.stat.hp"), txtX, ilBoxY + 2, C_HP_RED, false);
        String hpStr = NUM_FMT.format(s.currentHp()) + " / " + NUM_FMT.format(s.maxHp());
        draw(g, hpStr, px + fullW - PAD - textWidth(hpStr), ilBoxY + 2, C_VALUE_DIM, false);

        draw(g, tr("ui.customdamagesystem.stat.mp"), txtX, ilBoxY + 14, C_MP_BLUE, false);
        String mpStr = NUM_FMT.format(s.currentMp()) + " / " + NUM_FMT.format(s.maxMp());
        draw(g, mpStr, px + fullW - PAD - textWidth(mpStr), ilBoxY + 14, C_VALUE_DIM, false);
    }

    private void drawCombatPower(GuiGraphics g, int y) {
        ClientState.PlayerStats s = ClientState.get().getPlayerStats();
        g.fill(px + PAD, y + 1, px + PAD + 6, y + 7, C_COMBAT_ICON);
        draw(g, tr("ui.customdamagesystem.stat.item_level"), px + PAD + 10, y, C_LABEL, false);
        draw(g, formatItemLevel(s.itemLevel()), px + PAD + 80, y, C_COMBAT_VAL, true);
    }

    private void drawCombatStats(GuiGraphics g, int topY) {
        ClientState.PlayerStats s = ClientState.get().getPlayerStats();
        int lx = px + PAD;
        int lx2 = col2X;

        drawStatRow(g, topY, tr("ui.customdamagesystem.stat.attack"), NUM_FMT.format(s.attack()), lx, tr("ui.customdamagesystem.stat.magic_attack"), NUM_FMT.format(s.magicAttack()), lx2);
        drawStatRow(g, topY + ROW_H, tr("ui.customdamagesystem.stat.defense"), NUM_FMT.format(s.defense()), lx, tr("ui.customdamagesystem.stat.crit_rate"), String.format("%.2f%%", s.critRate()), lx2);
        drawStatRow(g, topY + ROW_H * 2, tr("ui.customdamagesystem.stat.crit_damage"), String.format("%.1f%%", s.critDamage()), lx, tr("ui.customdamagesystem.stat.move_speed"), String.format("%.1f%%", s.moveSpeed()), lx2);
        drawStatRow(g, topY + ROW_H * 3, tr("ui.customdamagesystem.stat.buff_duration"), String.format("%.1f%%", s.buffDuration()), lx, tr("ui.customdamagesystem.stat.cooldown_reduction"), String.format("%.1f%%", s.cooldownReduction()), lx2);
        drawStatRow(g, topY + ROW_H * 4, tr("ui.customdamagesystem.stat.gold_bonus"), String.format("%.1f%%", s.clearGoldBonus()), lx, "", "", lx2);
    }

    private void drawBaseStats(GuiGraphics g, int topY) {
        ClientState.PlayerStats s = ClientState.get().getPlayerStats();
        draw(g, tr("ui.customdamagesystem.stat.base_stats"), px + PAD, topY, C_SECTION_HDR, false);

        int lx = px + PAD;
        int lx2 = col2X;
        int y2 = topY + 14;

        int buffStr = s.strength() - s.equipStrength();
        int buffAgi = s.agility() - s.equipAgility();
        int buffInt = s.intelligence() - s.equipIntelligence();
        int buffLuk = s.luck() - s.equipLuck();

        drawStatRowDot(g, y2, tr("ui.customdamagesystem.stat.strength"), baseStatVal(s.strength(), s.equipStrength(), buffStr), lx, tr("ui.customdamagesystem.stat.agility"), baseStatVal(s.agility(), s.equipAgility(), buffAgi), lx2);
        drawStatRowDot(g, y2 + ROW_H, tr("ui.customdamagesystem.stat.intelligence"), baseStatVal(s.intelligence(), s.equipIntelligence(), buffInt), lx, tr("ui.customdamagesystem.stat.luck"), baseStatVal(s.luck(), s.equipLuck(), buffLuk), lx2);
    }

    private static String baseStatVal(int total, int equip, int buff) {
        if (buff == 0) return String.valueOf(total);
        return total + " (" + equip + "+" + buff + ")";
    }

    private void drawBuffs(GuiGraphics g, int topY, List<ClientState.BuffEntry> buffs) {
        draw(g, tr("ui.customdamagesystem.stat.active_buffs"), px + PAD, topY, C_SECTION_HDR, false);

        int y = topY + 14;
        int lx = px + PAD;
        int colW = (fullW - PAD * 2) / 2;

        for (int i = 0; i < buffs.size(); i++) {
            ClientState.BuffEntry b = buffs.get(i);
            int col = i % 2;
            int row = i / 2;
            int bx = lx + col * colW;
            int by = y + row * ROW_H;

            g.fill(bx, by, bx + colW - 4, by + ROW_H - 2, C_BUFF_BG);
            draw(g, b.name(), bx + 4, by + 2, C_LABEL, false);

            String timeStr = formatSeconds(b.remainingSeconds());
            int tw = textWidth(timeStr);
            draw(g, timeStr, bx + colW - 8 - tw, by + 2, C_BUFF_TIME, false);
        }
    }

    private static String formatSeconds(int seconds) {
        if (seconds >= 60) {
            return I18n.get("ui.customdamagesystem.stat.time_min_sec", seconds / 60, seconds % 60);
        }
        return I18n.get("ui.customdamagesystem.stat.time_sec", seconds);
    }

    private void drawStatRow(GuiGraphics g, int y, String lbl1, String val1, int x1, String lbl2, String val2, int x2) {
        draw(g, lbl1, x1, y, C_LABEL, false);
        if (!val1.isEmpty()) {
            drawScaled(g, val1, x1 + 98, y, C_VALUE, true, STAT_VALUE_SCALE);
        }
        if (!lbl2.isEmpty()) {
            draw(g, lbl2, x2, y, C_LABEL, false);
            drawScaled(g, val2, x2 + 92, y, C_VALUE, true, STAT_VALUE_SCALE);
        }
    }

    private void drawStatRowDot(GuiGraphics g, int y, String lbl1, String val1, int x1, String lbl2, String val2, int x2) {
        g.fill(x1 + 1, y + 3, x1 + 4, y + 6, C_VALUE_BONUS);
        draw(g, lbl1, x1 + 7, y, C_LABEL, false);
        drawScaled(g, val1, x1 + 60, y, C_VALUE, true, STAT_VALUE_SCALE);

        g.fill(x2 + 1, y + 3, x2 + 4, y + 6, C_VALUE_BONUS);
        draw(g, lbl2, x2 + 7, y, C_LABEL, false);
        drawScaled(g, val2, x2 + 60, y, C_VALUE, true, STAT_VALUE_SCALE);
    }

    private void draw(GuiGraphics g, String text, int x, int y, int color, boolean shadow) {
        FontUtil.draw(g, font, text, x, y, color, shadow);
    }

    private int textWidth(String text) {
        return FontUtil.width(font, text);
    }

    private void drawScaled(GuiGraphics g, String text, int x, int y, int color, boolean shadow, float scale) {
        if (scale == 1.0f) {
            draw(g, text, x, y, color, shadow);
            return;
        }
        float inv = 1.0f / scale;
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        FontUtil.draw(g, font, text, Math.round(x * inv), Math.round(y * inv), color, shadow);
        g.pose().popPose();
    }

    private String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    private static String formatItemLevel(double level) {
        if (Math.abs(level - Math.rint(level)) < 1.0e-9) {
            return NUM_FMT.format((long) Math.rint(level));
        }
        return ITEM_LEVEL_FMT.format(level);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            float lmx = logicalMX(mx);
            float lmy = logicalMY(my);

            if (lmx >= px + fullW - 16 && lmx < px + fullW - 4 && lmy >= py + 4 && lmy < py + TITLE_H - 2) {
                onClose();
                return true;
            }
            if (lmx < px || lmx >= px + fullW || lmy < py || lmy >= py + fullH) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }
}
