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
import org.zeripe.angongserverside.config.AngongGuiConfig;
import org.zeripe.angongserverside.config.ServerConfig;
import org.zeripe.angongserverside.config.StatFormulaConfig;
import org.zeripe.angongserverside.network.ServerNetworkHandler;
import org.zeripe.angongserverside.storage.DatabaseManager;
import org.zeripe.angongserverside.storage.JsonPlayerDataStorage;
import org.zeripe.angongserverside.storage.MysqlPlayerDataStorage;
import org.zeripe.angongserverside.storage.PlayerDataStorage;
import org.zeripe.angongserverside.economy.EconomyProvider;
import org.zeripe.angongserverside.economy.InternalEconomyProvider;
import org.zeripe.angongserverside.economy.ScoreboardEconomyProvider;
import org.zeripe.angongserverside.api.QuestApi;
import org.zeripe.angongserverside.party.PartyManager;
import org.zeripe.angongserverside.quest.QuestConfig;
import org.zeripe.angongserverside.quest.QuestEventListener;
import org.zeripe.angongserverside.quest.QuestManager;
import org.zeripe.angongserverside.quest.QuestRewardGranter;
import org.zeripe.angongserverside.webpanel.WebPanelServer;
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

    private PlayerDataStorage storage;
    private DatabaseManager databaseManager;
    private StatCalculationEngine calcEngine;
    private BuffSystem buffSystem;
    private CustomHealthManager healthManager;
    private StatManager statManager;
    private ServerDamageHandler damageHandler;
    private DamageNumberSender dmgSender;
    private DamageSkinManager skinManager;
    private QuestManager questManager;
    private QuestConfig questConfig;
    private PartyManager partyManager;
    private EconomyProvider economyProvider;
    private WebPanelServer webPanelServer;
    private ZcdsCommand zcdsCommand;
    private ServerConfig serverConfig;
    private AngongGuiConfig angongGuiConfig;
    private MonsterAttackGroupConfig monsterAttackGroupConfig;
    private StatFormulaConfig statFormulaConfig;
    private EquipmentStatConfig equipmentStatConfig;
    private int tickCounter = 0;
    private int equipmentSyncCounter = 0;
    private int equipmentSyncIntervalTicks = 20;
    private int autoSaveCounter = 0;
    private int partyHpSyncCounter = 0;
    private static final int AUTO_SAVE_INTERVAL_TICKS = 1200; // 1분 (20tps * 60s)

    public static CustomHealthManager getHealthManager() {
        return INSTANCE != null ? INSTANCE.healthManager : null;
    }

    public static StatManager getStatManager() {
        return INSTANCE != null ? INSTANCE.statManager : null;
    }

    public static EquipmentStatConfig getEquipmentStatConfig() {
        return INSTANCE != null ? INSTANCE.equipmentStatConfig : null;
    }

    public static EconomyProvider getEconomyProvider() {
        return INSTANCE != null ? INSTANCE.economyProvider : null;
    }

    public static AngongGuiConfig getAngongGuiConfig() {
        return INSTANCE != null ? INSTANCE.angongGuiConfig : null;
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
        zcdsCommand = new ZcdsCommand(equipmentStatConfig, LOGGER);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            zcdsCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            networkHandler.sendSystemConfig(handler.getPlayer());
            networkHandler.sendAccessoryRegistry(handler.getPlayer());
            if (statManager != null) statManager.onPlayerJoin(handler.getPlayer());
            if (skinManager != null) {
                skinManager.onPlayerJoin(handler.getPlayer().getUUID());
                networkHandler.sendSkinList(handler.getPlayer());
                networkHandler.sendAllPlayerSkins(handler.getPlayer());
            }
            if (questManager != null) {
                questManager.onPlayerJoin(handler.getPlayer().getUUID());
                networkHandler.sendQuestList(handler.getPlayer());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            try { CombatWeaponManager.leaveCombat(handler.getPlayer()); } catch (Exception e) { LOGGER.warn("[Quit] CombatWeaponManager.leaveCombat 오류: {}", e.getMessage()); }
            try { CombatWeaponManager.removePlayer(uuid); } catch (Exception e) { LOGGER.warn("[Quit] CombatWeaponManager.removePlayer 오류: {}", e.getMessage()); }
            try { if (statManager != null) statManager.onPlayerQuit(uuid); } catch (Exception e) { LOGGER.warn("[Quit] statManager 오류: {}", e.getMessage()); }
            try { if (skinManager != null) skinManager.onPlayerQuit(uuid); } catch (Exception e) { LOGGER.warn("[Quit] skinManager 오류: {}", e.getMessage()); }
            try { if (questManager != null) questManager.onPlayerQuit(uuid); } catch (Exception e) { LOGGER.warn("[Quit] questManager 오류: {}", e.getMessage()); }
            try { if (partyManager != null) partyManager.onPlayerQuit(uuid, server); } catch (Exception e) { LOGGER.warn("[Quit] partyManager 오류: {}", e.getMessage()); }
            try { AccessoryDataManager.remove(uuid); } catch (Exception e) { LOGGER.warn("[Quit] AccessoryDataManager 오류: {}", e.getMessage()); }
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        LOGGER.info("[CustomDamageSystem] 초기화 완료");
    }

    private void onServerStarted(MinecraftServer server) {
        LOGGER.info("[CustomDamageSystem] 서버 시작 - 시스템 구성 중");
        serverConfig = ServerConfig.load(LOGGER);
        angongGuiConfig = AngongGuiConfig.load(FabricLoader.getInstance().getConfigDir(), LOGGER);
        monsterAttackGroupConfig = MonsterAttackGroupConfig.load(FabricLoader.getInstance().getConfigDir(), LOGGER);
        statFormulaConfig = StatFormulaConfig.load(LOGGER);
        // equipmentStatConfig는 onInitialize에서 이미 로드됨 (커맨드에서 참조)
        equipmentSyncIntervalTicks = Math.max(1, serverConfig.equipmentSyncIntervalTicks);

        // Storage 초기화 (MySQL or JSON)
        if (serverConfig.useMySQL) {
            databaseManager = new DatabaseManager(LOGGER);
            databaseManager.init(serverConfig);
            storage = new MysqlPlayerDataStorage(databaseManager, serverConfig.mysqlTablePrefix, LOGGER);
        } else {
            storage = new JsonPlayerDataStorage(
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),
                    FabricLoader.getInstance().getConfigDir(),
                    LOGGER);
        }
        storage.init();

        calcEngine = new StatCalculationEngine(
                serverConfig.defenseConstant,
                serverConfig.environmentalDamageScale,
                serverConfig.externalDamageUsesCustomDefense,
                statFormulaConfig
        );
        buffSystem = new BuffSystem(LOGGER);
        healthManager = new CustomHealthManager(LOGGER);
        statManager = new StatManager(storage, calcEngine, buffSystem, equipmentStatConfig, healthManager, LOGGER);
        statManager.setVanillaArmorPolicy(serverConfig.includeVanillaArmorForPlayerDefense, serverConfig.vanillaArmorDefenseMultiplier);
        dmgSender = new DamageNumberSender(server);
        dmgSender.applyConfig(serverConfig);
        if (serverConfig.damageSystemEnabled) {
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
        }

        HitCooldownRegistry.get().setDefaultTicks(serverConfig.defaultHitCooldownTicks);
        HitCooldownRegistry.get().setNonWeaponTicks(serverConfig.nonWeaponHitCooldownTicks);
        HitCooldownRegistry.get().setAttackSpeedPolicy(
                serverConfig.useVanillaAttackSpeedForHitCooldown,
                serverConfig.minAttackSpeedCooldownTicks,
                serverConfig.maxAttackSpeedCooldownTicks
        );
        AccessoryDataManager.init(storage);
        registerWeaponsFromConfig();

        // 데미지 스킨 시스템
        DamageSkinConfig skinConfig = DamageSkinConfig.load(LOGGER);
        skinManager = new DamageSkinManager(storage, skinConfig, LOGGER);
        dmgSender.setSkinManager(skinManager);

        // 퀘스트 시스템
        if (serverConfig.questSystemEnabled) {
            questConfig = new QuestConfig(LOGGER);
            questConfig.load();
            questManager = new QuestManager(LOGGER, questConfig, storage);
            questManager.setDailyResetTime(serverConfig.dailyResetHour, serverConfig.dailyResetTimezone);
            QuestRewardGranter rewardGranter = new QuestRewardGranter(LOGGER);
            rewardGranter.setStatManager(statManager);
            rewardGranter.setServer(server);
            questManager.setRewardGranter(rewardGranter);
            // 퀘스트 상태 변경 시 클라이언트에 실시간 전송
            questManager.setUpdateCallback((playerId, questData, definition) -> {
                net.minecraft.server.level.ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                if (target != null) {
                    String json = questManager.buildQuestUpdateJson(questData, definition).toString();
                    ServerNetworkHandler.sendStat(target, json);
                }
            });
            QuestApi.bind(questManager);
            new QuestEventListener(questManager, LOGGER).register();
            networkHandler.setQuestManager(questManager);
        }

        // 파티 시스템
        partyManager = new PartyManager(LOGGER);
        partyManager.setHealthManager(healthManager);
        if (damageHandler != null) {
            damageHandler.setPartyManager(partyManager);
            damageHandler.setPartyFriendlyFire(serverConfig.partyFriendlyFire);
        }

        networkHandler.setStatManager(statManager);
        networkHandler.setSkinManager(skinManager);
        networkHandler.setServerConfig(serverConfig);
        networkHandler.setAngongGuiConfig(angongGuiConfig);
        networkHandler.setPartyManager(partyManager);
        networkHandler.setEquipmentStatConfig(equipmentStatConfig);
        if (damageHandler != null) damageHandler.register();
        CustomDamageApi.bind(statManager);

        // Economy 시스템
        switch (serverConfig.economyProvider.toLowerCase()) {
            case "scoreboard" -> {
                economyProvider = new ScoreboardEconomyProvider(server, serverConfig.scoreboardObjective, LOGGER);
                LOGGER.info("[CustomDamageSystem] 이코노미 모드: scoreboard (objective: {})", serverConfig.scoreboardObjective);
            }
            default -> {
                economyProvider = new InternalEconomyProvider(statManager);
                LOGGER.info("[CustomDamageSystem] 이코노미 모드: internal");
            }
        }

        // Web Panel
        if (serverConfig.webPanelEnabled) {
            webPanelServer = new WebPanelServer(
                    serverConfig.webPanelPort, serverConfig.webPanelPassword,
                    storage, statManager, serverConfig,
                    FabricLoader.getInstance().getConfigDir(),
                    server, LOGGER, equipmentStatConfig);
            webPanelServer.setAngongGuiConfig(angongGuiConfig);
            if (questConfig != null) webPanelServer.setQuestConfig(questConfig);
            webPanelServer.start();
        }

        // ZcdsCommand에 StatManager, EconomyProvider 주입
        if (zcdsCommand != null) {
            zcdsCommand.setStatManager(statManager);
            zcdsCommand.setEconomyProvider(economyProvider);
        }

        LOGGER.info("[CustomDamageSystem] 시스템 구성 완료");
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("[CustomDamageSystem] 서버 종료 - 스탯 저장 중");
        if (webPanelServer != null) webPanelServer.stop();
        AccessoryDataManager.shutdown();
        if (questManager != null) questManager.saveAll();
        if (skinManager != null) skinManager.saveAll();
        if (statManager != null) statManager.saveAll();
        if (storage != null) storage.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
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
        if (serverConfig != null && serverConfig.statSystemEnabled) {
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
        }
        if (damageHandler != null) {
            damageHandler.cleanupExpiredCooldowns(server.overworld().getGameTime());
        }
        // 파티 HP 동기화 (20틱 = 1초 주기)
        partyHpSyncCounter++;
        if (partyManager != null && partyHpSyncCounter >= 20) {
            partyHpSyncCounter = 0;
            partyManager.tickHpSync(server);
        }
        autoSaveCounter++;
        if (autoSaveCounter >= AUTO_SAVE_INTERVAL_TICKS) {
            autoSaveCounter = 0;
            if (statManager != null) statManager.saveAll();
            AccessoryDataManager.saveAll();
            if (skinManager != null) skinManager.saveAll();
            if (questManager != null) questManager.saveAll();
            LOGGER.debug("[CustomDamageSystem] 자동 저장 완료");
        }
    }
}
