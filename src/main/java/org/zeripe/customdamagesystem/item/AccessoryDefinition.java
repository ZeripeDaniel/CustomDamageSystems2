package org.zeripe.customdamagesystem.item;

public record AccessoryDefinition(
        AccessoryType type,
        int strength,
        int agility,
        int intelligence,
        int luck
) {}
