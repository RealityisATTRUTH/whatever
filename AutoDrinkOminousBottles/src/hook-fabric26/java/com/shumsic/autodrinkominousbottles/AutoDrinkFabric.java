package com.shumsic.autodrinkominousbottles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

/** Fabric 26.x. */
public class AutoDrinkFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AutoDrink mod = new AutoDrink(new GameImpl(), new Settings(FabricLoader.getInstance().getConfigDir().toFile()));
        KeyMapping key = KeyMappingHelper.registerKeyMapping(Keys.create());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) mod.toggle();
            mod.tick();
        });
    }
}
