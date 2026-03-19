package org.zeripe.angongui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Accessory equipment screen — dark-themed panel overlays the crafting area
 * while preserving the vanilla inventory / hotbar below.
 */
public class AccessoryInventoryScreen extends InventoryScreen {

    /* ── colour palette (matches StatScreen dark-blue theme) ─────────── */
    private static final int C_BG         = 0xF0101828;
    private static final int C_TITLE_BG   = 0xFF0D1219;
    private static final int C_BORDER     = 0xFF2A4A6B;
    private static final int C_GLOW       = 0x222A4A6B;
    private static final int C_DIVIDER    = 0xFF1A2E40;
    private static final int C_LABEL      = 0xFF8BAEC8;
    private static final int C_SLOT_BG    = 0xFF0D1219;
    private static final int C_SLOT_HI    = 0xFF3A5A7B;
    private static final int C_SLOT_LO    = 0xFF060C14;
    private static final int C_HOVER      = 0x44FFFFFF;
    private static final int C_ACCENT     = 0xFF4A90D9;
    private static final int C_RING       = 0xFFFFD700;   // gold
    private static final int C_NECK       = 0xFF4ADFFF;   // cyan
    private static final int C_EAR        = 0xFFE040FB;   // pink
    /* ── panel geometry (relative to leftPos / topPos) ───────────────── */
    private static final int PX = 82, PY = 6, PW = 92, PH = 68;
    private static final int SZ = 18;                       // slot pixel size

    /* ── tab geometry ────────────────────────────────────────────────── */
    private static final int TAB_SZ = 22, TAB_GAP = 2;

    /* ── slot grid: {relX, relY} inside the panel ────────────────────── */
    private static final int[][] SLOTS = {
            {8,  26},   // 0  Ring 1
            {37, 26},   // 1  Necklace
            {66, 26},   // 2  Earring 1
            {8,  47},   // 3  Ring 2
            {66, 47},   // 4  Earring 2
    };
    private static final int[] SLOT_ACCENT = {C_RING, C_NECK, C_EAR, C_RING, C_EAR};

    public AccessoryInventoryScreen(Player player) {
        super(player);
    }

    /* ================================================================ */
    /*  init                                                             */
    /* ================================================================ */
    @Override
    protected void init() {
        super.init();
        this.clearWidgets();          // remove recipe-book toggle

        int tx = this.leftPos - TAB_SZ - 1;
        int ty = this.topPos + 4;

        addRenderableWidget(new IconTabButton(
                tx, ty, TAB_SZ,
                new ItemStack(Items.CHEST),
                Component.translatable("ui.customdamagesystem.tab.inventory"),
                false,
                () -> { if (minecraft != null && minecraft.player != null) minecraft.setScreen(new InventoryScreen(minecraft.player)); }
        ));
        addRenderableWidget(new IconTabButton(
                tx, ty + TAB_SZ + TAB_GAP, TAB_SZ,
                new ItemStack(Items.TOTEM_OF_UNDYING),
                Component.translatable("ui.customdamagesystem.tab.accessory"),
                true,
                () -> {}
        ));
    }

    /* ================================================================ */
    /*  render                                                           */
    /* ================================================================ */
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);

        int px = this.leftPos + PX;
        int py = this.topPos + PY;

        /* ── panel background ──────────────────────────────────────── */
        g.fill(px, py, px + PW, py + PH, C_BG);
        drawBorder(g, px, py, PW, PH);

        /* ── title bar ─────────────────────────────────────────────── */
        g.fill(px + 1, py + 1, px + PW - 1, py + 13, C_TITLE_BG);
        g.fill(px + 1, py + 13, px + PW - 1, py + 14, C_BORDER);
        g.fill(px + 1, py + 1, px + PW - 1, py + 2, C_GLOW);

        // small accent diamond before title
        int diamX = px + 6;
        int diamY = py + 5;
        g.fill(diamX + 1, diamY, diamX + 3, diamY + 1, C_ACCENT);
        g.fill(diamX, diamY + 1, diamX + 4, diamY + 2, C_ACCENT);
        g.fill(diamX + 1, diamY + 2, diamX + 3, diamY + 3, C_ACCENT);

        String title = I18n.get("ui.customdamagesystem.acc.title");
        int tw = FontUtil.width(this.font, title);
        FontUtil.draw(g, this.font, title, px + (PW - tw) / 2 + 3, py + 3, C_LABEL, false);

        /* ── column labels ─────────────────────────────────────────── */
        int ly = py + 16;
        drawCentered(g, I18n.get("ui.customdamagesystem.acc.ring"),     px + 17, ly, C_RING);
        drawCentered(g, I18n.get("ui.customdamagesystem.acc.necklace"), px + 46, ly, C_NECK);
        drawCentered(g, I18n.get("ui.customdamagesystem.acc.earring"),  px + 75, ly, C_EAR);

        /* ── slots ─────────────────────────────────────────────────── */
        for (int i = 0; i < SLOTS.length; i++) {
            int sx = px + SLOTS[i][0];
            int sy = py + SLOTS[i][1];
            boolean hov = mx >= sx && mx < sx + SZ && my >= sy && my < sy + SZ;
            drawSlot(g, sx, sy, hov, SLOT_ACCENT[i]);
        }

        /* ── decorative connector line (row-1 ↔ row-2 middle) ────── */
        int connY = py + 44;
        g.fill(px + 8 + SZ + 2, connY, px + 66 - 2, connY + 1, C_DIVIDER);

        /* ── centre diamond decoration (empty necklace-row-2 area) ── */
        int cdx = px + 44;
        int cdy = py + 53;
        g.fill(cdx + 1, cdy, cdx + 3, cdy + 1, C_DIVIDER);
        g.fill(cdx, cdy + 1, cdx + 4, cdy + 2, C_DIVIDER);
        g.fill(cdx + 1, cdy + 2, cdx + 3, cdy + 3, C_DIVIDER);
    }

    /* ================================================================ */
    /*  mouse interaction                                                */
    /* ================================================================ */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Block clicks on the panel area (prevents touching hidden crafting slots)
        int px = this.leftPos + PX;
        int py = this.topPos + PY;
        if (mx >= px && mx < px + PW && my >= py && my < py + PH) {
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /* ================================================================ */
    /*  drawing helpers                                                  */
    /* ================================================================ */
    private void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, C_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        g.fill(x, y, x + 1, y + h, C_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, C_BORDER);
    }

    private void drawSlot(GuiGraphics g, int x, int y, boolean hovered, int accent) {
        // 3-D bevel
        g.fill(x, y, x + SZ, y + 1, C_SLOT_HI);
        g.fill(x, y, x + 1, y + SZ, C_SLOT_HI);
        g.fill(x + SZ - 1, y + 1, x + SZ, y + SZ, C_SLOT_LO);
        g.fill(x + 1, y + SZ - 1, x + SZ, y + SZ, C_SLOT_LO);
        // fill
        g.fill(x + 1, y + 1, x + SZ - 1, y + SZ - 1, C_SLOT_BG);
        // colour accent dot (top-left)
        g.fill(x + 1, y + 1, x + 3, y + 3, accent);
        // hover highlight
        if (hovered) {
            g.fill(x + 1, y + 1, x + SZ - 1, y + SZ - 1, C_HOVER);
        }
    }

    private void drawCentered(GuiGraphics g, String text, int cx, int y, int colour) {
        int w = FontUtil.width(this.font, text);
        FontUtil.draw(g, this.font, text, cx - w / 2, y, colour, false);
    }
}
