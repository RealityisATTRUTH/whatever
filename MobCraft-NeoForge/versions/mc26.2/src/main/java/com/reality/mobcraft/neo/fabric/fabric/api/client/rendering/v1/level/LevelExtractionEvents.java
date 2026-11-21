package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.level;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;

/** 26.2 moved END_EXTRACTION into its own class; it's the same event NeoClientHooks already fires. */
public final class LevelExtractionEvents {
    public static final Event<LevelRenderEvents.EndExtraction> END_EXTRACTION = LevelRenderEvents.END_EXTRACTION;
    public interface EndExtraction extends LevelRenderEvents.EndExtraction {}
}
