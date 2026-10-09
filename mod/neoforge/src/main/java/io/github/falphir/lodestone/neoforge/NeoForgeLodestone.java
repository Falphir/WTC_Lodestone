package io.github.falphir.lodestone.neoforge;

import io.github.falphir.lodestone.Lodestone;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * All the NeoForge there is: hand {@link Lodestone} the things only a loader knows, then forward
 * the two server events it cares about. Adding Forge or Fabric means another file this size.
 */
@Mod(Lodestone.MODID)
public class NeoForgeLodestone {

    public NeoForgeLodestone(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        VersionInfo versions = FMLLoader.versionInfo();
        Lodestone.init(FMLPaths.CONFIGDIR.get(), new Lodestone.Platform(
                modContainer.getModInfo().getVersion().toString(), versions.mcVersion(), versions.neoForgeVersion()));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Lodestone.serverStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        Lodestone.serverTick(event.getServer());
    }
}
