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
import org.zeripe.angongserverside.combat.HitCooldownRegistry;
import org.zeripe.angongserverside.combat.ServerDamageHandler;
import org.zeripe.angongserverside.combat.StatCalculationEngine;
import org.zeripe.angongserverside.combat.StatManager;
import org.zeripe.angongserverside.config.MonsterAttackGroupConfig;
import org.zeripe.angongserverside.config.ServerConfig;
import org.zeripe.angongserverside.config.StatFormulaConfig;
import org.zeripe.angongserverside.network.ServerNetworkHandler;
import org.zeripe.angongserverside.stats.StatStorage;

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
    private ServerConfig serverConfig;
    private MonsterAttackGroupConfig monsterAttackGroupConfig;
    private StatFormulaConfig statFormulaConfig;
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("[CustomDamageSystem] 초기화 시작");
        networkHandler.registerPayloads();
        networkHandler.registerReceivers();

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (statManager != null) statManager.onPlayerJoin(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (statManager != null) statManager.onPlayerQuit(handler.getPlayer().getUUID());
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        LOGGER.info("[CustomDamageSystem] 초기화 완료");
    }

    private void onServerStarted(MinecraftServer server) {
        LOGGER.info("[CustomDamageSystem] 서버 시작 - 시스템 구성 중");
        serverConfig = ServerConfig.load(LOGGER);
        monsterAttackGroupConfig = MonsterAttackGroupConfig.load(FabricLoader.getInstance().getConfigDir(), LOGGER);
        statFormulaConfig = StatFormulaConfig.load(LOGGER);

        storage = new StatStorage(server, LOGGER);
        calcEngine = new StatCalculationEngine(
                serverConfig.defenseConstant,
                serverConfig.environmentalDamageScale,
                serverConfig.externalDamageUsesCustomDefense,
                statFormulaConfig
        );
        buffSystem = new BuffSystem(LOGGER);
        healthManager = new CustomHealthManager(LOGGER);
        statManager = new StatManager(storage, calcEngine, buffSystem, healthManager, LOGGER);
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
        HitCooldownRegistry.get().setAttackSpeedPolicy(
                serverConfig.useVanillaAttackSpeedForHitCooldown,
                serverConfig.minAttackSpeedCooldownTicks,
                serverConfig.maxAttackSpeedCooldownTicks
        );
        networkHandler.setStatManager(statManager);
        damageHandler.register();
        CustomDamageApi.bind(statManager);
        LOGGER.info("[CustomDamageSystem] 시스템 구성 완료");
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("[CustomDamageSystem] 서버 종료 - 스탯 저장 중");
        if (statManager != null) statManager.saveAll();
        if (storage != null) storage.close();
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            if (buffSystem != null) buffSystem.tickAll();
        }
    }
}
