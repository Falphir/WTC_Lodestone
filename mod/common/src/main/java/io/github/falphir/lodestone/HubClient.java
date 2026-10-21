package io.github.falphir.lodestone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
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

    /**
     * Ticks between whitelist polls, as set by the hub; same threading as heartbeatIntervalTicks.
     * Defaults to the heartbeat's minute, which is what a hub too old to send it expects.
     */
    private static volatile int whitelistSyncIntervalTicks = DEFAULT_HEARTBEAT_INTERVAL_TICKS;

    /**
     * The network kick message from the hub; blank (also: an older hub, or not heard from yet) leaves
     * the mod config's own. Written by the HTTP thread, read by the login check.
     */
    private static volatile String hubWhitelistMessage = "";

    /** The network ban message template from the hub; blank until heard from. Same threading as hubWhitelistMessage. */
    private static volatile String hubBanMessage = "";

    /** Only touched on the server thread. */
    private static boolean warnedEmptyWhitelist = false;

    /** Whitelist version last reported to the hub as applied. Only touched on the server thread. */
    private static String reportedWhitelistVersion = null;

    private HubClient() {}

    public static int heartbeatIntervalTicks() {
        return heartbeatIntervalTicks;
    }

    public static int whitelistSyncIntervalTicks() {
        return whitelistSyncIntervalTicks;
    }

    /**
     * Kick message for players who are not whitelisted: the hub's if it set one, else the mod config's,
     * with {@code &} color codes decoded either way. Blank means vanilla's.
     */
    public static String whitelistMessage() {
        String hub = hubWhitelistMessage;
        return LodestoneConfig.colorCodes(hub.isBlank() ? LodestoneConfig.whitelistMessage() : hub);
    }

    /**
     * What a player refused or kicked for a network ban sees, with their reason and expiry filled in and
     * {@code &} color codes decoded. Only for bans this mod applied: a server's own keep vanilla's wording.
     */
    public static String banMessage(String reason, Date expires) {
        String hub = hubBanMessage;
        return LodestoneConfig.colorCodes(BanMessages.render(hub.isBlank() ? BanMessages.DEFAULT : hub, reason, expires));
    }

    /** The kick and ban messages ride on both the hello and the heartbeat response. */
    private static void readMessages(JsonObject body) {
        JsonElement message = body.get("whitelistMessage");
        if (message != null && !message.isJsonNull()) {
            hubWhitelistMessage = message.getAsString();
        }
        JsonElement ban = body.get("banMessage");
        if (ban != null && !ban.isJsonNull()) {
            hubBanMessage = ban.getAsString();
        }
    }

    /** A link code this server handed out. A minute short of the hub's 5, so one shown here is never already dead there. */
    private record IssuedCode(String code, long expiresAt) {}

    private static final long LINK_CODE_TTL_MS = Duration.ofMinutes(4).toMillis();

    /** Keyed by player UUID, so reconnecting shows the same code. Read and written from the login check only. */
    private static final Map<UUID, IssuedCode> linkCodes = new ConcurrentHashMap<>();

    /**
     * The code to show a player refused for not being whitelisted, so they can claim the account with the
     * bot's /link. The hub hears about it asynchronously -- a login check must never wait on the network --
     * and the player only types it into Discord well after that. Null when this server doesn't take its
     * whitelist from the hub, since a link would never let them in here.
     */
    public static String linkCode(GameProfile profile) {
        if (!LodestoneConfig.isReady() || !LodestoneConfig.syncWhitelist() || LodestoneConfig.dryRun()) return null;

        long now = System.currentTimeMillis();
        linkCodes.values().removeIf(issued -> issued.expiresAt() < now);
        IssuedCode issued = linkCodes.get(profile.getId());
        if (issued != null) return issued.code();

        String code = LinkCodes.generate();
        linkCodes.put(profile.getId(), new IssuedCode(code, now + LINK_CODE_TTL_MS));

        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        body.addProperty("uuid", profile.getId().toString());
        body.addProperty("name", profile.getName());
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/bridge/link-code"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + LodestoneConfig.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() >= 300) {
                        Lodestone.LOGGER.warn("WTC Lodestone hub refused a link code (HTTP {})", response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    Lodestone.LOGGER.warn("WTC Lodestone could not send a link code: {}", error.getMessage());
                    return null;
                });
        return code;
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
        body.addProperty("syncBans", LodestoneConfig.syncBans());

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
            // a poll is one small GET, but a misconfigured 0 shouldn't make it one per tick
            if (body.has("whitelistSyncIntervalSeconds")) {
                int seconds = body.get("whitelistSyncIntervalSeconds").getAsInt();
                whitelistSyncIntervalTicks = Math.max(20 * 5, seconds * 20);
            }
            readMessages(body);

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

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    // older hubs answer with an empty body; keep whatever hello gave us then
                    if (response.statusCode() == 200 && !response.body().isBlank()) {
                        readMessages(JsonParser.parseString(response.body()).getAsJsonObject());
                    }
                })
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

    /**
     * Tags every ban this mod writes, so it can tell them from the server's own: a ban from /ban here, or
     * one that was in banned-players.json before, has a different source and is never touched.
     */
    public static final String BAN_SOURCE = "WTC Lodestone";

    /** Ban list version last reported to the hub as applied. Only touched on the server thread. */
    private static String reportedBanVersion = null;

    /** One ban from the hub, in the shape vanilla's ban list stores. {@code expires} null is permanent. */
    private record HubBan(GameProfile profile, String reason, Date created, Date expires) {}

    /** Fetches the network ban list from the hub and applies it to this server. */
    public static void syncBans(MinecraftServer server) {
        String url = baseUrl() + "/api/bridge/bans";

        if (LodestoneConfig.dryRun()) {
            Lodestone.LOGGER.info("[dry run] Would sync bans from hub at {}", url);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + LodestoneConfig.token())
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        Lodestone.LOGGER.warn("WTC Lodestone ban sync failed (HTTP {})", response.statusCode());
                        return;
                    }
                    List<HubBan> bans = parseBans(response.body());
                    String version = response.headers().firstValue("X-Ban-Version").orElse(null);
                    server.execute(() -> {
                        applyBans(server, bans);
                        reportBansApplied(version);
                    });
                })
                .exceptionally(error -> {
                    Lodestone.LOGGER.warn("WTC Lodestone ban sync failed: {}", error.getMessage());
                    return null;
                });
    }

    private static List<HubBan> parseBans(String body) {
        List<HubBan> bans = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(body).getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject();
            bans.add(new HubBan(
                    new GameProfile(UUID.fromString(entry.get("uuid").getAsString()), entry.get("name").getAsString()),
                    optionalString(entry, "reason"),
                    optionalDate(entry, "bannedAt"),
                    optionalDate(entry, "expiresAt")));
        }
        return bans;
    }

    private static String optionalString(JsonObject entry, String key) {
        JsonElement value = entry.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /** banned-players.json keeps dates to the second, so these are cut to the second too, or every comparison would differ. */
    private static Date optionalDate(JsonObject entry, String key) {
        String value = optionalString(entry, key);
        return value == null ? null : Date.from(Instant.parse(value).truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * Runs on the server thread. Lifts our own bans the hub no longer lists, writes the ones it does
     * (new, or with a changed reason or expiry), and kicks anyone online who just got banned. A ban the
     * server made itself is left alone, even for a player the hub also bans: they're banned either way.
     */
    private static void applyBans(MinecraftServer server, List<HubBan> bans) {
        UserBanList list = server.getPlayerList().getBans();
        Set<UUID> wanted = bans.stream().map(ban -> ban.profile().getId()).collect(Collectors.toSet());
        int lifted = 0;
        int applied = 0;

        for (UserBanListEntry entry : List.copyOf(list.getEntries())) {
            // getUser() is protected in vanilla; the loader's access transformer widens it (see applyWhitelist)
            GameProfile user = entry.getUser();
            if (user != null && BAN_SOURCE.equals(entry.getSource()) && !wanted.contains(user.getId())) {
                list.remove(user);
                lifted++;
            }
        }
        for (HubBan ban : bans) {
            UserBanListEntry existing = list.get(ban.profile());
            if (existing != null && !BAN_SOURCE.equals(existing.getSource())) continue;
            if (existing != null && Objects.equals(existing.getReason(), ban.reason())
                    && Objects.equals(existing.getExpires(), ban.expires())) {
                continue;
            }
            list.add(new UserBanListEntry(ban.profile(), ban.created(), BAN_SOURCE, ban.expires(), ban.reason()));
            applied++;
            // kicked the way vanilla's /ban does, but with the network's message
            ServerPlayer online = server.getPlayerList().getPlayer(ban.profile().getId());
            if (online != null) online.connection.disconnect(Component.literal(banMessage(ban.reason(), ban.expires())));
        }

        if (applied + lifted > 0) {
            Lodestone.LOGGER.info("Bans synced from hub: {} applied, {} lifted", applied, lifted);
        }
    }

    /** Tells the hub which ban list version this server now has. Only sent when it changed. Server thread only. */
    private static void reportBansApplied(String version) {
        if (version == null || version.equals(reportedBanVersion)) return;

        JsonObject body = new JsonObject();
        body.addProperty("version", version);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/bridge/bans/applied"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + LodestoneConfig.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        reportedBanVersion = version;
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() >= 300) {
                        Lodestone.LOGGER.warn("WTC Lodestone could not report the applied ban list (HTTP {})", response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    Lodestone.LOGGER.warn("WTC Lodestone could not report the applied ban list: {}", error.getMessage());
                    return null;
                });
    }

    private static String baseUrl() {
        String url = LodestoneConfig.hubUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}