package org.zeripe.customdamagesystem.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class AccessoryItem extends Item {

    private final AccessoryType accessoryType;
    private final int strength;
    private final int agility;
    private final int intelligence;
    private final int luck;

    public AccessoryItem(Properties properties, AccessoryType type, int str, int agi, int intel, int luk) {
        super(properties);
        this.accessoryType = type;
        this.strength = str;
        this.agility = agi;
        this.intelligence = intel;
        this.luck = luk;
    }

    public AccessoryType getAccessoryType() {
        return accessoryType;
    }

    public AccessoryDefinition toDefinition() {
        return new AccessoryDefinition(accessoryType, strength, agility, intelligence, luck);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.translatable("ui.customdamagesystem.acc.type." + accessoryType.name().toLowerCase())
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltipComponents.add(Component.empty());

        if (strength != 0) addStat(tooltipComponents, "ui.customdamagesystem.stat.strength", strength);
        if (agility != 0) addStat(tooltipComponents, "ui.customdamagesystem.stat.agility", agility);
        if (intelligence != 0) addStat(tooltipComponents, "ui.customdamagesystem.stat.intelligence", intelligence);
        if (luck != 0) addStat(tooltipComponents, "ui.customdamagesystem.stat.luck", luck);
    }

    private static void addStat(List<Component> list, String key, int value) {
        String sign = value > 0 ? "+" : "";
        ChatFormatting color = value > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        list.add(Component.literal("  ")
                .append(Component.translatable(key))
                .append(Component.literal(" " + sign + value))
                .withStyle(color));
    }
}
