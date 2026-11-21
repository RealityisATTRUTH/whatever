package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.world;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import com.reality.mobcraft.neo.fabric.fabric.api.event.EventFactory;

/**
 * The two world render events MobCraft uses, fired by WorldRenderEventsMixin. Fabric API has these from 1.21.10 on;
 * for 1.21.9 MobCraft fires them itself, at the same places in the game's drawing.
 */
public final class WorldRenderEvents {
    private WorldRenderEvents() {}

    /** Just before the game submits entities to draw. */
    public static final Event<BeforeEntities> BEFORE_ENTITIES = EventFactory.createArrayBacked(BeforeEntities.class,
            listeners -> context -> { for (BeforeEntities l : listeners) l.beforeEntities(context); });

    /** Once everything to draw this frame has been gathered, right after the world border. */
    public static final Event<EndExtraction> END_EXTRACTION = EventFactory.createArrayBacked(EndExtraction.class,
            listeners -> context -> { for (EndExtraction l : listeners) l.endExtraction(context); });

    @FunctionalInterface public interface BeforeEntities { void beforeEntities(WorldRenderContext context); }
    @FunctionalInterface public interface EndExtraction { void endExtraction(WorldExtractionContext context); }
}
