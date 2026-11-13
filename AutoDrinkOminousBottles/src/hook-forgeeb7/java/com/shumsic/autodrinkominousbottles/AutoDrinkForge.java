package com.shumsic.autodrinkominousbottles;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge 1.21.6 and newer, with its new event buses. The mod only does anything on the client. */
@Mod("auto_drink_ominous_bottles")
public class AutoDrinkForge {
    public AutoDrinkForge(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.init(context);
    }
}
