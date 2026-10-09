package io.github.falphir.lodestone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;

/** Talks to the WTC Lodestone hub. Never blocks the server thread; failures only log a warning. */
public final class HubClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Fallback until the hub tells us its interval on check-in: 20 ticks/s * 60s. */
    private static final int DEFAULT_HEARTBEAT_INTERVAL_TICKS = 20 * 60;

    /** Ticks between heartbeats, as set by the hub. Written by the HTTP thread, read by the server thread. */
    private static volatile int heartbeatIntervalTicks = DEFAULT_HEARTBEAT_INTERVAL_TICKS;

    /** Only touched on the server thread. */
    private static boolean warnedEmptyWhitelist = false;

    /** Whitelist version last reported to the hub as applied. Only touched on the server thread. */
    private static String reportedWhitelistVersion = null;

    private HubClient() {}

    public static int heartbeatIntervalTicks() {
        return heartbeatIntervalTicks;
    }

    /** Checks in with the hub: reports this server's setup, and logs whether the connection and token work. */
    public static void hello(String modVersion, String minecraftVersion, String loaderVersion, boolean enforceWhitelist) {
        String url = baseUrl() + "/api/bridge/hello";

        if (LodestoneConfig.dryRun()) {
            Lodestone.LOGGER.info("[dry run] Would check in with hub at {}", url);
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("modVersion", modVersion);
        body.addProperty("minecraftVersion", minecraftVersion);
        body.addProperty("loaderVersion", loaderVersion);
        body.addProperty("syncWhitelist", LodestoneConfig.syncWhitelist());
        // server.properties, not ours: with this off, syncing the whitelist never kicks anyone,
        // so the hub shows it rather than letting the mismatch pass silently.
        body.addProperty("enforceWhitelist", enforceWhitelist);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + LodestoneConfig.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(HubClient::handleHelloResponse)
                .exceptionally(error -> {
                    Lodestone.LOGGER.warn("WTC Lodestone hub unreachable at {}: {}", url, error.getMessage());
                    return null;
                });
    }

    private static void handleHelloResponse(HttpResponse<String> response) {
        int status = response.statusCode();

        if (status == 200) {
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            String hubServerId = body.get("serverId").getAsString();
            Lodestone.LOGGER.info("Connected to WTC Lodestone hub as '{}' ({})",
                    hubServerId, body.get("name").getAsString());

            // older hubs don't send it; keep the default then
            if (body.has("heartbeatIntervalSeconds")) {
                int seconds = body.get("heartbeatIntervalSeconds").getAsInt();
                heartbeatIntervalTicks = Math.max(20 * 10, seconds * 20);
            }

            if (!hubServerId.equals(LodestoneConfig.serverId())) {
                Lodestone.LOGGER.warn("Config says serverId '{}' but this token belongs to '{}'. Check the config.",
                        LodestoneConfig.serverId(), hubServerId);
            }
        } else if (status == 401 || status == 403) {
            Lodestone.LOGGER.warn("WTC Lodestone hub rejected this server's token (HTTP {}). Check 'token' in the config.", status);
        } else {
            Lodestone.LOGGER.warn("WTC Lodestone hub check-in failed (HTTP {})", status);
        }
    }

    /** Sends the server's current player count, who is on, tick time and memory usage to the hub. Server thread only. */
    public static void heartbeat(MinecraftServer server) {
        String url = baseUrl() + "/api/bridge/heartbeat";

        if (LodestoneConfig.dryRun()) {
            Lodestone.LOGGER.info("[dry run] Would send heartbeat to hub at {}", url);
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long memoryMaxMb = runtime.maxMemory() / (1024 * 1024);
        // mspt is the honest number: tps saturates at 20 the moment a tick fits in its 50ms budget,
        // so it can't show a server going from comfortable to nearly-late.
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        double tps = Math.min(20.0, 1000.0 / Math.max(50.0, mspt));

        JsonArray players = new JsonArray();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", player.getUUID().toString());
            entry.addProperty("name", player.getGameProfile().getName());
            players.add(entry);
        }

        JsonObject body = new JsonObject();
        body.addProperty("playerCount", server.getPlayerCount());
        body.addProperty("maxPlayers", server.getMaxPlayers());
        body.addProperty("tps", tps);
        body.addProperty("msptAvg", mspt);
        body.addProperty("memoryUsedMb", memoryUsedMb);
        body.addProperty("memoryMaxMb", memoryMaxMb);
        body.add("players", players);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + LodestoneConfig.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(error -> {
                    Lodestone.LOGGER.warn("WTC Lodestone heartbeat failed: {}", error.getMessage());
                    return null;
                });
    }

    /** Fetches the network whitelist from the hub and mirrors it into this server's whitelist. */
    public static void syncWhitelist(MinecraftServer server) {
        String url = baseUrl() + "/api/bridge/whitelist";

        if (LodestoneConfig.dryRun()) {
            Lodestone.LOGGER.info("[dry run] Would sync whitelist from hub at {}", url);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + LodestoneConfig.token())
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    // 204: nobody has ever been added to the hub whitelist -- leave this server's alone
                    // rather than wipe it (see WhitelistService.everPopulated() on the hub).
                    if (response.statusCode() == 204) {
                        if (!warnedEmptyWhitelist) {
                            Lodestone.LOGGER.warn("Hub whitelist is empty - leaving this server's whitelist untouched. Import it on the dashboard.");
                            warnedEmptyWhitelist = true;
                        }
                        return;
                    }
                    if (response.statusCode() != 200) {
                        Lodestone.LOGGER.warn("WTC Lodestone whitelist sync failed (HTTP {})", response.statusCode());
                        return;
                    }
                    List<GameProfile> players = parseWhitelist(response.body());
                    String version = response.headers().firstValue("X-Whitelist-Version").orElse(null);
                    server.execute(() -> {
                        applyWhitelist(server, players);
                        reportWhitelistApplied(version);
                    });
                })
                .exceptionally(error -> {
                    Lodestone.LOGGER.warn("WTC Lodestone whitelist sync failed: {}", error.getMessage());
                    return null;
                });
    }

    private static List<GameProfile> parseWhitelist(String body) {
        List<GameProfile> players = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(body).getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            players.add(new GameProfile(UUID.fromString(entry.get("uuid").getAsString()), entry.get("name").getAsString()));
        }
        return players;
    }

    /**
     * Runs on the server thread. Makes the local whitelist exactly match the hub's (which may be
     * genuinely empty -- the hub only sends 200 here once it's confirmed populated at least once,
     * see syncWhitelist), then kicks anyone no longer on it.
     */
    private static void applyWhitelist(MinecraftServer server, List<GameProfile> players) {
        UserWhiteList whitelist = server.getPlayerList().getWhiteList();
        Set<UUID> wanted = players.stream().map(GameProfile::getId).collect(Collectors.toSet());
        int removed = 0;
        int added = 0;

        for (UserWhiteListEntry entry : List.copyOf(whitelist.getEntries())) {
            // getUser() is protected in vanilla; each loader module widens it with an access
            // transformer. That file is the one thing here a loader has to provide -- Forge takes
            // the same one, but a Fabric module would need an access widener in its place.
            GameProfile user = entry.getUser();
            if (user != null && !wanted.contains(user.getId())) {
                whitelist.remove(user);
                removed++;
            }
        }
        for (GameProfile player : players) {
            if (!whitelist.isWhiteListed(player)) {
                whitelist.add(new UserWhiteListEntry(player));
                added++;
            }
        }

        if (added + removed > 0) {
            Lodestone.LOGGER.info("Whitelist synced from hub: {} added, {} removed", added, removed);
            // Same as vanilla /whitelist remove: only kicks when enforce-whitelist is on
            server.kickUnlistedPlayers(server.createCommandSourceStack());
        }
    }

    /** Tells the hub which whitelist version this server now has. Only sent when it changed. Server thread only. */
    private static void reportWhitelistApplied(String version) {
        if (version == null || version.equals(reportedWhitelistVersion)) return;

        JsonObject body = new JsonObject();
        body.addProperty("version", version);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/bridge/whitelist/applied"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + LodestoneConfig.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        reportedWhitelistVersion = version;
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() >= 300) {
                        Lodestone.LOGGER.warn("WTC Lodestone could not report the applied whitelist (HTTP {})", response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    Lodestone.LOGGER.warn("WTC Lodestone could not report the applied whitelist: {}", error.getMessage());
                    return null;
                });
    }

    private static String baseUrl() {
        String url = LodestoneConfig.hubUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}