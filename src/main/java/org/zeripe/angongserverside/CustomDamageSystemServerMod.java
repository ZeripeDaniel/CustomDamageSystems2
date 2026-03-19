package org.zeripe.angongserverside;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeripe.angongserverside.api.CustomDamageApi;
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

    @Override
    public void onInitialize() {
        LOGGER.info("[CustomDamageSystem] 초기화 시작");
        networkHandler.registerPayloads();
        networkHandler.registerReceivers();

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
        equipmentStatConfig = EquipmentStatConfig.load(LOGGER);
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
            if (entry == null || entry.itemId == null || entry.weapons != 1) continue;
            BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.itemId)).ifPresent(ref -> {
                HitCooldownRegistry.get().registerWeapon(ref.value());
                if (!AccessoryRegistry.isAccessory(ref.value())) {
                    AccessoryRegistry.register(ref.value(),
                            new AccessoryDefinition(AccessoryType.WEAPON, entry.strength, entry.agility, entry.intelligence, entry.luck));
                }
            });
        }
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
