package com.reality.mobcraft.neo;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * NeoForge 26.3's loot table event gives only a HolderGetter.Provider, while MobCraft's loot code reads enchantments
 * through a HolderLookup.Provider. This wraps one as the other; looking things up by name is all the loot code needs.
 */
final class GetterLookup implements HolderLookup.Provider {
    private final HolderGetter.Provider getters;
    GetterLookup(HolderGetter.Provider getters) { this.getters = getters; }

    @Override public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() { return Stream.empty(); }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
        Optional<? extends HolderGetter<T>> getter = getters.lookup(key);
        return getter.map(g -> new HolderLookup.RegistryLookup<T>() {
            @Override public ResourceKey<? extends Registry<? extends T>> key() { return key; }
            @Override public Lifecycle registryLifecycle() { return Lifecycle.stable(); }
            @Override public Stream<Holder.Reference<T>> listElements() { return Stream.empty(); }
            @Override public Stream<HolderSet.Named<T>> listTags() { return Stream.empty(); }
            @Override public Optional<Holder.Reference<T>> get(ResourceKey<T> id) { return g.get(id); }
            @Override public Optional<HolderSet.Named<T>> get(TagKey<T> tag) { return g.get(tag); }
        });
    }
}
