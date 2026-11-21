package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public interface LivingEntityFeatureRendererRegistrationCallback {
    Event<LivingEntityFeatureRendererRegistrationCallback> EVENT = new Event<>(l -> (t, r, h, c) -> { for (LivingEntityFeatureRendererRegistrationCallback x : l) x.registerRenderers(t, r, h, c); });
    void registerRenderers(EntityType<? extends LivingEntity> entityType, LivingEntityRenderer<?, ?, ?> entityRenderer, RegistrationHelper registrationHelper, EntityRendererProvider.Context context);
    interface RegistrationHelper {
        <S extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState, M extends net.minecraft.client.model.EntityModel<? super S>> void register(RenderLayer<S, M> layer);
    }
}
