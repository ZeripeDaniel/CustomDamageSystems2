package org.zeripe.customdamagesystem.item;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AccessorySlot extends Slot {
    private final AccessoryType requiredType;

    public AccessorySlot(Container container, int index, int x, int y, AccessoryType type) {
        super(container, index, x, y);
        this.requiredType = type;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return AccessoryRegistry.find(stack.getItem())
                .map(def -> def.type() == requiredType)
                .orElse(false);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
