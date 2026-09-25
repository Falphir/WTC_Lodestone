package io.github.falphir.lodestone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.MinecraftServer;

/** Talks to the WTC Lodestone hub. Never blocks the server thread; failures only log a warning. */
public final class HubClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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

    private static String baseUrl() {
        String url = Config.HUB_URL.get();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}