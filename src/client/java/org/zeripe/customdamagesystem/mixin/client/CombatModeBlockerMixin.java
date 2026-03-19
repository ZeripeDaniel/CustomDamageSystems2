package org.zeripe.customdamagesystem.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zeripe.angongui.client.CombatModeState;

@Mixin(Minecraft.class)
public class CombatModeBlockerMixin {

    /**
     * 전투모드에서 F(양손교체), Q(아이템드롭) 차단.
     * handleKeybinds() 진입 시 해당 키 입력을 소비해서 바닐라 로직이 실행되지 않도록 함.
     */
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void customdamagesystem$blockKeybindsInCombat(CallbackInfo ci) {
        if (!CombatModeState.isCombatMode()) return;
        Minecraft mc = (Minecraft) (Object) this;
        // F key – swap offhand
        while (mc.options.keySwapOffhand.consumeClick()) { /* eat */ }
        // Q key – drop item
        while (mc.options.keyDrop.consumeClick()) { /* eat */ }
        // E key – inventory open (아이템 조작 방지)
        while (mc.options.keyInventory.consumeClick()) { /* eat */ }
        // 1-9 hotbar keys – 슬롯 변경 차단 (스킬 트리거는 CombatSkillInputRouter에서 별도 처리)
        for (var hotbarKey : mc.options.keyHotbarSlots) {
            while (hotbarKey.consumeClick()) { /* eat */ }
        }
    }

    /**
     * 전투모드에서 우클릭(아이템 사용 / 블록 설치) 차단.
     */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$blockUseItemInCombat(CallbackInfo ci) {
        if (CombatModeState.isCombatMode()) {
            ci.cancel();
        }
    }
}
