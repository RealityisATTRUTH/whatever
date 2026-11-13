package com.shumsic.autodrinkominousbottles;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** NeoForge 1.21 to 1.21.8. The mod only does anything on the client. */
@Mod("auto_drink_ominous_bottles")
public class AutoDrinkNeoForge {
    public AutoDrinkNeoForge(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) NeoForgeClient.init(modBus);
    }
}
