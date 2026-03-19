package org.zeripe.customdamagesystem.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zeripe.angongserverside.CustomDamageSystemServerMod;
import org.zeripe.angongserverside.combat.CustomHealthManager;
import org.zeripe.angongserverside.combat.StatManager;

/**
 * 바닐라 회복(황금사과, 자연회복, 포션 등)을 커스텀 HP에 1:1 반영.
 * 바닐라 20 회복 → 커스텀 HP 20 회복.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerHealMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void onHeal(float amount, CallbackInfo ci) {
        //noinspection ConstantValue
        if (!((Object) this instanceof ServerPlayer player)) return;

        CustomHealthManager healthManager = CustomDamageSystemServerMod.getHealthManager();
        StatManager statManager = CustomDamageSystemServerMod.getStatManager();
        if (healthManager == null || statManager == null) return;
        if (statManager.getData(player.getUUID()) == null) return;

        // 1:1 회복
        int healAmount = Math.max(1, Math.round(amount));
        healthManager.heal(player, healAmount);
        statManager.syncHpToClient(player);

        // 바닐라 heal 차단 (커스텀 HP로 대체)
        ci.cancel();
    }
}
