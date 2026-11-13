package com.shumsic.autodrinkominousbottles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

/** Fabric 1.21 to 1.21.11. */
public class AutoDrinkFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AutoDrink mod = new AutoDrink(new GameImpl(), new Settings(FabricLoader.getInstance().getConfigDir().toFile()));
        KeyMapping key = KeyBindingHelper.registerKeyBinding(Keys.create());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) mod.toggle();
            mod.tick();
        });
    }
}
