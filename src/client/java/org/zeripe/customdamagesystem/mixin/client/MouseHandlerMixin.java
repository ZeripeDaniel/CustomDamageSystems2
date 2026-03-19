package org.zeripe.customdamagesystem.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zeripe.angongui.client.CombatModeState;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$blockScrollInCombat(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CombatModeState.isCombatMode()) {
            ci.cancel();
        }
    }
}
