package org.zeripe.angongserverside.webpanel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.zeripe.angongserverside.combat.StatManager;
import org.zeripe.angongserverside.config.AngongGuiConfig;
import org.zeripe.angongserverside.config.EquipmentStatConfig;
import org.zeripe.angongserverside.config.ServerConfig;
import org.zeripe.angongserverside.quest.QuestConfig;
import org.zeripe.angongserverside.quest.QuestDefinition;
import org.zeripe.angongserverside.quest.QuestObjective;
import org.zeripe.angongserverside.quest.QuestReward;
import org.zeripe.angongserverside.stats.PlayerStatData;
import org.zeripe.angongserverside.storage.PlayerDataStorage;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class WebPanelServer {

    private final int port;
    private final String password;
    private final PlayerDataStorage storage;
    private final StatManager statManager;
    private final ServerConfig serverConfig;
    private final Path configDir;
    private final MinecraftServer minecraftServer;
    private final Logger logger;
    private final Gson gson;
    private final EquipmentStatConfig equipmentStatConfig;
    private AngongGuiConfig angongGuiConfig;
    private QuestConfig questConfig;

    private HttpServer server;
    private String cachedIndexHtml;

    private final Map<String, Long> authTokens = new ConcurrentHashMap<>();
    private static final long TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000L; // 24 hours

    public WebPanelServer(int port, String password, PlayerDataStorage storage,
                          StatManager statManager, ServerConfig serverConfig,
                          Path configDir, MinecraftServer minecraftServer, Logger logger) {
        this(port, password, storage, statManager, serverConfig, configDir, minecraftServer, logger, null);
    }

    public WebPanelServer(int port, String password, PlayerDataStorage storage,
                          StatManager statManager, ServerConfig serverConfig,
                          Path configDir, MinecraftServer minecraftServer, Logger logger,
                          EquipmentStatConfig equipmentStatConfig) {
        this.port = port;
        this.password = password;
        this.storage = storage;
        this.statManager = statManager;
        this.serverConfig = serverConfig;
        this.configDir = configDir;
        this.minecraftServer = minecraftServer;
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.equipmentStatConfig = equipmentStatConfig;
    }

    public void setAngongGuiConfig(AngongGuiConfig config) {
        this.angongGuiConfig = config;
    }

    public void setQuestConfig(QuestConfig questConfig) {
        this.questConfig = questConfig;
    }

    public void start() {
      try {
        doStart();
      } catch (IOException e) {
        logger.error("WebPanel: Failed to start HTTP server on port {}: {}", port, e.getMessage());
      }
    }

    private void doStart() throws IOException {
        // Cache the index.html from resources
        try (InputStream is = getClass().getResourceAsStream("/webpanel/index.html")) {
            if (is != null) {
                cachedIndexHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                cachedIndexHtml = "<html><body><h1>Web Panel</h1><p>index.html not found in resources.</p></body></html>";
                logger.warn("WebPanel: /webpanel/index.html not found in resources, using fallback page.");
            }
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/", new StaticHandler());
        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/dashboard", new DashboardHandler());
        server.createContext("/api/players", new PlayersHandler());
        server.createContext("/api/config", new ConfigHandler());
        server.createContext("/api/items", new ItemsHandler());
        server.createContext("/api/additional", new AdditionalHandler());
        server.createContext("/api/quests", new QuestsHandler());
        server.createContext("/api/restart", new RestartHandler());
        server.createContext("/api/system", new SystemInfoHandler());

        server.start();
        logger.info("WebPanel server started on port {}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("WebPanel server stopped.");
        }
    }

    // ──────────────────────────────────────────────────
    //  Utility methods
    // ──────────────────────────────────────────────────

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private boolean handlePreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7).trim();
        Long expiry = authTokens.get(token);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            authTokens.remove(token);
            return false;
        }
        return true;
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object obj) throws IOException {
        String json = gson.toJson(obj);
        sendJsonString(exchange, statusCode, json);
    }

    private void sendJsonString(HttpExchange exchange, int statusCode, String json) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        boolean isHead = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        exchange.sendResponseHeaders(statusCode, isHead ? -1 : bytes.length);
        if (!isHead) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            exchange.getResponseBody().close();
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJsonString(exchange, statusCode, gson.toJson(error));
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        authTokens.entrySet().removeIf(entry -> now > entry.getValue());
    }

    // ──────────────────────────────────────────────────
    //  Handlers
    // ──────────────────────────────────────────────────

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                addCorsHeaders(exchange);
                byte[] bytes = cachedIndexHtml.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                logger.error("WebPanel: Error serving static page", e);
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    private class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                String body = readRequestBody(exchange);
                JsonObject request = JsonParser.parseString(body).getAsJsonObject();
                String providedPassword = request.has("password") ? request.get("password").getAsString() : "";

                if (!password.equals(providedPassword)) {
                    sendError(exchange, 401, "Invalid password");
                    return;
                }

                cleanExpiredTokens();

                String token = UUID.randomUUID().toString();
                authTokens.put(token, System.currentTimeMillis() + TOKEN_EXPIRY_MS);

                JsonObject response = new JsonObject();
                response.addProperty("token", token);
                sendJsonString(exchange, 200, gson.toJson(response));

            } catch (Exception e) {
                logger.error("WebPanel: Error in auth handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!isAuthenticated(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                JsonObject dashboard = new JsonObject();
                dashboard.addProperty("onlinePlayers", minecraftServer.getPlayerCount());
                dashboard.addProperty("totalPlayers", storage.loadAllStats().size());
                dashboard.addProperty("storageType", serverConfig.useMySQL ? "mysql" : "json");
                dashboard.addProperty("economyProvider", serverConfig.economyProvider);
                dashboard.addProperty("webPanelPort", serverConfig.webPanelPort);

                JsonArray onlinePlayerList = new JsonArray();
                for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
                    JsonObject playerObj = new JsonObject();
                    playerObj.addProperty("name", player.getGameProfile().getName());
                    playerObj.addProperty("uuid", player.getUUID().toString());
                    onlinePlayerList.add(playerObj);
                }
                dashboard.add("onlinePlayerList", onlinePlayerList);

                sendJsonString(exchange, 200, gson.toJson(dashboard));

            } catch (Exception e) {
                logger.error("WebPanel: Error in dashboard handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    private class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!isAuthenticated(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod().toUpperCase();

                // POST /api/players/{uuid}/gold
                if (path.matches("/api/players/[^/]+/gold") && "POST".equals(method)) {
                    handleGoldAction(exchange, path);
                    return;
                }

                // GET /api/players/{uuid}
                if (path.matches("/api/players/[^/]+") && "GET".equals(method)) {
                    handleGetPlayer(exchange, path);
                    return;
                }

                // GET /api/players
                if ("/api/players".equals(path) && "GET".equals(method)) {
                    handleGetAllPlayers(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("WebPanel: Error in players handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetAllPlayers(HttpExchange exchange) throws IOException {
            List<PlayerStatData> allStats = storage.loadAllStats();
            JsonArray playersArray = new JsonArray();
            for (PlayerStatData stat : allStats) {
                JsonObject p = new JsonObject();
                p.addProperty("uuid", stat.uuid);
                p.addProperty("name", stat.name);
                p.addProperty("gold", stat.gold);
                p.addProperty("itemLevel", stat.itemLevel);
                p.addProperty("currentHp", stat.currentHp);
                p.addProperty("currentMp", stat.currentMp);
                p.addProperty("equipAttack", stat.equipAttack);
                p.addProperty("equipDefense", stat.equipDefense);
                p.addProperty("equipStrength", stat.equipStrength);
                p.addProperty("equipAgility", stat.equipAgility);
                p.addProperty("equipIntelligence", stat.equipIntelligence);
                p.addProperty("equipLuck", stat.equipLuck);
                playersArray.add(p);
            }
            sendJsonString(exchange, 200, gson.toJson(playersArray));
        }

        private void handleGetPlayer(HttpExchange exchange, String path) throws IOException {
            String uuid = path.substring("/api/players/".length());
            // Try online data first (has computed transient fields)
            PlayerStatData data = null;
            try {
                data = statManager.getData(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {}
            // Fall back to storage
            if (data == null) {
                data = storage.loadStats(uuid, "");
            }
            if (data == null) {
                sendError(exchange, 404, "Player not found");
                return;
            }
            sendJsonString(exchange, 200, gson.toJson(data));
        }

        private void handleGoldAction(HttpExchange exchange, String path) throws IOException {
            // Extract UUID from /api/players/{uuid}/gold
            String[] parts = path.split("/");
            // parts: ["", "api", "players", "{uuid}", "gold"]
            String uuidStr = parts[3];

            String body = readRequestBody(exchange);
            JsonObject request = JsonParser.parseString(body).getAsJsonObject();

            String action = request.has("action") ? request.get("action").getAsString() : "";
            long amount = request.has("amount") ? request.get("amount").getAsLong() : 0;

            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid UUID format");
                return;
            }

            long newBalance;

            // Check if player is online via StatManager
            boolean isOnline = statManager.getData(playerUuid) != null;

            if (isOnline) {
                switch (action) {
                    case "set":
                        statManager.setGoldByUuid(playerUuid, amount);
                        break;
                    case "add":
                        statManager.addGoldByUuid(playerUuid, amount);
                        break;
                    case "remove":
                        statManager.removeGoldByUuid(playerUuid, amount);
                        break;
                    default:
                        sendError(exchange, 400, "Invalid action. Use 'set', 'add', or 'remove'.");
                        return;
                }
                newBalance = statManager.getGold(playerUuid);
            } else {
                // Player is offline - load from storage, modify, save
                PlayerStatData stats = storage.loadStats(uuidStr, null);
                if (stats == null) {
                    sendError(exchange, 404, "Player not found");
                    return;
                }

                switch (action) {
                    case "set":
                        stats.gold = amount;
                        break;
                    case "add":
                        stats.gold += amount;
                        break;
                    case "remove":
                        stats.gold -= amount;
                        if (stats.gold < 0) stats.gold = 0;
                        break;
                    default:
                        sendError(exchange, 400, "Invalid action. Use 'set', 'add', or 'remove'.");
                        return;
                }
                newBalance = stats.gold;
                storage.saveStats(stats);
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("newBalance", newBalance);
            sendJsonString(exchange, 200, gson.toJson(response));
        }
    }

    private class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!isAuthenticated(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                String method = exchange.getRequestMethod().toUpperCase();

                if ("GET".equals(method)) {
                    handleGetConfig(exchange);
                } else if ("POST".equals(method)) {
                    handleUpdateConfig(exchange);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }

            } catch (Exception e) {
                logger.error("WebPanel: Error in config handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetConfig(HttpExchange exchange) throws IOException {
            sendJsonString(exchange, 200, gson.toJson(serverConfig));
        }

        private void handleUpdateConfig(HttpExchange exchange) throws IOException {
            Path configFile = configDir.resolve("customdamagesystem-server.json");

            // Read existing config from file
            JsonObject existingConfig;
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile, StandardCharsets.UTF_8);
                existingConfig = JsonParser.parseString(content).getAsJsonObject();
            } else {
                // Use current serverConfig as base
                existingConfig = JsonParser.parseString(gson.toJson(serverConfig)).getAsJsonObject();
            }

            // Parse the incoming update fields
            String body = readRequestBody(exchange);
            JsonObject updates = JsonParser.parseString(body).getAsJsonObject();

            // Merge updates into existing config
            for (Map.Entry<String, JsonElement> entry : updates.entrySet()) {
                existingConfig.add(entry.getKey(), entry.getValue());
            }

            // Write back to file
            Files.writeString(configFile, gson.toJson(existingConfig), StandardCharsets.UTF_8);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Config saved. Restart server to apply changes.");
            sendJsonString(exchange, 200, gson.toJson(response));
        }
    }

    // ──────────────────────────────────────────────────
    //  Items (EquipmentStatConfig) handler
    // ──────────────────────────────────────────────────

    private class ItemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!isAuthenticated(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                if (equipmentStatConfig == null) {
                    sendError(exchange, 503, "Item config not available");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod().toUpperCase();

                String remaining = path.substring("/api/items".length());
                if (remaining.isEmpty() || remaining.equals("/")) {
                    if ("GET".equals(method)) {
                        handleItemList(exchange);
                    } else if ("POST".equals(method)) {
                        handleAddItem(exchange);
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                } else {
                    String withoutSlash = remaining.startsWith("/") ? remaining.substring(1) : remaining;
                    String registryId = java.net.URLDecoder.decode(withoutSlash, StandardCharsets.UTF_8);

                    switch (method) {
                        case "GET" -> handleGetItem(exchange, registryId);
                        case "POST" -> handleUpdateItem(exchange, registryId);
                        case "DELETE" -> handleDeleteItem(exchange, registryId);
                        default -> sendError(exchange, 405, "Method not allowed");
                    }
                }
            } catch (Exception e) {
                logger.error("WebPanel: Error in items handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleItemList(HttpExchange exchange) throws IOException {
            JsonArray arr = new JsonArray();
            for (EquipmentStatConfig.ItemEntry item : equipmentStatConfig.items) {
                arr.add(itemToJson(item));
            }
            sendJsonString(exchange, 200, gson.toJson(arr));
        }

        private void handleGetItem(HttpExchange exchange, String registryId) throws IOException {
            EquipmentStatConfig.ItemEntry item = equipmentStatConfig.findByRegistryId(registryId);
            if (item == null) {
                for (EquipmentStatConfig.ItemEntry e : equipmentStatConfig.items) {
                    if (registryId.equals(e.itemId)) { item = e; break; }
                }
            }
            if (item == null) {
                sendError(exchange, 404, "Item not found");
                return;
            }
            sendJsonString(exchange, 200, gson.toJson(itemToJson(item)));
        }

        private void handleAddItem(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();

            EquipmentStatConfig.ItemEntry entry = new EquipmentStatConfig.ItemEntry();
            entry.itemId = getStr(req, "itemId", "minecraft:stone");
            entry.registryId = getStr(req, "registryId", null);
            entry.oraxenId = getStr(req, "oraxenId", null);
            entry.customModelData = getInt(req, "customModelData", 0);
            entry.displayName = getStr(req, "displayName", null);
            entry.itemData = getStr(req, "itemData", null);
            entry.itemLevel = getDbl(req, "itemLevel", 0);
            entry.itemLevelSlot = getInt(req, "itemLevelSlot", 0);
            entry.overrideVanillaMainhand = getBool(req, "overrideVanillaMainhand", true);
            entry.overrideVanillaArmor = getBool(req, "overrideVanillaArmor", true);
            entry.rarity = getStr(req, "rarity", "NORMAL");
            applyStatsFromJson(req, entry);

            if (req.has("slots") && req.get("slots").isJsonArray()) {
                entry.slots = new java.util.ArrayList<>();
                req.getAsJsonArray("slots").forEach(e -> entry.slots.add(e.getAsString()));
            }

            equipmentStatConfig.addEntry(entry);
            equipmentStatConfig.save();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJsonString(exchange, 200, gson.toJson(response));
        }

        private void handleUpdateItem(HttpExchange exchange, String registryId) throws IOException {
            EquipmentStatConfig.ItemEntry found = equipmentStatConfig.findByRegistryId(registryId);
            if (found == null) {
                for (EquipmentStatConfig.ItemEntry e : equipmentStatConfig.items) {
                    if (registryId.equals(e.itemId)) { found = e; break; }
                }
            }
            if (found == null) {
                sendError(exchange, 404, "Item not found");
                return;
            }
            final EquipmentStatConfig.ItemEntry item = found;

            String body = readRequestBody(exchange);
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();

            if (req.has("itemId")) item.itemId = req.get("itemId").getAsString();
            if (req.has("registryId")) item.registryId = req.get("registryId").isJsonNull() ? null : req.get("registryId").getAsString();
            if (req.has("oraxenId")) item.oraxenId = req.get("oraxenId").isJsonNull() ? null : req.get("oraxenId").getAsString();
            if (req.has("customModelData")) item.customModelData = req.get("customModelData").getAsInt();
            if (req.has("displayName")) item.displayName = req.get("displayName").isJsonNull() ? null : req.get("displayName").getAsString();
            if (req.has("itemData")) item.itemData = req.get("itemData").isJsonNull() ? null : req.get("itemData").getAsString();
            if (req.has("itemLevel")) item.itemLevel = req.get("itemLevel").getAsDouble();
            if (req.has("itemLevelSlot")) item.itemLevelSlot = req.get("itemLevelSlot").getAsInt();
            if (req.has("overrideVanillaMainhand")) item.overrideVanillaMainhand = req.get("overrideVanillaMainhand").getAsBoolean();
            if (req.has("overrideVanillaArmor")) item.overrideVanillaArmor = req.get("overrideVanillaArmor").getAsBoolean();
            if (req.has("rarity")) item.rarity = req.get("rarity").getAsString();
            if (req.has("slots") && req.get("slots").isJsonArray()) {
                item.slots = new java.util.ArrayList<>();
                req.getAsJsonArray("slots").forEach(e -> item.slots.add(e.getAsString()));
            }
            applyStatsFromJson(req, item);

            equipmentStatConfig.save();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJsonString(exchange, 200, gson.toJson(response));
        }

        private void handleDeleteItem(HttpExchange exchange, String registryId) throws IOException {
            boolean removed = equipmentStatConfig.removeByRegistryId(registryId);
            if (!removed) {
                removed = equipmentStatConfig.items.removeIf(e -> e != null && registryId.equals(e.itemId));
            }
            if (!removed) {
                sendError(exchange, 404, "Item not found");
                return;
            }

            equipmentStatConfig.save();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJsonString(exchange, 200, gson.toJson(response));
        }

        private JsonObject itemToJson(EquipmentStatConfig.ItemEntry item) {
            JsonObject obj = new JsonObject();
            obj.addProperty("itemId", item.itemId);
            obj.addProperty("registryId", item.registryId);
            obj.addProperty("oraxenId", item.oraxenId);
            obj.addProperty("customModelData", item.customModelData);
            obj.addProperty("displayName", item.displayName);
            obj.addProperty("itemData", item.itemData);
            obj.addProperty("itemLevel", item.itemLevel);
            obj.addProperty("itemLevelSlot", item.itemLevelSlot);
            obj.addProperty("overrideVanillaMainhand", item.overrideVanillaMainhand);
            obj.addProperty("overrideVanillaArmor", item.overrideVanillaArmor);
            obj.addProperty("rarity", item.rarity);

            JsonArray slotsArr = new JsonArray();
            if (item.slots != null) item.slots.forEach(slotsArr::add);
            obj.add("slots", slotsArr);

            obj.addProperty("attack", item.attack);
            obj.addProperty("magicAttack", item.magicAttack);
            obj.addProperty("defense", item.defense);
            obj.addProperty("hp", item.hp);
            obj.addProperty("critRate", item.critRate);
            obj.addProperty("critDamage", item.critDamage);
            obj.addProperty("attackSpeed", item.attackSpeed);
            obj.addProperty("cooldownReduction", item.cooldownReduction);
            obj.addProperty("moveSpeed", item.moveSpeed);
            obj.addProperty("strength", item.strength);
            obj.addProperty("agility", item.agility);
            obj.addProperty("intelligence", item.intelligence);
            obj.addProperty("luck", item.luck);
            obj.addProperty("weapons", item.weapons);
            return obj;
        }

        private void applyStatsFromJson(JsonObject req, EquipmentStatConfig.ItemEntry item) {
            if (req.has("attack")) item.attack = req.get("attack").getAsDouble();
            if (req.has("magicAttack")) item.magicAttack = req.get("magicAttack").getAsDouble();
            if (req.has("defense")) item.defense = req.get("defense").getAsDouble();
            if (req.has("hp")) item.hp = req.get("hp").getAsDouble();
            if (req.has("critRate")) item.critRate = req.get("critRate").getAsDouble();
            if (req.has("critDamage")) item.critDamage = req.get("critDamage").getAsDouble();
            if (req.has("attackSpeed")) item.attackSpeed = req.get("attackSpeed").getAsDouble();
            if (req.has("cooldownReduction")) item.cooldownReduction = req.get("cooldownReduction").getAsDouble();
            if (req.has("moveSpeed")) item.moveSpeed = req.get("moveSpeed").getAsDouble();
            if (req.has("strength")) item.strength = req.get("strength").getAsInt();
            if (req.has("agility")) item.agility = req.get("agility").getAsInt();
            if (req.has("intelligence")) item.intelligence = req.get("intelligence").getAsInt();
            if (req.has("luck")) item.luck = req.get("luck").getAsInt();
            if (req.has("weapons")) item.weapons = req.get("weapons").getAsInt();
        }

        private String getStr(JsonObject o, String k, String def) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
        }
        private int getInt(JsonObject o, String k, int def) {
            return o.has(k) ? o.get(k).getAsInt() : def;
        }
        private double getDbl(JsonObject o, String k, double def) {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        }
        private boolean getBool(JsonObject o, String k, boolean def) {
            return o.has(k) ? o.get(k).getAsBoolean() : def;
        }
    }

    // ──────────────────────────────────────────────────
    //  Additional (AngongGui config) handler
    // ──────────────────────────────────────────────────

    private class AdditionalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!isAuthenticated(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                String method = exchange.getRequestMethod().toUpperCase();

                if ("GET".equals(method)) {
                    handleGetAdditional(exchange);
                } else if ("POST".equals(method)) {
                    handleUpdateAdditional(exchange);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }

            } catch (Exception e) {
                logger.error("WebPanel: Error in additional handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetAdditional(HttpExchange exchange) throws IOException {
            JsonObject response = new JsonObject();

            // System toggles from serverConfig
            response.addProperty("statSystemEnabled", serverConfig.statSystemEnabled);
            response.addProperty("damageSystemEnabled", serverConfig.damageSystemEnabled);
            response.addProperty("customHealthEnabled", serverConfig.customHealthEnabled);
            response.addProperty("customHudEnabled", serverConfig.customHudEnabled);

            // AngongGui config
            if (angongGuiConfig != null) {
                response.add("angongGui", angongGuiConfig.toJson());
            }

            sendJsonString(exchange, 200, gson.toJson(response));
        }

        private void handleUpdateAdditional(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            JsonObject updates = JsonParser.parseString(body).getAsJsonObject();

            // Update system toggles → save to server config file
            boolean systemToggleChanged = false;
            Path configFile = configDir.resolve("customdamagesystem-server.json");

            JsonObject existingConfig;
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile, StandardCharsets.UTF_8);
                existingConfig = JsonParser.parseString(content).getAsJsonObject();
            } else {
                existingConfig = JsonParser.parseString(gson.toJson(serverConfig)).getAsJsonObject();
            }

            for (String key : new String[]{"statSystemEnabled", "damageSystemEnabled", "customHealthEnabled", "customHudEnabled"}) {
                if (updates.has(key)) {
                    existingConfig.add(key, updates.get(key));
                    systemToggleChanged = true;
                }
            }

            if (systemToggleChanged) {
                Files.writeString(configFile, gson.toJson(existingConfig), StandardCharsets.UTF_8);
            }

            // Update AngongGui config
            if (updates.has("angongGui") && angongGuiConfig != null) {
                JsonObject guiUpdates = updates.getAsJsonObject("angongGui");
                angongGuiConfig.applyFromJson(guiUpdates);
                angongGuiConfig.save(logger);
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Additional config saved. System toggles require server restart.");
            sendJsonString(exchange, 200, gson.toJson(response));
        }
    }

    // ──────────────────────────────────────────────────
    //  System info handler (CPU / RAM)
    // ──────────────────────────────────────────────────

    private class SystemInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!isAuthenticated(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                Runtime runtime = Runtime.getRuntime();
                long maxMemory = runtime.maxMemory();
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;

                // CPU usage via MXBean
                java.lang.management.OperatingSystemMXBean osBean =
                        java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                double cpuLoad = -1;
                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                    cpuLoad = sunBean.getProcessCpuLoad() * 100;
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("maxMemoryMB", maxMemory / 1024 / 1024);
                resp.addProperty("usedMemoryMB", usedMemory / 1024 / 1024);
                resp.addProperty("freeMemoryMB", freeMemory / 1024 / 1024);
                resp.addProperty("totalMemoryMB", totalMemory / 1024 / 1024);
                resp.addProperty("cpuUsage", Math.round(cpuLoad * 10.0) / 10.0);
                resp.addProperty("availableProcessors", runtime.availableProcessors());

                sendJsonString(exchange, 200, gson.toJson(resp));

            } catch (Exception e) {
                logger.error("WebPanel: Error in system info handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    // ──────────────────────────────────────────────────
    //  Restart handler
    // ──────────────────────────────────────────────────

    private class RestartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!isAuthenticated(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method not allowed");
                    return;
                }

                String batPath = serverConfig.restartBatPath;
                if (batPath == null || batPath.isBlank()) {
                    sendError(exchange, 400, "restartBatPath is not configured in server config");
                    return;
                }

                java.io.File batFile = new java.io.File(batPath);
                if (!batFile.exists()) {
                    sendError(exchange, 400, "BAT file not found: " + batPath);
                    return;
                }

                // Send success response before starting restart
                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("message", "Server restart initiated");
                sendJsonString(exchange, 200, gson.toJson(resp));

                logger.info("[WebPanel] Server restart requested. Launching: {}", batPath);

                // Launch the bat file as a detached process (survives JVM shutdown)
                new ProcessBuilder("cmd.exe", "/c", "start", "", "/b", batFile.getAbsolutePath())
                        .directory(batFile.getParentFile())
                        .inheritIO()
                        .start();

                // Schedule server stop after a short delay
                minecraftServer.execute(() -> {
                    logger.info("[WebPanel] Stopping server for restart...");
                    minecraftServer.halt(false);
                });

            } catch (Exception e) {
                logger.error("WebPanel: Error in restart handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }
    }

    // ──────────────────────────────────────────────────
    //  Quests (QuestConfig) handler
    // ──────────────────────────────────────────────────

    private class QuestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (handlePreflight(exchange)) return;

                if (!isAuthenticated(exchange)) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                if (questConfig == null) {
                    sendError(exchange, 503, "Quest config not available");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod().toUpperCase();

                String remaining = path.substring("/api/quests".length());
                if (remaining.isEmpty() || remaining.equals("/")) {
                    if ("GET".equals(method)) {
                        handleQuestList(exchange);
                    } else if ("POST".equals(method)) {
                        handleAddQuest(exchange);
                    } else {
                        sendError(exchange, 405, "Method not allowed");
                    }
                } else {
                    String withoutSlash = remaining.startsWith("/") ? remaining.substring(1) : remaining;
                    String questId = java.net.URLDecoder.decode(withoutSlash, StandardCharsets.UTF_8);

                    switch (method) {
                        case "GET" -> handleGetQuest(exchange, questId);
                        case "POST" -> handleUpdateQuest(exchange, questId);
                        case "DELETE" -> handleDeleteQuest(exchange, questId);
                        default -> sendError(exchange, 405, "Method not allowed");
                    }
                }
            } catch (Exception e) {
                logger.error("WebPanel: Error in quests handler", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleQuestList(HttpExchange exchange) throws IOException {
            JsonArray arr = new JsonArray();
            for (QuestDefinition def : questConfig.getDefinitions().values()) {
                arr.add(defToJson(def));
            }
            sendJsonString(exchange, 200, gson.toJson(arr));
        }

        private void handleGetQuest(HttpExchange exchange, String questId) throws IOException {
            QuestDefinition def = questConfig.getDefinition(questId);
            if (def == null) {
                sendError(exchange, 404, "Quest not found");
                return;
            }
            sendJsonString(exchange, 200, gson.toJson(defToJson(def)));
        }

        private void handleAddQuest(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();

            QuestDefinition def = jsonToDef(req);
            if (def.id == null || def.id.isEmpty()) {
                sendError(exchange, 400, "Quest id is required");
                return;
            }

            questConfig.putDefinition(def);
            questConfig.save();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJsonString(exchange, 200, gson.toJson(response));
        }

        private void handleUpdateQuest(HttpExchange exchange, String questId) throws IOException {
            QuestDefinition existing = questConfig.getDefinition(questId);
            if (existing == null) {
                sendError(exchange, 404, "Quest not found");
                return;
            }

            String body = readRequestBody(exchange);
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();

            QuestDefinition def = jsonToDef(req);
            def.id = questId; // keep original id

            questConfig.putDefinition(def);
            questConfig.save();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJsonString(exchange, 200, gson.toJson(response));
        }

        private void handleDeleteQuest(HttpExchange exchange, String questId) throws IOException {
            boolean removed = questConfig.removeDefinition(questId);
            if (!removed) {
                sendError(exchange, 404, "Quest not found");
                return;
            }

            questConfig.save();

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            sendJsonString(exchange, 200, gson.toJson(response));
        }

        private JsonObject defToJson(QuestDefinition def) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", def.id);
            obj.addProperty("title", def.title);
            obj.addProperty("description", def.description);
            obj.addProperty("category", def.category);
            if (def.chainId != null) obj.addProperty("chainId", def.chainId);
            obj.addProperty("chainOrder", def.chainOrder);
            obj.addProperty("repeatable", def.repeatable);
            obj.addProperty("dailyReset", def.dailyReset);
            obj.addProperty("autoAccept", def.autoAccept);
            obj.addProperty("sortOrder", def.sortOrder);
            if (def.nextQuest != null) obj.addProperty("nextQuest", def.nextQuest);

            JsonObject prereq = new JsonObject();
            JsonArray pqArr = new JsonArray();
            def.prerequisiteQuests.forEach(pqArr::add);
            prereq.add("quests", pqArr);
            prereq.addProperty("minLevel", def.minLevel);
            obj.add("prerequisites", prereq);

            JsonArray objArr = new JsonArray();
            for (QuestObjective o : def.objectives) objArr.add(objToJson(o));
            obj.add("objectives", objArr);

            JsonArray rwdArr = new JsonArray();
            for (QuestReward r : def.rewards) rwdArr.add(rwdToJson(r));
            obj.add("rewards", rwdArr);

            return obj;
        }

        private JsonObject objToJson(QuestObjective o) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", o.type);
            obj.addProperty("description", o.description);
            obj.addProperty("amount", o.amount);
            if (o.entityType != null) obj.addProperty("entityType", o.entityType);
            if (o.entityName != null) obj.addProperty("entityName", o.entityName);
            if (o.item != null) obj.addProperty("item", o.item);
            if (o.block != null) obj.addProperty("block", o.block);
            if (o.npcId != null) obj.addProperty("npcId", o.npcId);
            if (o.fromNpc != null) obj.addProperty("fromNpc", o.fromNpc);
            if (o.customId != null) obj.addProperty("customId", o.customId);
            if (o.dropChance != 1.0) obj.addProperty("dropChance", o.dropChance);
            if (o.x != 0 || o.y != 0 || o.z != 0) {
                obj.addProperty("x", o.x);
                obj.addProperty("y", o.y);
                obj.addProperty("z", o.z);
                obj.addProperty("radius", o.radius);
            }
            if (o.level > 0) obj.addProperty("level", o.level);
            return obj;
        }

        private JsonObject rwdToJson(QuestReward r) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", r.type);
            if (r.amount != 0) obj.addProperty("amount", r.amount);
            if (r.item != null) obj.addProperty("item", r.item);
            if (r.command != null) obj.addProperty("command", r.command);
            if (r.stat != null) obj.addProperty("stat", r.stat);
            if (r.value != 0) obj.addProperty("value", r.value);
            return obj;
        }

        private QuestDefinition jsonToDef(JsonObject req) {
            QuestDefinition def = new QuestDefinition();
            def.id = getStr(req, "id", "");
            def.title = getStr(req, "title", "");
            def.description = getStr(req, "description", "");
            def.category = getStr(req, "category", QuestDefinition.CAT_SUB);
            def.chainId = getStr(req, "chainId", null);
            def.chainOrder = getInt(req, "chainOrder", 0);
            def.repeatable = getBool(req, "repeatable", false);
            def.dailyReset = getBool(req, "dailyReset", false);
            def.autoAccept = getBool(req, "autoAccept", false);
            def.sortOrder = getInt(req, "sortOrder", 0);
            def.nextQuest = getStr(req, "nextQuest", null);

            if (req.has("prerequisites") && req.get("prerequisites").isJsonObject()) {
                JsonObject prereq = req.getAsJsonObject("prerequisites");
                if (prereq.has("quests") && prereq.get("quests").isJsonArray()) {
                    for (JsonElement e : prereq.getAsJsonArray("quests")) {
                        def.prerequisiteQuests.add(e.getAsString());
                    }
                }
                def.minLevel = getInt(prereq, "minLevel", 0);
            }

            if (req.has("objectives") && req.get("objectives").isJsonArray()) {
                for (JsonElement e : req.getAsJsonArray("objectives")) {
                    def.objectives.add(jsonToObj(e.getAsJsonObject()));
                }
            }

            if (req.has("rewards") && req.get("rewards").isJsonArray()) {
                for (JsonElement e : req.getAsJsonArray("rewards")) {
                    def.rewards.add(jsonToRwd(e.getAsJsonObject()));
                }
            }

            return def;
        }

        private QuestObjective jsonToObj(JsonObject req) {
            QuestObjective o = new QuestObjective();
            o.type = getStr(req, "type", QuestObjective.CUSTOM);
            o.description = getStr(req, "description", "");
            o.amount = getInt(req, "amount", 1);
            o.entityType = getStr(req, "entityType", null);
            o.entityName = getStr(req, "entityName", null);
            o.item = getStr(req, "item", null);
            o.block = getStr(req, "block", null);
            o.npcId = getStr(req, "npcId", null);
            o.fromNpc = getStr(req, "fromNpc", null);
            o.customId = getStr(req, "customId", null);
            o.dropChance = getDbl(req, "dropChance", 1.0);
            o.x = getDbl(req, "x", 0);
            o.y = getDbl(req, "y", 0);
            o.z = getDbl(req, "z", 0);
            o.radius = getDbl(req, "radius", 5);
            o.level = getInt(req, "level", 0);
            return o;
        }

        private QuestReward jsonToRwd(JsonObject req) {
            QuestReward r = new QuestReward();
            r.type = getStr(req, "type", QuestReward.GOLD);
            r.amount = getInt(req, "amount", 0);
            r.item = getStr(req, "item", null);
            r.command = getStr(req, "command", null);
            r.stat = getStr(req, "stat", null);
            r.value = getInt(req, "value", 0);
            return r;
        }

        private String getStr(JsonObject o, String k, String def) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
        }
        private int getInt(JsonObject o, String k, int def) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
        }
        private double getDbl(JsonObject o, String k, double def) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
        }
        private boolean getBool(JsonObject o, String k, boolean def) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
        }
    }
}
