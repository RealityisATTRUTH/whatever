package com.reality.mobcraft.effect;

import com.reality.mobcraft.item.MobRelics;
import com.reality.mobcraft.item.ModItems;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Brewing for 26.2, which has no brewing recipe files. Splash and lingering
 * versions come from the vanilla gunpowder and dragon's breath steps.
 * Curse is splash only: see PotionBrewingCurseMixin.
 */
public final class Brewing {
    private Brewing() {}

    public static void register() {
        FabricBrewingRecipeRegistryBuilder.BUILD.register(b -> {
            b.addMix(Potions.AWKWARD, ModItems.BLAZE_EMBER, MobRelics.BLAZING);
            b.addMix(Potions.AWKWARD, ModItems.GHAST_WISP, MobRelics.DRIFTING);
            b.addMix(Potions.AWKWARD, Items.DRAGON_EGG, ModEffects.CURSE_POTION);
            b.addMix(Potions.WATER, Items.DRAGON_EGG, ModEffects.CURSE_POTION);
        });
    }
}
