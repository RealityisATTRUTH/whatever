package com.reality.mobcraft.neo.fabric.fabric.api.biome.v1;

import java.util.function.Predicate;

/** Placeholder; on NeoForge the biome is chosen by the biome modifier JSON in data/mobcraft/neoforge/biome_modifier. */
public final class BiomeSelectors {
    public static Predicate<Object> foundInOverworld() { return o -> true; }
}
