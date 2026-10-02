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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;

/** Talks to the WTC Lodestone hub. Never blocks the server thread; failures only log a warning. */
public final class HubClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Only touched on the server thread. */
    private static boolean warnedEmptyWhitelist = false;

    private HubClient() {}

    /** Checks in with the hub and logs whether the connection and token work. */
    public static void hello() {
        String url = baseUrl() + "/api/bridge/hello";

        if (Config.DRY_RUN.get()) {
            WTCLodestone.LOGGER.info("[dry run] Would check in with hub at {}", url);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + Config.TOKEN.get())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(HubClient::handleHelloResponse)
                .exceptionally(error -> {
                    WTCLodestone.LOGGER.warn("WTC Lodestone hub unreachable at {}: {}", url, error.getMessage());
                    return null;
                });
    }

    private static void handleHelloResponse(HttpResponse<String> response) {
        int status = response.statusCode();

        if (status == 200) {
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            String hubServerId = body.get("serverId").getAsString();
            WTCLodestone.LOGGER.info("Connected to WTC Lodestone hub as '{}' ({})",
                    hubServerId, body.get("name").getAsString());

            if (!hubServerId.equals(Config.SERVER_ID.get())) {
                WTCLodestone.LOGGER.warn("Config says serverId '{}' but this token belongs to '{}'. Check the config.",
                        Config.SERVER_ID.get(), hubServerId);
            }
        } else if (status == 401 || status == 403) {
            WTCLodestone.LOGGER.warn("WTC Lodestone hub rejected this server's token (HTTP {}). Check 'token' in the config.", status);
        } else {
            WTCLodestone.LOGGER.warn("WTC Lodestone hub check-in failed (HTTP {})", status);
        }
    }

    /** Sends the server's current player count, TPS and memory usage to the hub. */
    public static void heartbeat(MinecraftServer server) {
        String url = baseUrl() + "/api/bridge/heartbeat";

        if (Config.DRY_RUN.get()) {
            WTCLodestone.LOGGER.info("[dry run] Would send heartbeat to hub at {}", url);
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long memoryMaxMb = runtime.maxMemory() / (1024 * 1024);
        double tps = Math.min(20.0, 1000.0 / Math.max(50.0, server.getAverageTickTimeNanos() / 1_000_000.0));

        JsonObject body = new JsonObject();
        body.addProperty("playerCount", server.getPlayerCount());
        body.addProperty("maxPlayers", server.getMaxPlayers());
        body.addProperty("tps", tps);
        body.addProperty("memoryUsedMb", memoryUsedMb);
        body.addProperty("memoryMaxMb", memoryMaxMb);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + Config.TOKEN.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(error -> {
                    WTCLodestone.LOGGER.warn("WTC Lodestone heartbeat failed: {}", error.getMessage());
                    return null;
                });
    }

    /** Fetches the network whitelist from the hub and mirrors it into this server's whitelist. */
    public static void syncWhitelist(MinecraftServer server) {
        String url = baseUrl() + "/api/bridge/whitelist";

        if (Config.DRY_RUN.get()) {
            WTCLodestone.LOGGER.info("[dry run] Would sync whitelist from hub at {}", url);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + Config.TOKEN.get())
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        WTCLodestone.LOGGER.warn("WTC Lodestone whitelist sync failed (HTTP {})", response.statusCode());
                        return;
                    }
                    List<GameProfile> players = parseWhitelist(response.body());
                    server.execute(() -> applyWhitelist(server, players));
                })
                .exceptionally(error -> {
                    WTCLodestone.LOGGER.warn("WTC Lodestone whitelist sync failed: {}", error.getMessage());
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

    /** Runs on the server thread. Makes the local whitelist exactly match the hub's, then kicks anyone no longer on it. */
    private static void applyWhitelist(MinecraftServer server, List<GameProfile> players) {
        // ponytail: an empty hub list means "not set up yet", so a fresh hub can't wipe existing whitelists.
        // Downside: removing the very last player on the hub never reaches the servers.
        if (players.isEmpty()) {
            if (!warnedEmptyWhitelist) {
                WTCLodestone.LOGGER.warn("Hub whitelist is empty - leaving this server's whitelist untouched. Import it on the dashboard.");
                warnedEmptyWhitelist = true;
            }
            return;
        }

        UserWhiteList whitelist = server.getPlayerList().getWhiteList();
        Set<UUID> wanted = players.stream().map(GameProfile::getId).collect(Collectors.toSet());
        int removed = 0;
        int added = 0;

        for (UserWhiteListEntry entry : List.copyOf(whitelist.getEntries())) {
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
            WTCLodestone.LOGGER.info("Whitelist synced from hub: {} added, {} removed", added, removed);
            // Same as vanilla /whitelist remove: only kicks when enforce-whitelist is on
            server.kickUnlistedPlayers(server.createCommandSourceStack());
        }
    }

    private static String baseUrl() {
        String url = Config.HUB_URL.get();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}