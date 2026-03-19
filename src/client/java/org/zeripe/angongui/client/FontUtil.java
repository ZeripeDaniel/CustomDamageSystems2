package org.zeripe.angongui.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class FontUtil {
    public static final ResourceLocation HEIROF = ResourceLocation.fromNamespaceAndPath("customdamagesystem", "heirof");
    public static final ResourceLocation NOTOSANS = ResourceLocation.fromNamespaceAndPath("customdamagesystem", "notosans");

    private static final Style S_HEIROF = Style.EMPTY.withFont(HEIROF);
    private static final Style S_NOTOSANS = Style.EMPTY.withFont(NOTOSANS);

    private FontUtil() {}

    public static Component heirof(String text) {
        return Component.literal(text).withStyle(S_HEIROF);
    }

    public static Component notosans(String text) {
        return Component.literal(text).withStyle(S_NOTOSANS);
    }

    public static int draw(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        return g.drawString(font, notosans(text), x, y, color, shadow);
    }

    public static int drawHeirof(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        return g.drawString(font, heirof(text), x, y, color, shadow);
    }

    public static int width(Font font, String text) {
        return font.width(notosans(text));
    }

    public static int widthHeirof(Font font, String text) {
        return font.width(heirof(text));
    }
}
