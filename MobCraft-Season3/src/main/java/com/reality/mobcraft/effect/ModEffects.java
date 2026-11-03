package com.reality.mobcraft.effect;

import com.reality.mobcraft.MobCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

/**
 * The Curse effect and its potion.
 *
 *   Brewing  Water Bottle or Awkward Potion + Dragon Egg = Splash Potion of Curse (3:00),
 *            straight away. There's no drinkable one. Dragon's breath turns
 *            it lingering. Brewing is data in 26.3: data/mobcraft/recipe/brewing.
 */
public final class ModEffects {

    public static final Holder<MobEffect> CURSE =
            Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MobCraft.id("curse"), new CurseEffect());

    public static final Holder<Potion> CURSE_POTION =
            Registry.registerForHolder(BuiltInRegistries.POTION, MobCraft.id("curse"),
                    new Potion("curse", new MobEffectInstance(CURSE, 3600, 0)));

    private ModEffects() {}

    /** Loads the class so the effect and potion register. Brewing recipes are JSON files. */
    public static void init() {}
}
