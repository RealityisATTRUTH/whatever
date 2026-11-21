package com.reality.mobcraft.neo.fabric.fabric.api.biome.v1;

import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** On NeoForge, features are added by biome modifier JSON files instead, so this does nothing. */
public final class BiomeModifications {
    public static void addFeature(Predicate<Object> selector, GenerationStep.Decoration step, ResourceKey<PlacedFeature> feature) {}
}
