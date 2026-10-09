package io.github.falphir.lodestone;

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

    private static Platform platform = new Platform("unknown", "unknown", "unknown");
    private static int ticksUntilHeartbeat = HubClient.heartbeatIntervalTicks();

    private Lodestone() {}

    /** What the loader knows about this installation, reported to the hub on check-in. */
    public record Platform(String modVersion, String minecraftVersion, String loaderVersion) {}

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
        HubClient.hello(platform.modVersion(), platform.minecraftVersion(), platform.loaderVersion());
        if (LodestoneConfig.syncWhitelist()) HubClient.syncWhitelist(server);
    }

    /** Called every server tick; sends a heartbeat and syncs the whitelist on the hub's interval. */
    public static void serverTick(MinecraftServer server) {
        if (!LodestoneConfig.isReady()) return;
        if (--ticksUntilHeartbeat > 0) return;
        ticksUntilHeartbeat = HubClient.heartbeatIntervalTicks();
        HubClient.heartbeat(server);
        if (LodestoneConfig.syncWhitelist()) HubClient.syncWhitelist(server);
    }
}
