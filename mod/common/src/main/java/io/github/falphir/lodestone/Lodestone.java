package io.github.falphir.lodestone;

import java.util.function.ToDoubleFunction;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;

/**
 * Everything the mod does, with nothing in it that belongs to a mod loader -- only vanilla
 * Minecraft and plain Java. Each loader module is then just an entrypoint that calls
 * {@link #init} once and forwards two server events here, which is what lets the same code
 * run on NeoForge, Forge or Fabric.
 */
public final class Lodestone {

    public static final String MODID = "wtc_lodestone";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static Platform platform = new Platform("unknown", "unknown", "unknown", "unknown", server -> 0);
    private static int ticksUntilHeartbeat = HubClient.heartbeatIntervalTicks();
    private static int ticksUntilWhitelistSync = HubClient.whitelistSyncIntervalTicks();
    private static int ticksUntilBanSync = HubClient.whitelistSyncIntervalTicks();

    private Lodestone() {}

    /**
     * What only the loader knows: this installation's versions, reported to the hub on check-in, and
     * the server's average tick time in milliseconds, which vanilla exposes differently from one
     * Minecraft version to the next (1.21.1's getAverageTickTimeNanos() doesn't exist on 1.20.1).
     *
     * @param loader "neoforge", "forge" and so on, as the hub names the jars it installs
     */
    public record Platform(String modVersion, String minecraftVersion, String loader, String loaderVersion,
            ToDoubleFunction<MinecraftServer> averageTickMillis) {}

    /** The server's average tick time in milliseconds, however this loader's Minecraft gets it. */
    static double averageTickMillis(MinecraftServer server) {
        return platform.averageTickMillis().applyAsDouble(server);
    }

    /** Called once while the loader starts up, before any server exists. */
    public static void init(java.nio.file.Path configDir, Platform platform) {
        Lodestone.platform = platform;
        LodestoneConfig.load(configDir);
    }

    /** The server is starting: check in with the hub and do a first whitelist sync. */
    public static void serverStarting(MinecraftServer server) {
        if (!LodestoneConfig.enabled()) {
            LOGGER.info("WTC Lodestone is disabled in config");
            return;
        }
        if (!LodestoneConfig.isReady()) {
            LOGGER.warn("WTC Lodestone is not configured (serverId or token missing) - hub connection disabled");
            return;
        }
        LOGGER.info("WTC Lodestone ready: server '{}' -> {}{}",
                LodestoneConfig.serverId(), LodestoneConfig.hubUrl(), LodestoneConfig.dryRun() ? " (dry run)" : "");
        if (LodestoneConfig.syncWhitelist() && !server.isEnforceWhitelist()) {
            LOGGER.warn("syncWhitelist is on but enforce-whitelist is false in server.properties - "
                    + "players removed from the hub whitelist will stay connected until they disconnect");
        }
        HubClient.hello(platform, server.isEnforceWhitelist());
        if (LodestoneConfig.syncWhitelist()) HubClient.syncWhitelist(server);
        if (LodestoneConfig.syncBans()) HubClient.syncBans(server);
    }

    /**
     * Called every server tick. Heartbeats and whitelist polls each run on their own interval from
     * the hub: a heartbeat is stored and charted, so it stays slow; a poll stores nothing, so it
     * can be quick enough that an approval or removal lands in seconds.
     */
    public static void serverTick(MinecraftServer server) {
        if (!LodestoneConfig.isReady()) return;
        if (--ticksUntilHeartbeat <= 0) {
            ticksUntilHeartbeat = HubClient.heartbeatIntervalTicks();
            HubClient.heartbeat(server);
        }
        if (LodestoneConfig.syncWhitelist() && --ticksUntilWhitelistSync <= 0) {
            ticksUntilWhitelistSync = HubClient.whitelistSyncIntervalTicks();
            HubClient.syncWhitelist(server);
        }
        // the ban list polls as often as the whitelist: a ban should land just as fast
        if (LodestoneConfig.syncBans() && --ticksUntilBanSync <= 0) {
            ticksUntilBanSync = HubClient.whitelistSyncIntervalTicks();
            HubClient.syncBans(server);
        }
        HubClient.applyMotd(server);
    }
}
