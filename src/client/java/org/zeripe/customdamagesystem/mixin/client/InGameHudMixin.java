package org.zeripe.customdamagesystem.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zeripe.angongui.client.ClientState;

/**
 * 바닐라 HUD 요소를 숨김.
 * 서버에서 customHudEnabled=false 또는 customHealthEnabled=false로 보내면
 * 해당 바닐라 HUD를 그대로 보여줌.
 */
@Mixin(Gui.class)
public class InGameHudMixin {

    /** CDS 서버 config 대기 중이면 바닐라도 숨김 (깜빡임 방지) */
    private static boolean isWaiting() {
        return ClientState.get().isWaitingForConfig();
    }

    /** customHud AND customHealth 둘 다 켜져야 바닐라 체력/방어/음식/공기방울 숨김 */
    private static boolean shouldHideVanillaHealth() {
        return isWaiting()
                || (ClientState.get().isCustomHudEnabled()
                    && ClientState.get().isCustomHealthEnabled());
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideHearts(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (shouldHideVanillaHealth()) ci.cancel();
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void customdamagesystem$hideArmor(GuiGraphics guiGraphics, Player player, int i, int j, int k, int l, CallbackInfo ci) {
        if (shouldHideVanillaHealth()) ci.cancel();
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideFood(GuiGraphics guiGraphics, Player player, int i, int j, CallbackInfo ci) {
        if (shouldHideVanillaHealth()) ci.cancel();
    }

    @Inject(method = "renderAirBubbles", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideAirBubbles(GuiGraphics guiGraphics, Player player, int i, int j, int k, CallbackInfo ci) {
        if (shouldHideVanillaHealth()) ci.cancel();
    }

    /** customHud가 켜져야 바닐라 핫바/XP바/XP레벨 숨김 */
    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideVanillaHotbarInCombat(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (isWaiting() || ClientState.get().isCustomHudEnabled()) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideVanillaXpBar(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        if (isWaiting() || ClientState.get().isCustomHudEnabled()) ci.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$hideVanillaXpLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (isWaiting() || ClientState.get().isCustomHudEnabled()) ci.cancel();
    }

}
