package com.reality.mobcraft.neo.fabric.loader.api;

import java.nio.file.Path;
import java.util.Optional;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

/** Just enough of Fabric Loader's API for MobCraft, answered by NeoForge. */
public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();
    public static FabricLoader getInstance() { return INSTANCE; }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    public Path getGameDir() { return FMLPaths.GAMEDIR.get(); }
    public boolean isModLoaded(String id) { return ModList.get() != null ? ModList.get().isLoaded(id) : FMLLoader.getLoadingModList().getModFileById(id) != null; }
    public boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }
    public java.util.Collection<ModContainer> getAllMods() {
        return ModList.get().getSortedMods().stream().map(ModContainer::new).toList();
    }
    public Optional<ModContainer> getModContainer(String id) {
        return ModList.get().getModContainerById(id).map(ModContainer::new);
    }
}
