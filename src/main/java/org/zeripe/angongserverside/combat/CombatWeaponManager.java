package org.zeripe.angongserverside.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.zeripe.customdamagesystem.item.AccessoryDataManager;
import org.zeripe.customdamagesystem.item.AccessoryInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatWeaponManager {
    private static final Map<UUID, SavedEquip> saved = new ConcurrentHashMap<>();

    private CombatWeaponManager() {}

    private record SavedEquip(ItemStack mainhand, ItemStack offhand) {}

    public static void enterCombat(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (saved.containsKey(uuid)) return;

        AccessoryInventory inv = AccessoryDataManager.getOrLoad(uuid);
        ItemStack weapon = inv.getItem(AccessoryInventory.WEAPON);
        if (weapon.isEmpty()) return;

        // Save current equipment
        saved.put(uuid, new SavedEquip(
                player.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
                player.getItemBySlot(EquipmentSlot.OFFHAND).copy()
        ));

        // Equip weapon + torch
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
        player.getInventory().setChanged();
    }

    public static void leaveCombat(ServerPlayer player) {
        UUID uuid = player.getUUID();
        SavedEquip eq = saved.remove(uuid);
        if (eq == null) return;

        player.setItemSlot(EquipmentSlot.MAINHAND, eq.mainhand);
        player.setItemSlot(EquipmentSlot.OFFHAND, eq.offhand);
        player.getInventory().setChanged();
    }

    public static void removePlayer(UUID uuid) {
        saved.remove(uuid);
    }

    public static boolean isInCombat(UUID uuid) {
        return saved.containsKey(uuid);
    }
}
