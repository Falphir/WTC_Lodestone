package io.github.falphir.lodestone;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(WTCLodestone.MODID)
public class WTCLodestone {
    public static final String MODID = "wtc_lodestone";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final String modVersion;

    private int ticksUntilHeartbeat = HubClient.heartbeatIntervalTicks();

    public WTCLodestone(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modVersion = modContainer.getModInfo().getVersion().toString();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (!Config.ENABLED.get()) {
            LOGGER.info("WTC Lodestone is disabled in config");
            return;
        }
        if (!Config.isReady()) {
            LOGGER.warn("WTC Lodestone is not configured (serverId or token missing) - hub connection disabled");
            return;
        }
        LOGGER.info("WTC Lodestone ready: server '{}' -> {}{}",
                Config.SERVER_ID.get(), Config.HUB_URL.get(), Config.DRY_RUN.get() ? " (dry run)" : "");
        VersionInfo versions = FMLLoader.versionInfo();
        HubClient.hello(modVersion, versions.mcVersion(), versions.neoForgeVersion());
        if (Config.SYNC_WHITELIST.get()) HubClient.syncWhitelist(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!Config.isReady()) return;
        if (--ticksUntilHeartbeat > 0) return;
        ticksUntilHeartbeat = HubClient.heartbeatIntervalTicks();
        HubClient.heartbeat(event.getServer());
        if (Config.SYNC_WHITELIST.get()) HubClient.syncWhitelist(event.getServer());
    }
}