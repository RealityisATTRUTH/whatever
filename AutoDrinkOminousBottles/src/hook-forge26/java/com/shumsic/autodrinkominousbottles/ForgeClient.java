package com.shumsic.autodrinkominousbottles;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client side, kept apart so a server never loads client classes. */
final class ForgeClient {
    private static final KeyMapping KEY = Keys.create();
    private static AutoDrink mod;

    private ForgeClient() {}

    static void init() {
        mod = new AutoDrink(new GameImpl(), new Settings(FMLPaths.CONFIGDIR.get().toFile()));
        RegisterKeyMappingsEvent.BUS.addListener(ForgeClient::onKeys);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ForgeClient::onTick);
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    private static void onTick(TickEvent.ClientTickEvent.Post event) {
        while (KEY.consumeClick()) mod.toggle();
        mod.tick();
    }
}
