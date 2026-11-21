package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public final class EntityRendererRegistry {
    public static final Map<EntityType<?>, EntityRendererProvider<?>> RENDERERS = new LinkedHashMap<>();
    public static <E extends Entity> void register(EntityType<? extends E> type, EntityRendererProvider<E> provider) { RENDERERS.put(type, provider); }
}
