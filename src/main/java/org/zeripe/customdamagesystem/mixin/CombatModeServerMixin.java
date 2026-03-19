package org.zeripe.customdamagesystem.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zeripe.angongserverside.combat.CombatWeaponManager;

/**
 * 서버사이드 전투모드 차단 Mixin.
 * 핵 클라이언트가 클라이언트 Mixin을 우회해도 서버에서 패킷을 무시함.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CombatModeServerMixin {

    @Shadow public ServerPlayer player;

    /** F키(양손교체), Q키(아이템드롭) 차단 */
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$blockActionInCombat(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!CombatWeaponManager.isInCombat(player.getUUID())) return;
        var action = packet.getAction();
        if (action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                || action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
            ci.cancel();
        }
    }

    /** 우클릭 – 블록에 아이템 사용(설치) 차단 */
    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$blockUseItemOnInCombat(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (CombatWeaponManager.isInCombat(player.getUUID())) {
            ci.cancel();
        }
    }

    /** 우클릭 – 아이템 사용(먹기, 물약 등) 차단 */
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$blockUseItemInCombat(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (CombatWeaponManager.isInCombat(player.getUUID())) {
            ci.cancel();
        }
    }

    /** 1-9 핫바 슬롯 변경 차단 */
    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$blockHotbarInCombat(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (CombatWeaponManager.isInCombat(player.getUUID())) {
            ci.cancel();
        }
    }

    /** 인벤토리 슬롯 클릭 차단 (핵 클라이언트가 인벤토리를 강제로 열고 아이템 이동 방지) */
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void customdamagesystem$blockContainerClickInCombat(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (CombatWeaponManager.isInCombat(player.getUUID())) {
            ci.cancel();
        }
    }
}
