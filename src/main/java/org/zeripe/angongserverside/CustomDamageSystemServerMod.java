package org.zeripe.angongserverside;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeripe.angongserverside.api.CustomDamageApi;
import org.zeripe.angongserverside.command.ZcdsCommand;
import org.zeripe.angongserverside.combat.BuffSystem;
import org.zeripe.angongserverside.combat.CustomHealthManager;
import org.zeripe.angongserverside.combat.DamageNumberSender;
import org.zeripe.angongserverside.combat.DamageSkinManager;
import org.zeripe.angongserverside.combat.HitCooldownRegistry;
import org.zeripe.angongserverside.combat.ServerDamageHandler;
import org.zeripe.angongserverside.combat.StatCalculationEngine;
import org.zeripe.angongserverside.combat.StatManager;
import org.zeripe.angongserverside.config.EquipmentStatConfig;
import org.zeripe.angongserverside.config.MonsterAttackGroupConfig;
import org.zeripe.angongserverside.config.DamageSkinConfig;
import org.zeripe.angongserverside.config.ServerConfig;
import org.zeripe.angongserverside.config.StatFormulaConfig;
import org.zeripe.angongserverside.network.ServerNetworkHandler;
import org.zeripe.angongserverside.stats.StatStorage;
import org.zeripe.customdamagesystem.item.AccessoryDataManager;
import org.zeripe.angongserverside.combat.CombatWeaponManager;
import org.zeripe.customdamagesystem.item.AccessoryDefinition;
import org.zeripe.customdamagesystem.item.AccessoryRegistry;
import org.zeripe.customdamagesystem.item.AccessoryType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class CustomDamageSystemServerMod implements ModInitializer {
    public static final String MOD_ID = "customdamagesystem";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CustomDamageSystemServerMod INSTANCE;

    private final ServerNetworkHandler networkHandler = new ServerNetworkHandler(LOGGER);

    private StatStorage storage;
    private StatCalculationEngine calcEngine;
    private BuffSystem buffSystem;
    private CustomHealthManager healthManager;
    private StatManager statManager;
    private ServerDamageHandler damageHandler;
    private DamageNumberSender dmgSender;
    private DamageSkinManager skinManager;
    private ServerConfig serverConfig;
    private MonsterAttackGroupConfig monsterAttackGroupConfig;
    private StatFormulaConfig statFormulaConfig;
    private EquipmentStatConfig equipmentStatConfig;
    private int tickCounter = 0;
    private int equipmentSyncCounter = 0;
    private int equipmentSyncIntervalTicks = 20;

    public static CustomHealthManager getHealthManager() {
        return INSTANCE != null ? INSTANCE.healthManager : null;
    }

    public static StatManager getStatManager() {
        return INSTANCE != null ? INSTANCE.statManager : null;
    }

    public static EquipmentStatConfig getEquipmentStatConfig() {
        return INSTANCE != null ? INSTANCE.equipmentStatConfig : null;
    }

    @Override
    public void onInitialize() {
        INSTANCE = this;
        LOGGER.info("[CustomDamageSystem] 초기화 시작");
        networkHandler.registerPayloads();
        networkHandler.registerReceivers();

        // Config 미리 로드 (커맨드에서 참조하기 위해)
        equipmentStatConfig = EquipmentStatConfig.load(LOGGER);

        // /zcds 커맨드 등록
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ZcdsCommand zcds = new ZcdsCommand(equipmentStatConfig, LOGGER);
            zcds.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (statManager != null) statManager.onPlayerJoin(handler.getPlayer());
            if (skinManager != null) {
                skinManager.onPlayerJoin(handler.getPlayer().getUUID());
                networkHandler.sendSkinList(handler.getPlayer());
                networkHandler.sendAllPlayerSkins(handler.getPlayer());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            CombatWeaponManager.leaveCombat(handler.getPlayer());
            CombatWeaponManager.removePlayer(uuid);
            if (statManager != null) statManager.onPlayerQuit(uuid);
            if (skinManager != null) skinManager.onPlayerQuit(uuid);
            AccessoryDataManager.remove(uuid);
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        LOGGER.info("[CustomDamageSystem] 초기화 완료");
    }

    private void onServerStarted(MinecraftServer server) {
        LOGGER.info("[CustomDamageSystem] 서버 시작 - 시스템 구성 중");
        serverConfig = ServerConfig.load(LOGGER);
        monsterAttackGroupConfig = MonsterAttackGroupConfig.load(FabricLoader.getInstance().getConfigDir(), LOGGER);
        statFormulaConfig = StatFormulaConfig.load(LOGGER);
        // equipmentStatConfig는 onInitialize에서 이미 로드됨 (커맨드에서 참조)
        equipmentSyncIntervalTicks = Math.max(1, serverConfig.equipmentSyncIntervalTicks);

        storage = new StatStorage(server, LOGGER);
        calcEngine = new StatCalculationEngine(
                serverConfig.defenseConstant,
                serverConfig.environmentalDamageScale,
                serverConfig.externalDamageUsesCustomDefense,
                statFormulaConfig
        );
        buffSystem = new BuffSystem(LOGGER);
        healthManager = new CustomHealthManager(LOGGER);
        statManager = new StatManager(storage, calcEngine, buffSystem, equipmentStatConfig, healthManager, LOGGER);
        dmgSender = new DamageNumberSender(server);
        dmgSender.applyConfig(serverConfig);
        damageHandler = new ServerDamageHandler(
                healthManager,
                calcEngine,
                statManager,
                dmgSender,
                LOGGER,
                serverConfig.includeVanillaArmorForPlayerDefense,
                serverConfig.vanillaArmorDefenseMultiplier,
                monsterAttackGroupConfig
        );

        HitCooldownRegistry.get().setDefaultTicks(serverConfig.defaultHitCooldownTicks);
        HitCooldownRegistry.get().setNonWeaponTicks(serverConfig.nonWeaponHitCooldownTicks);
        HitCooldownRegistry.get().setAttackSpeedPolicy(
                serverConfig.useVanillaAttackSpeedForHitCooldown,
                serverConfig.minAttackSpeedCooldownTicks,
                serverConfig.maxAttackSpeedCooldownTicks
        );
        AccessoryDataManager.init(server);
        registerWeaponsFromConfig();

        // 데미지 스킨 시스템
        DamageSkinConfig skinConfig = DamageSkinConfig.load(LOGGER);
        skinManager = new DamageSkinManager(
                FabricLoader.getInstance().getConfigDir(), skinConfig, LOGGER);
        dmgSender.setSkinManager(skinManager);

        networkHandler.setStatManager(statManager);
        networkHandler.setSkinManager(skinManager);
        damageHandler.register();
        CustomDamageApi.bind(statManager);
        LOGGER.info("[CustomDamageSystem] 시스템 구성 완료");
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("[CustomDamageSystem] 서버 종료 - 스탯 저장 중");
        AccessoryDataManager.shutdown();
        if (skinManager != null) skinManager.saveAll();
        if (statManager != null) statManager.saveAll();
        if (storage != null) storage.close();
    }

    private void registerWeaponsFromConfig() {
        if (equipmentStatConfig == null) return;
        for (var entry : equipmentStatConfig.items) {
            if (entry == null || entry.itemId == null) continue;

            // slots 기반으로 AccessoryType 결정
            AccessoryType accType = getAccessoryTypeFromSlots(entry);

            BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.itemId)).ifPresent(ref -> {
                // 무기면 히트 쿨다운 등록
                if (entry.weapons == 1) {
                    HitCooldownRegistry.get().registerWeapon(ref.value());
                }
                // 악세서리/무기 타입이면 AccessoryRegistry에 등록
                if (accType != null && !AccessoryRegistry.isAccessory(ref.value())) {
                    AccessoryRegistry.register(ref.value(),
                            new AccessoryDefinition(accType, entry.strength, entry.agility, entry.intelligence, entry.luck));
                }
            });
        }
    }

    private AccessoryType getAccessoryTypeFromSlots(EquipmentStatConfig.ItemEntry entry) {
        if (entry.slots == null || entry.slots.isEmpty()) {
            return entry.weapons == 1 ? AccessoryType.WEAPON : null;
        }
        String slot = entry.slots.getFirst().toLowerCase();
        return switch (slot) {
            case "ring" -> AccessoryType.RING;
            case "necklace" -> AccessoryType.NECKLACE;
            case "earring" -> AccessoryType.EARRING;
            case "mainhand" -> AccessoryType.WEAPON;
            default -> null; // head, chest, legs, feet → 바닐라 장비 슬롯
        };
    }

    private void onServerTick(MinecraftServer server) {
        equipmentSyncCounter++;
        if (statManager != null && equipmentSyncCounter >= equipmentSyncIntervalTicks) {
            equipmentSyncCounter = 0;
            statManager.tickEquipmentSync(server);
        }
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            if (buffSystem != null) buffSystem.tickAll();
        }
        if (damageHandler != null) {
            damageHandler.cleanupExpiredCooldowns(server.overworld().getGameTime());
        }
    }
}
