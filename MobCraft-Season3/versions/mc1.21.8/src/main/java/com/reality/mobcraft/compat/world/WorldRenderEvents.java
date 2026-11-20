package com.reality.mobcraft.compat.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.compat.SubmitNodeCollector;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * The world render event MobCraft uses. On 1.21.8 Fabric API still has its older world render events, so this one is
 * fired from Fabric's own BEFORE_ENTITIES, with a collector that draws straight into Fabric's buffers.
 */
public final class WorldRenderEvents {
    private WorldRenderEvents() {}

    /** Just before the game draws entities. */
    public static final Event<BeforeEntities> BEFORE_ENTITIES = EventFactory.createArrayBacked(BeforeEntities.class,
            listeners -> context -> { for (BeforeEntities l : listeners) l.beforeEntities(context); });

    @FunctionalInterface public interface BeforeEntities { void beforeEntities(WorldRenderContext context); }

    static {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (context.consumers() == null || context.world() == null) return;
            PoseStack pose = context.matrixStack() != null ? context.matrixStack() : new PoseStack();
            BEFORE_ENTITIES.invoker().beforeEntities(new WorldRenderContext(new SubmitNodeCollector(context.consumers()),
                    pose, context.tickCounter(), context.world()));
        });
    }
}
