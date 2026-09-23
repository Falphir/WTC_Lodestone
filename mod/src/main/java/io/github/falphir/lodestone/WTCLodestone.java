package io.github.falphir.lodestone;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(WTCLodestone.MODID)
public class WTCLodestone {
    public static final String MODID = "wtc_lodestone";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WTCLodestone(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
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
        HubClient.hello();
    }
}