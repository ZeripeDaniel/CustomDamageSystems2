package org.zeripe.angongserverside.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.equipment.Equippable;
import org.slf4j.Logger;
import org.zeripe.angongserverside.config.EquipmentStatConfig;
import org.zeripe.customdamagesystem.item.AccessoryDefinition;
import org.zeripe.customdamagesystem.item.AccessoryRegistry;
import org.zeripe.customdamagesystem.item.AccessoryType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /zcds 커맨드 (Fabric Brigadier)
 */
public final class ZcdsCommand {

    public enum ItemType {
        WEAPON("mainhand"),
        RING("ring"),
        NECKLACE("necklace"),
        EARRING("earring"),
        HELMET("head"),
        CHESTPLATE("chest"),
        LEGGINGS("legs"),
        BOOTS("feet");

        public final String slot;

        ItemType(String slot) {
            this.slot = slot;
        }
    }

    private final EquipmentStatConfig config;
    private final Logger logger;

    public ZcdsCommand(EquipmentStatConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zcds")
                .requires(src -> src.hasPermission(2))

                // /zcds register <type> <id>
                .then(Commands.literal("register")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(typeSuggestions())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(this::executeRegister))))

                // /zcds unregister <id>
                .then(Commands.literal("unregister")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(registryIdSuggestions())
                                .executes(this::executeUnregister)))

                // /zcds list
                .then(Commands.literal("list")
                        .executes(this::executeList))

                // /zcds give <player> <id>
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(registryIdSuggestions())
                                        .executes(this::executeGive))))
        );
    }

    // ═══════════════════════════════════════
    //  register
    // ═══════════════════════════════════════

    private int executeRegister(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("플레이어만 사용할 수 있습니다."));
            return 0;
        }

        String typeStr = StringArgumentType.getString(ctx, "type");
        String registryId = StringArgumentType.getString(ctx, "id").toLowerCase();

        ItemType type;
        try {
            type = ItemType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("§c올바르지 않은 타입: " + typeStr));
            src.sendFailure(Component.literal("§7사용 가능: " + Arrays.stream(ItemType.values())
                    .map(t -> t.name().toLowerCase()).collect(Collectors.joining(", "))));
            return 0;
        }

        if (config.findByRegistryId(registryId) != null) {
            src.sendFailure(Component.literal("§c이미 등록된 ID: " + registryId));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("§c아이템을 손에 들고 실행하세요."));
            return 0;
        }

        // Extract item info
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int customModelData = 0;
        CustomModelData cmdData = held.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmdData != null && !cmdData.floats().isEmpty()) {
            customModelData = cmdData.floats().getFirst().intValue();
        }
        Component customName = held.get(DataComponents.CUSTOM_NAME);
        String displayName = customName != null ? customName.getString() : null;

        // 장비 타입이면 Equippable 컴포넌트 추가 (방어구 슬롯 장착 가능하게)
        EquipmentSlot equipSlot = getEquipmentSlot(type);
        if (equipSlot != null && held.get(DataComponents.EQUIPPABLE) == null) {
            held.set(DataComponents.EQUIPPABLE, Equippable.builder(equipSlot).build());
        }

        // registry_id 태그를 CUSTOM_DATA에 삽입 (클라이언트 아이템 레벨 표시용)
        {
            CustomData existing = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag customTag = existing.copyTag();
            CompoundTag pbv = customTag.contains("PublicBukkitValues", Tag.TAG_COMPOUND)
                    ? customTag.getCompound("PublicBukkitValues") : new CompoundTag();
            pbv.putString("customdamagesystem:registry_id", registryId);
            customTag.put("PublicBukkitValues", pbv);
            held.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
        }

        // 원본 아이템 전체를 SNBT로 직렬화
        String itemData = null;
        try {
            Tag tag = held.save(player.registryAccess());
            itemData = tag.toString();
        } catch (Exception e) {
            logger.warn("[ZcdsCommand] 아이템 SNBT 직렬화 실패: {}", e.getMessage());
        }

        // Create entry with default stats
        EquipmentStatConfig.ItemEntry entry = createDefaultEntry(type, registryId, itemId,
                customModelData, displayName);
        entry.itemData = itemData;

        config.addEntry(entry);

        // 악세서리/무기 타입이면 AccessoryRegistry에도 등록 (슬롯 장착 허용)
        AccessoryType accType = getAccessoryType(type);
        if (accType != null) {
            AccessoryRegistry.register(held.getItem(), new AccessoryDefinition(
                    accType, entry.strength, entry.agility, entry.intelligence, entry.luck));
        }

        try {
            config.save();
            src.sendSuccess(() -> Component.literal("§a아이템 등록 완료!"), true);
            src.sendSuccess(() -> Component.literal("§7ID: §f" + registryId), false);
            src.sendSuccess(() -> Component.literal("§7타입: §f" + type.name()), false);
            src.sendSuccess(() -> Component.literal("§7아이템: §f" + itemId), false);
            if (customModelData != 0) {
                int cmd = customModelData;
                src.sendSuccess(() -> Component.literal("§7CustomModelData: §f" + cmd), false);
            }
            src.sendSuccess(() -> Component.literal("§e기본 스탯이 적용되었습니다. JSON에서 세부 조정 가능합니다."), false);
        } catch (Exception e) {
            src.sendFailure(Component.literal("§c저장 실패: " + e.getMessage()));
            logger.warn("[ZcdsCommand] 설정 저장 실패: {}", e.getMessage());
        }
        return 1;
    }

    // ═══════════════════════════════════════
    //  unregister
    // ═══════════════════════════════════════

    private int executeUnregister(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String registryId = StringArgumentType.getString(ctx, "id").toLowerCase();

        if (config.findByRegistryId(registryId) == null) {
            src.sendFailure(Component.literal("§c등록되지 않은 ID: " + registryId));
            return 0;
        }

        config.removeByRegistryId(registryId);
        try {
            config.save();
            src.sendSuccess(() -> Component.literal("§a등록 해제 완료: §f" + registryId), true);
        } catch (Exception e) {
            src.sendFailure(Component.literal("§c저장 실패: " + e.getMessage()));
        }
        return 1;
    }

    // ═══════════════════════════════════════
    //  list
    // ═══════════════════════════════════════

    private int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (config.items == null || config.items.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7등록된 아이템이 없습니다."), false);
            return 1;
        }

        src.sendSuccess(() -> Component.literal("§6═══ 등록된 아이템 목록 ═══"), false);
        int count = 0;
        for (EquipmentStatConfig.ItemEntry entry : config.items) {
            if (entry == null) continue;
            count++;
            String id = entry.registryId != null ? entry.registryId : entry.itemId;
            String typeStr = guessType(entry);
            String cmd = entry.customModelData != 0 ? " §b[CMD:" + entry.customModelData + "]" : "";
            String name = entry.displayName != null ? " §f" + entry.displayName : "";
            String line = "§e" + count + ". §a" + id + " §7(" + entry.itemId + ") [" + typeStr + "]" + cmd + name;
            src.sendSuccess(() -> Component.literal(line), false);
        }
        int total = count;
        src.sendSuccess(() -> Component.literal("§7총 §f" + total + "§7개"), false);
        return 1;
    }

    // ═══════════════════════════════════════
    //  give
    // ═══════════════════════════════════════

    private int executeGive(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String registryId = StringArgumentType.getString(ctx, "id").toLowerCase();

        EquipmentStatConfig.ItemEntry entry = config.findByRegistryId(registryId);
        if (entry == null) {
            src.sendFailure(Component.literal("§c등록되지 않은 ID: " + registryId));
            return 0;
        }

        ItemStack item;

        // 저장된 SNBT 데이터가 있으면 원본 아이템 복원
        if (entry.itemData != null && !entry.itemData.isEmpty()) {
            try {
                CompoundTag tag = TagParser.parseTag(entry.itemData);
                item = ItemStack.parse(target.registryAccess(), tag).orElse(ItemStack.EMPTY);
                if (item.isEmpty()) {
                    src.sendFailure(Component.literal("§c저장된 아이템 데이터를 복원할 수 없습니다."));
                    return 0;
                }
            } catch (Exception e) {
                src.sendFailure(Component.literal("§cSNBT 파싱 실패: " + e.getMessage()));
                logger.warn("[ZcdsCommand] SNBT 파싱 실패: {}", e.getMessage());
                return 0;
            }
        } else {
            // itemData가 없으면 기존 방식으로 기본 아이템 생성
            var itemRef = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.itemId));
            if (itemRef.isEmpty()) {
                src.sendFailure(Component.literal("§c아이템을 찾을 수 없습니다: " + entry.itemId));
                return 0;
            }
            item = new ItemStack(itemRef.get().value());

            // CustomModelData
            if (entry.customModelData != 0) {
                item.set(DataComponents.CUSTOM_MODEL_DATA,
                        new CustomModelData(List.of((float) entry.customModelData), List.of(), List.of(), List.of()));
            }

            // Display name
            if (entry.displayName != null) {
                item.set(DataComponents.CUSTOM_NAME, Component.literal(entry.displayName));
            }
        }

        // 장비 타입이면 Equippable 컴포넌트 보장
        EquipmentSlot equipSlot = getEquipmentSlotFromEntry(entry);
        if (equipSlot != null && item.get(DataComponents.EQUIPPABLE) == null) {
            item.set(DataComponents.EQUIPPABLE, Equippable.builder(equipSlot).build());
        }

        // Lore (스탯 표시) — 원본 아이템 위에 덮어씌움
        List<Component> lore = new ArrayList<>();
        if (entry.attack > 0) lore.add(Component.literal("§c⚔ 공격력: §f+" + (int) entry.attack));
        if (entry.magicAttack > 0) lore.add(Component.literal("§9✦ 마법공격력: §f+" + (int) entry.magicAttack));
        if (entry.defense > 0) lore.add(Component.literal("§b🛡 방어력: §f+" + (int) entry.defense));
        if (entry.hp > 0) lore.add(Component.literal("§a♥ HP: §f+" + (int) entry.hp));
        if (entry.critRate > 0) lore.add(Component.literal("§e⚡ 크리티컬: §f+" + entry.critRate + "%"));
        if (entry.critDamage > 0) lore.add(Component.literal("§6💥 크리데미지: §f+" + entry.critDamage + "%"));
        if (entry.strength > 0) lore.add(Component.literal("§c✦ 힘: §f+" + entry.strength));
        if (entry.agility > 0) lore.add(Component.literal("§a✦ 민첩: §f+" + entry.agility));
        if (entry.intelligence > 0) lore.add(Component.literal("§9✦ 지능: §f+" + entry.intelligence));
        if (entry.luck > 0) lore.add(Component.literal("§e✦ 운: §f+" + entry.luck));
        if (!lore.isEmpty()) {
            item.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        }

        // registry_id 태그를 CUSTOM_DATA에 삽입 (클라이언트 아이템 레벨 표시용)
        // Paper PDC 호환 형식: PublicBukkitValues.customdamagesystem:registry_id
        CustomData existing = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customTag = existing.copyTag();
        CompoundTag pbv = customTag.contains("PublicBukkitValues", Tag.TAG_COMPOUND)
                ? customTag.getCompound("PublicBukkitValues") : new CompoundTag();
        pbv.putString("customdamagesystem:registry_id", registryId);
        customTag.put("PublicBukkitValues", pbv);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));

        target.getInventory().add(item);
        src.sendSuccess(() -> Component.literal("§a" + target.getName().getString() + "에게 §f" + registryId + "§a 지급 완료!"), true);
        return 1;
    }

    // ═══════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════

    private EquipmentStatConfig.ItemEntry createDefaultEntry(
            ItemType type, String registryId, String itemId,
            int customModelData, String displayName) {

        EquipmentStatConfig.ItemEntry e = new EquipmentStatConfig.ItemEntry();
        e.registryId = registryId;
        e.itemId = itemId;
        e.customModelData = customModelData;
        e.displayName = displayName;
        e.slots = new ArrayList<>(List.of(type.slot));
        e.itemLevel = 1.0;
        e.itemLevelSlot = type == ItemType.WEAPON ? 1 : 2;

        switch (type) {
            case WEAPON -> {
                e.overrideVanillaMainhand = true;
                e.overrideVanillaArmor = false;
                e.attack = 10.0;
                e.critRate = 2.0;
                e.critDamage = 10.0;
                e.attackSpeed = 4.0;
                e.weapons = 1;
            }
            case RING -> {
                e.overrideVanillaMainhand = false;
                e.overrideVanillaArmor = false;
                e.strength = 2;
                e.luck = 1;
            }
            case NECKLACE -> {
                e.overrideVanillaMainhand = false;
                e.overrideVanillaArmor = false;
                e.intelligence = 2;
                e.agility = 1;
            }
            case EARRING -> {
                e.overrideVanillaMainhand = false;
                e.overrideVanillaArmor = false;
                e.agility = 2;
                e.luck = 1;
            }
            case HELMET -> {
                e.overrideVanillaMainhand = false;
                e.overrideVanillaArmor = true;
                e.defense = 4.0;
                e.hp = 5.0;
            }
            case CHESTPLATE -> {
                e.overrideVanillaMainhand = false;
                e.overrideVanillaArmor = true;
                e.defense = 8.0;
                e.hp = 10.0;
            }
            case LEGGINGS -> {
                e.overrideVanillaMainhand = false;
                e.overrideVanillaArmor = true;
                e.defense = 6.0;
                e.hp = 8.0;
            }
            case BOOTS -> {
                e.overrideVanillaMainhand = false;
                e.overrideVanillaArmor = true;
                e.defense = 4.0;
                e.hp = 5.0;
                e.moveSpeed = 5.0;
            }
        }
        return e;
    }

    private String guessType(EquipmentStatConfig.ItemEntry entry) {
        if (entry.weapons == 1) return "WEAPON";
        if (entry.slots == null || entry.slots.isEmpty()) return "UNKNOWN";
        String slot = entry.slots.getFirst().toLowerCase();
        return switch (slot) {
            case "ring" -> "RING";
            case "necklace" -> "NECKLACE";
            case "earring" -> "EARRING";
            case "head" -> "HELMET";
            case "chest" -> "CHESTPLATE";
            case "legs" -> "LEGGINGS";
            case "feet" -> "BOOTS";
            case "mainhand" -> "WEAPON";
            default -> slot.toUpperCase();
        };
    }

    /** ItemType → AccessoryType 매핑. 방어구는 null */
    private AccessoryType getAccessoryType(ItemType type) {
        return switch (type) {
            case RING -> AccessoryType.RING;
            case NECKLACE -> AccessoryType.NECKLACE;
            case EARRING -> AccessoryType.EARRING;
            case WEAPON -> AccessoryType.WEAPON;
            default -> null; // HELMET, CHESTPLATE, LEGGINGS, BOOTS
        };
    }

    /** ItemType → EquipmentSlot 매핑. 악세서리/무기는 null */
    private EquipmentSlot getEquipmentSlot(ItemType type) {
        return switch (type) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
            default -> null; // WEAPON, RING, NECKLACE, EARRING
        };
    }

    /** ItemEntry의 slots 정보로 EquipmentSlot 추론 */
    private EquipmentSlot getEquipmentSlotFromEntry(EquipmentStatConfig.ItemEntry entry) {
        if (entry.slots == null || entry.slots.isEmpty()) return null;
        String slot = entry.slots.getFirst().toLowerCase();
        return switch (slot) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private SuggestionProvider<CommandSourceStack> typeSuggestions() {
        return (ctx, builder) -> {
            for (ItemType t : ItemType.values()) {
                String name = t.name().toLowerCase();
                if (name.startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> registryIdSuggestions() {
        return (ctx, builder) -> {
            if (config.items != null) {
                for (EquipmentStatConfig.ItemEntry entry : config.items) {
                    if (entry != null && entry.registryId != null
                            && entry.registryId.startsWith(builder.getRemainingLowerCase())) {
                        builder.suggest(entry.registryId);
                    }
                }
            }
            return builder.buildFuture();
        };
    }
}
