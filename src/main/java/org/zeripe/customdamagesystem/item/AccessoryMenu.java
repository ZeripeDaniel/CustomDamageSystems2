package org.zeripe.customdamagesystem.item;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.zeripe.customdamagesystem.ModMenuTypes;

public class AccessoryMenu extends AbstractContainerMenu {
    private final AccessoryInventory accessories;
    private boolean clientOnly = false;

    private static final int ACC_END = 6;
    private static final int INV_START = 6;
    private static final int INV_END = 33;   // 6 + 27
    private static final int HOT_START = 33;
    private static final int HOT_END = 42;   // 33 + 9

    /** Server constructor */
    public AccessoryMenu(int syncId, Inventory playerInventory, AccessoryInventory accessories) {
        super(ModMenuTypes.ACCESSORY, syncId);
        this.accessories = accessories;

        // ── Accessory slots (6) ──
        // Positions must match AccessoryScreen slot background drawing
        addSlot(new AccessorySlot(accessories, 0, 91,  33, AccessoryType.RING));      // R1
        addSlot(new AccessorySlot(accessories, 1, 91,  54, AccessoryType.RING));      // R2
        addSlot(new AccessorySlot(accessories, 2, 120, 33, AccessoryType.NECKLACE));  // N
        addSlot(new AccessorySlot(accessories, 3, 149, 33, AccessoryType.EARRING));   // E1
        addSlot(new AccessorySlot(accessories, 4, 149, 54, AccessoryType.EARRING));   // E2
        addSlot(new AccessorySlot(accessories, 5, 120, 54, AccessoryType.WEAPON));    // W

        // ── Player inventory (3×9) ──
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // ── Hotbar (1×9) ──
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Client constructor (used by MenuType factory) */
    public AccessoryMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new AccessoryInventory());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index < ACC_END) {
            // From accessory → player inventory
            if (!moveItemStackTo(original, INV_START, HOT_END, true)) return ItemStack.EMPTY;
        } else {
            // From player inventory → try accessory first
            if (AccessoryRegistry.isAccessory(original.getItem())) {
                AccessoryDefinition def = AccessoryRegistry.find(original.getItem()).orElse(null);
                if (def != null && tryMoveToAccessory(original, def.type())) {
                    // success
                } else if (index < INV_END) {
                    if (!moveItemStackTo(original, HOT_START, HOT_END, false)) return ItemStack.EMPTY;
                } else {
                    if (!moveItemStackTo(original, INV_START, INV_END, false)) return ItemStack.EMPTY;
                }
            } else if (index < INV_END) {
                if (!moveItemStackTo(original, HOT_START, HOT_END, false)) return ItemStack.EMPTY;
            } else {
                if (!moveItemStackTo(original, INV_START, INV_END, false)) return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    private boolean tryMoveToAccessory(ItemStack stack, AccessoryType type) {
        for (int i = 0; i < ACC_END; i++) {
            Slot s = this.slots.get(i);
            if (s instanceof AccessorySlot && !s.hasItem() && s.mayPlace(stack)) {
                s.set(stack.split(1));
                return true;
            }
        }
        return false;
    }

    /** 클라이언트 전용 모드 설정 (플러그인 서버에서 사용) */
    public void setClientOnly(boolean clientOnly) {
        this.clientOnly = clientOnly;
    }

    public boolean isClientOnly() {
        return clientOnly;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!clientOnly && !player.level().isClientSide()) {
            AccessoryDataManager.save(player.getUUID());
        }
    }
}
