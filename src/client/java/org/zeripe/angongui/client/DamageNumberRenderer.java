package org.zeripe.angongui.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class DamageNumberRenderer {
    private static final NumberFormat NUM_FMT = NumberFormat.getNumberInstance(Locale.US);

    private static final int COLOR_PHYSICAL = 0xFFFFFFFF;
    private static final int COLOR_MAGICAL = 0xFFCC66FF;
    private static final int COLOR_HEAL = 0xFF44FF44;
    private static final int COLOR_CRIT = 0xFFFFD700;
    private static final int COLOR_SHADOW = 0xFF000000;

    private static final long LIFETIME_MS = 1500L;
    private static final float RISE_DISTANCE = 30f;
    private static final float NORMAL_SCALE = 1.0f;
    private static final float CRIT_SCALE = 1.5f;

    private static final List<DamageNumber> active = new ArrayList<>();

    private DamageNumberRenderer() {}

    private record DamageNumber(
            double worldX, double worldY, double worldZ,
            int amount, boolean crit, String type, long spawnTime
    ) {}

    public static void add(double x, double y, double z, int amount, boolean crit, String type) {
        active.add(new DamageNumber(x, y, z, amount, crit, type, System.currentTimeMillis()));
    }

    public static void register() {
        HudRenderCallback.EVENT.register((g, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            render(g, mc.font);
        });
    }

    public static void render(GuiGraphics g, Font font) {
        if (active.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();

        var camera = mc.gameRenderer.getMainCamera();
        var cam = camera.getPosition();
        Quaternionf viewRotation = new Quaternionf(camera.rotation()).conjugate();
        float fov = mc.options.fov().get().floatValue();
        Matrix4f projection = mc.gameRenderer.getProjectionMatrix(fov);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        Iterator<DamageNumber> it = active.iterator();
        while (it.hasNext()) {
            DamageNumber dn = it.next();
            long age = now - dn.spawnTime;
            if (age >= LIFETIME_MS) {
                it.remove();
                continue;
            }

            float progress = (float) age / LIFETIME_MS;
            Vector3f viewPos = new Vector3f(
                    (float) (dn.worldX - cam.x),
                    (float) (dn.worldY - cam.y),
                    (float) (dn.worldZ - cam.z)
            ).rotate(viewRotation);

            Vector4f clipPos = new Vector4f(viewPos, 1.0f);
            clipPos.mul(projection);
            if (clipPos.w() <= 0) continue;

            float ndcX = clipPos.x() / clipPos.w();
            float ndcY = clipPos.y() / clipPos.w();
            float sx = (ndcX * 0.5f + 0.5f) * screenW;
            float sy = (1.0f - (ndcY * 0.5f + 0.5f)) * screenH;
            sy -= RISE_DISTANCE * progress;

            float alpha = progress < 0.6f ? 1.0f : 1.0f - (progress - 0.6f) / 0.4f;
            int alphaInt = Math.clamp((int) (alpha * 255), 0, 255);
            if (alphaInt <= 4) continue;

            int baseColor = switch (dn.type.toUpperCase()) {
                case "MAGICAL", "MAGIC" -> COLOR_MAGICAL;
                case "HEAL" -> COLOR_HEAL;
                default -> COLOR_PHYSICAL;
            };
            if (dn.crit) baseColor = COLOR_CRIT;

            int color = (alphaInt << 24) | (baseColor & 0x00FFFFFF);
            int shadowColor = (alphaInt << 24) | (COLOR_SHADOW & 0x00FFFFFF);

            String text = NUM_FMT.format(dn.amount);
            if (dn.crit) text += "!";
            if ("HEAL".equalsIgnoreCase(dn.type)) text = "+" + text;
            Component c = Component.literal(text);

            float scale = dn.crit ? CRIT_SCALE : NORMAL_SCALE;
            if (progress < 0.07f) scale *= 1.0f + 0.3f * (1.0f - progress / 0.07f);

            int textW = font.width(c);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(sx, sy, 0);
            pose.scale(scale, scale, 1.0f);
            pose.translate(-textW / 2f, -4f, 0);
            g.drawString(font, c, 1, 1, shadowColor, false);
            g.drawString(font, c, 0, 0, color, false);
            pose.popPose();
        }
    }

    public static void clear() {
        active.clear();
    }
}
