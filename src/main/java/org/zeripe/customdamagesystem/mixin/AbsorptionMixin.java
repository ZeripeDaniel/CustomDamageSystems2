package org.zeripe.customdamagesystem.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zeripe.angongserverside.CustomDamageSystemServerMod;
import org.zeripe.angongserverside.combat.CustomHealthManager;
import org.zeripe.angongserverside.combat.StatManager;

/**
 * Absorption(흡수) 효과를 커스텀 HP에 반영.
 * maxHp를 변경하지 않고, 별도의 absorptionHp로 관리.
 * 총 혜택 = MAX_ABSORPTION(6): 부족HP 회복 + 나머지 흡수.
 */
@Mixin(LivingEntity.class)
public abstract class AbsorptionMixin {

    @Shadow
    public abstract float getAbsorptionAmount();

    /** 이전 Absorption 값 추적 */
    @Unique
    private float customdamagesystem$prevAbsorption = 0f;

    @Inject(method = "setAbsorptionAmount", at = @At("HEAD"))
    private void onSetAbsorption(float newAmount, CallbackInfo ci) {
        //noinspection ConstantValue
        if (!((Object) this instanceof ServerPlayer player)) return;

        CustomHealthManager healthManager = CustomDamageSystemServerMod.getHealthManager();
        StatManager statManager = CustomDamageSystemServerMod.getStatManager();
        if (healthManager == null || statManager == null) return;
        if (statManager.getData(player.getUUID()) == null) return;

        float oldAmount = customdamagesystem$prevAbsorption;
        customdamagesystem$prevAbsorption = Math.max(0, newAmount);

        if (newAmount > 0 && oldAmount <= 0) {
            // Absorption 새로 적용 → 커스텀 흡수 효과 (MAX_ABSORPTION 캡)
            healthManager.applyAbsorption(player, CustomHealthManager.MAX_ABSORPTION);
        } else if (newAmount <= 0 && oldAmount > 0) {
            // Absorption 해제 → 흡수 HP 제거
            healthManager.clearAbsorption(player.getUUID());
        }
        // newAmount > 0 && oldAmount > 0 → 이미 활성 중 (재섭취) → 흡수 갱신
        else if (newAmount > oldAmount && oldAmount > 0) {
            // 재섭취: 기존 흡수 유지, 부족분만 보충
            healthManager.applyAbsorption(player, CustomHealthManager.MAX_ABSORPTION);
        }

        statManager.syncHpToClient(player);
    }
}
