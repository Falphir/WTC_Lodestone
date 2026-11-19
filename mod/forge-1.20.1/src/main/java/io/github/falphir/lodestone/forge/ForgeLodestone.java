package io.github.falphir.lodestone.forge;

import io.github.falphir.lodestone.Lodestone;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.VersionInfo;

/**
 * All the Forge there is, as NeoForgeLodestone is all the NeoForge: hand {@link Lodestone} the things only
 * a loader knows, then forward the two server events it cares about.
 */
@Mod(Lodestone.MODID)
public class ForgeLodestone {

    public ForgeLodestone(FMLJavaModLoadingContext context) {
        MinecraftForge.EVENT_BUS.register(this);
        VersionInfo versions = FMLLoader.versionInfo();
        String modVersion = ModList.get().getModContainerById(Lodestone.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        // 1.20.1 has no getAverageTickTimeNanos(): its average is already in milliseconds
        Lodestone.init(FMLPaths.CONFIGDIR.get(), new Lodestone.Platform(
                modVersion, versions.mcVersion(), "forge", versions.forgeVersion(), server -> server.getAverageTickTime()));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Lodestone.serverStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // the event fires before and after each tick; once a tick is what the shared code counts in
        if (event.phase == TickEvent.Phase.END) Lodestone.serverTick(event.getServer());
    }
}
