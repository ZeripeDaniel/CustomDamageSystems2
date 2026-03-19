package org.zeripe.customdamagesystem.item;

import net.minecraft.world.SimpleContainer;

public class AccessoryInventory extends SimpleContainer {
    public static final int RING_1 = 0;
    public static final int RING_2 = 1;
    public static final int NECKLACE = 2;
    public static final int EARRING_1 = 3;
    public static final int EARRING_2 = 4;
    public static final int WEAPON = 5;
    public static final int SIZE = 6;

    public AccessoryInventory() {
        super(SIZE);
    }

    public AccessoryType typeForSlot(int slot) {
        return switch (slot) {
            case RING_1, RING_2 -> AccessoryType.RING;
            case NECKLACE -> AccessoryType.NECKLACE;
            case EARRING_1, EARRING_2 -> AccessoryType.EARRING;
            case WEAPON -> AccessoryType.WEAPON;
            default -> null;
        };
    }
}
