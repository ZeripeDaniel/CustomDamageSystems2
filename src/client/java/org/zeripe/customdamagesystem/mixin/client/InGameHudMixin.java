package org.zeripe.customdamagesystem.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideHearts(GuiGraphics guiGraphics, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void customdamagesystem$hideArmor(GuiGraphics guiGraphics, Player player, int i, int j, int k, int l, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideFood(GuiGraphics guiGraphics, Player player, int i, int j, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderAirBubbles", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideAirBubbles(GuiGraphics guiGraphics, Player player, int i, int j, int k, CallbackInfo ci) {
        ci.cancel();
    }
}
