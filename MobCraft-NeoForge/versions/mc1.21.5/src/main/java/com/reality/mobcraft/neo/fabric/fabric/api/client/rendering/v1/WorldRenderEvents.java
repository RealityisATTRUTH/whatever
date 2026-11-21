package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;

public final class WorldRenderEvents {
    public static final Event<BeforeEntities> BEFORE_ENTITIES = new Event<>(l -> c -> { for (BeforeEntities x : l) x.beforeEntities(c); });
    public interface BeforeEntities { void beforeEntities(Context context); }
    public record Context(MultiBufferSource consumers, ClientLevel world, PoseStack matrixStack, DeltaTracker tickCounter) {}
}
