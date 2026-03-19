package org.zeripe.angongui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Square icon-tab button used for INV / ACC navigation.
 * Renders a dark-themed tab with an item icon and tooltip on hover.
 */
public class IconTabButton extends AbstractWidget {

    private static final int C_ACTIVE   = 0xF0101828;
    private static final int C_INACTIVE = 0xD01A2636;
    private static final int C_HOVER    = 0xD0202C3C;
    private static final int C_BORDER   = 0xFF2A4A6B;
    private static final int C_ACCENT   = 0xFF4A90D9;

    private final ItemStack icon;
    private final Runnable action;
    private final boolean tabActive;

    public IconTabButton(int x, int y, int size, ItemStack icon,
                         Component tooltip, boolean tabActive, Runnable action) {
        super(x, y, size, size, Component.empty());
        this.icon = icon;
        this.action = action;
        this.tabActive = tabActive;
        this.active = !tabActive;          // inactive tab = clickable widget
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHovered();

        // background
        int bg = tabActive ? C_ACTIVE : (hov ? C_HOVER : C_INACTIVE);
        g.fill(x, y, x + w, y + h, bg);

        // border — top, bottom, left always; right only when inactive
        g.fill(x, y, x + w, y + 1, C_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        g.fill(x, y, x + 1, y + h, C_BORDER);
        if (!tabActive) {
            g.fill(x + w - 1, y, x + w, y + h, C_BORDER);
        }

        // accent bar on active tab
        if (tabActive) {
            g.fill(x + 1, y + 1, x + 3, y + h - 1, C_ACCENT);
        }

        // 16×16 item icon, centred
        g.renderItem(icon, x + (w - 16) / 2, y + (h - 16) / 2);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
