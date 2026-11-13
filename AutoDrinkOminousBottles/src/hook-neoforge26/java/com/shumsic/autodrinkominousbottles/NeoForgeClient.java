package com.shumsic.autodrinkominousbottles;

import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side, kept apart so a server never loads client classes. */
final class NeoForgeClient {
    private static final KeyMapping KEY = Keys.create();
    private static AutoDrink mod;

    private NeoForgeClient() {}

    static void init(IEventBus modBus) {
        mod = new AutoDrink(new GameImpl(), new Settings(FMLPaths.CONFIGDIR.get().toFile()));
        modBus.addListener(NeoForgeClient::onKeys);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onTick);
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    private static void onTick(ClientTickEvent.Post event) {
        while (KEY.consumeClick()) mod.toggle();
        mod.tick();
    }
}
